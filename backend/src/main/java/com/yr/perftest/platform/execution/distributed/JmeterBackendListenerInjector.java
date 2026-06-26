package com.yr.perftest.platform.execution.distributed;

import com.yr.perftest.platform.script.JmeterScriptNormalizer;
import com.yr.perftest.platform.script.ScriptValidationException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.nio.file.Path;

@Component
public class JmeterBackendListenerInjector {
    private static final String FAILURE_SAMPLE_COLLECTOR_NAME = "Failure Sample Collector";
    private static final String AGGREGATE_COLLECTOR_NAME = "Aggregate Snapshot Collector";

    private final JmeterScriptNormalizer normalizer;

    public JmeterBackendListenerInjector(JmeterScriptNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public void inject(
            Path source,
            Path target,
            Path aggregateSnapshotPath
    ) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(source.toFile());
            normalizer.normalize(document);
            Element testPlan = (Element) document.getElementsByTagName("TestPlan").item(0);
            Element hashTree = nextHashTree(testPlan);
            if (hashTree == null) {
                throw new ScriptValidationException("script content is not a JMeter test plan");
            }
            removeInjectedElements(hashTree);
            hashTree.appendChild(aggregateSnapshotCollector(document));
            hashTree.appendChild(document.createElement("hashTree"));
            hashTree.appendChild(failureSampleListener(document));
            hashTree.appendChild(document.createElement("hashTree"));
            purgeFailureSampleCollectors(document);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(document), new StreamResult(target.toFile()));
        } catch (ScriptValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ScriptValidationException("failed to inject JMeter runtime listeners");
        }
    }

    private void purgeFailureSampleCollectors(Document document) {
        NodeList threadGroups = document.getElementsByTagName("ThreadGroup");
        for (int index = 0; index < threadGroups.getLength(); index++) {
            Element threadGroup = (Element) threadGroups.item(index);
            Element threadHashTree = nextHashTree(threadGroup);
            if (threadHashTree == null) {
                continue;
            }
            removeFailureSampleCollectors(threadHashTree);
        }
    }

    private void removeFailureSampleCollectors(Element hashTree) {
        for (Node node = hashTree.getFirstChild(); node != null; ) {
            Node next = node.getNextSibling();
            if (node instanceof Element element
                    && FAILURE_SAMPLE_COLLECTOR_NAME.equals(element.getAttribute("testname"))
                    && ("JSR223Assertion".equals(element.getTagName())
                    || "JSR223Listener".equals(element.getTagName()))) {
                hashTree.removeChild(element);
                if (next instanceof Element nextElement && "hashTree".equals(nextElement.getTagName())) {
                    hashTree.removeChild(nextElement);
                    next = nextElement.getNextSibling();
                }
            }
            node = next;
        }
    }

    private void removeInjectedElements(Element hashTree) {
        for (Node node = hashTree.getFirstChild(); node != null; ) {
            Node next = node.getNextSibling();
            if (node instanceof Element element && shouldRemove(element)) {
                hashTree.removeChild(element);
                if (next instanceof Element nextElement && "hashTree".equals(nextElement.getTagName())) {
                    hashTree.removeChild(nextElement);
                    next = nextElement.getNextSibling();
                }
            }
            node = next;
        }
    }

    private boolean shouldRemove(Element element) {
        String tagName = element.getTagName();
        String testName = element.getAttribute("testname");
        return ("BackendListener".equals(tagName) && "InfluxDB Backend Listener".equals(testName))
                || ("ResultCollector".equals(tagName) && "Failure Collector".equals(testName))
                || ("JSR223Listener".equals(tagName) && AGGREGATE_COLLECTOR_NAME.equals(testName))
                || ("JSR223Listener".equals(tagName) && FAILURE_SAMPLE_COLLECTOR_NAME.equals(testName));
    }

    private Element failureSampleListener(Document document) {
        Element listener = document.createElement("JSR223Listener");
        listener.setAttribute("guiclass", "TestBeanGUI");
        listener.setAttribute("testclass", "JSR223Listener");
        listener.setAttribute("testname", FAILURE_SAMPLE_COLLECTOR_NAME);
        listener.setAttribute("enabled", "true");
        listener.appendChild(stringProp(document, "scriptLanguage", "groovy"));
        listener.appendChild(stringProp(document, "script", failureSampleCollectorScript()));
        listener.appendChild(stringProp(document, "parameters", ""));
        listener.appendChild(stringProp(document, "filename", ""));
        listener.appendChild(stringProp(document, "cacheKey", "true"));
        return listener;
    }

    private String failureSampleCollectorScript() {
        return """
                import groovy.json.JsonOutput
                import groovy.transform.Field
                import java.util.concurrent.ConcurrentHashMap
                import java.util.concurrent.atomic.AtomicInteger
                import java.util.concurrent.atomic.AtomicLong

                @Field static final ConcurrentHashMap LABEL_COUNTERS = new ConcurrentHashMap()
                @Field static final AtomicLong GLOBAL_COUNTER = new AtomicLong()
                @Field static final AtomicLong ID_SEQ = new AtomicLong()
                @Field static final ConcurrentHashMap WRITERS = new ConcurrentHashMap()

                try {
                def result = prev
                if (result == null) return

                def assertionFailed = false
                def failureMessage = ''
                result.getAssertionResults()?.each { a ->
                    if (a.isFailure() || a.isError()) {
                        assertionFailed = true
                        if (failureMessage == '' && a.getFailureMessage()) {
                            failureMessage = a.getFailureMessage()
                        }
                    }
                }
                if (result.isSuccessful() && !assertionFailed) return

                def perLabelLimit = (props.get('failureSamplePerLabelLimit') ?: '50') as Integer
                def globalLimit = (props.get('failureSampleGlobalLimit') ?: '1000') as Integer

                def label = result.getSampleLabel()
                def counter = LABEL_COUNTERS.computeIfAbsent(label, { new AtomicInteger(0) })
                if (counter.incrementAndGet() > perLabelLimit) return
                if (GLOBAL_COUNTER.incrementAndGet() > globalLimit) return

                def path = props.get('failureSamplesPath') ?: '/test/failure-samples.jsonl'
                def hostName = props.get('jmeterRoleHost') ?: ''
                def id = ID_SEQ.incrementAndGet()

                def url = ''
                try { url = result.getURL()?.toString() ?: '' } catch (Throwable t) { url = '' }
                def bytes = result.getResponseData()
                def responseBody = bytes != null ? new String(bytes, 'UTF-8') : ''

                def row = [
                    id: id,
                    host: hostName,
                    ts: result.getTimeStamp(),
                    label: label,
                    code: result.getResponseCode() ?: '',
                    success: false,
                    elapsed: result.getTime(),
                    message: result.getResponseMessage() ?: '',
                    threadName: result.getThreadName() ?: '',
                    url: url,
                    requestHeaders: result.getRequestHeaders() ?: '',
                    requestBody: result.getSamplerData() ?: '',
                    responseHeaders: result.getResponseHeaders() ?: '',
                    responseBody: responseBody,
                    failureMessage: failureMessage
                ]

                def writer = WRITERS.computeIfAbsent(path, {
                    def file = new File(path as String)
                    file.parentFile?.mkdirs()
                    new BufferedWriter(new FileWriter(file, true))
                })
                synchronized (writer) {
                    writer.write(JsonOutput.toJson(row))
                    writer.newLine()
                    writer.flush()
                }
                } catch (Throwable t) {
                    try {
                        def errPath = (props.get('failureSamplesPath') ?: '/test/failure-samples.jsonl') + '.err'
                        new File(errPath as String).append(t.toString() + System.lineSeparator())
                    } catch (Throwable ignored) {}
                }
                """;
    }

    private Element aggregateSnapshotCollector(Document document) {
        Element listener = document.createElement("JSR223Listener");
        listener.setAttribute("guiclass", "TestBeanGUI");
        listener.setAttribute("testclass", "JSR223Listener");
        listener.setAttribute("testname", AGGREGATE_COLLECTOR_NAME);
        listener.setAttribute("enabled", "true");
        listener.appendChild(stringProp(document, "scriptLanguage", "groovy"));
        listener.appendChild(stringProp(document, "script", aggregateSnapshotScript()));
        listener.appendChild(stringProp(document, "parameters", ""));
        listener.appendChild(stringProp(document, "filename", ""));
        listener.appendChild(stringProp(document, "cacheKey", "true"));
        return listener;
    }

    private String aggregateSnapshotScript() {
        return """
                import org.HdrHistogram.Histogram
                import java.util.concurrent.ConcurrentHashMap
                import java.util.concurrent.atomic.AtomicLong
                import java.nio.ByteBuffer
                import java.util.zip.Deflater

                def state = props.get('aggregateState')
                if (state == null) {
                  synchronized (props) {
                    state = props.get('aggregateState')
                    if (state == null) {
                      state = [
                        labels: new ConcurrentHashMap(),
                        counts: new ConcurrentHashMap(),
                        startMs: new AtomicLong(0),
                        lastFlush: new AtomicLong(0),
                        lock: new Object(),
                      ]
                      props.put('aggregateState', state)
                    }
                  }
                }

                def label = prev.getSampleLabel()
                def elapsed = Math.max(1L, prev.getTime() as long)
                def now = System.currentTimeMillis()
                state.startMs.compareAndSet(0, now)

                def hist = state.labels.computeIfAbsent(label, { new Histogram(1L, 3_600_000L, 3) })
                synchronized (hist) { hist.recordValue(elapsed) }

                def stat = state.counts.computeIfAbsent(label, {
                  [new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong(Long.MAX_VALUE), new AtomicLong(0)]
                })
                stat[0].incrementAndGet()
                if (!prev.isSuccessful()) stat[1].incrementAndGet()
                stat[2].addAndGet(elapsed)
                stat[3].updateAndGet { Math.min(it, elapsed) }
                stat[4].updateAndGet { Math.max(it, elapsed) }

                def last = state.lastFlush.get()
                if (now - last >= 1000 && state.lastFlush.compareAndSet(last, now)) {
                  synchronized (state.lock) {
                    def path = props.get('aggregateSnapshotPath') ?: args ?: '/test/aggregate-snapshot.bin'
                    def tmp = new File(path.toString() + '.tmp')
                    tmp.withDataOutputStream { out ->
                      out.writeInt(1)
                      out.writeLong(state.startMs.get())
                      out.writeLong(now)
                      out.writeInt(state.labels.size())
                      state.labels.each { name, h ->
                        def buf = ByteBuffer.allocate(h.getNeededByteBufferCapacity())
                        def len
                        synchronized (h) { len = h.encodeIntoCompressedByteBuffer(buf, Deflater.BEST_SPEED) }
                        out.writeUTF(name)
                        def s = state.counts[name]
                        out.writeLong(s[0].get())
                        out.writeLong(s[1].get())
                        out.writeLong(s[2].get())
                        out.writeLong(s[3].get())
                        out.writeLong(s[4].get())
                        out.writeInt(len)
                        out.write(buf.array(), 0, len)
                      }
                    }
                    tmp.renameTo(new File(path.toString()))
                  }
                }
                """;
    }

    private Element nextHashTree(Element element) {
        for (Node node = element.getNextSibling(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element sibling) {
                return "hashTree".equals(sibling.getTagName()) ? sibling : null;
            }
        }
        return null;
    }

    private Element stringProp(Document document, String name, String value) {
        Element element = document.createElement("stringProp");
        element.setAttribute("name", name);
        element.setTextContent(value);
        return element;
    }
}
