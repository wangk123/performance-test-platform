package com.yr.perftest.jmeter.functions;

import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.AbstractFunction;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class UrlEncode extends AbstractFunction {
    private static final String KEY = "__urlEncode";
    private static final List<String> DESC = List.of("Text to URL-encode");
    private String text = "";

    @Override
    public String execute(SampleResult previousResult, Sampler currentSampler) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    @Override
    public void setParameters(Collection<CompoundVariable> parameters) throws InvalidVariableException {
        if (parameters.isEmpty()) {
            throw new InvalidVariableException(KEY + " requires text parameter");
        }
        Iterator<CompoundVariable> iterator = parameters.iterator();
        text = iterator.next().execute();
        if (iterator.hasNext()) {
            throw new InvalidVariableException(KEY + " accepts exactly one parameter");
        }
    }

    @Override
    public String getReferenceKey() {
        return "urlEncode";
    }

    @Override
    public List<String> getArgumentDesc() {
        return DESC;
    }
}
