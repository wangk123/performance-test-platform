package com.yr.perftest.jmeter.functions;

import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.AbstractFunction;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;

public class Md5 extends AbstractFunction {
    private static final String KEY = "__md5";
    private static final List<String> DESC = List.of("Plain text to hash");
    private String text = "";

    @Override
    public String execute(SampleResult previousResult, Sampler currentSampler) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
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
        return "md5";
    }

    @Override
    public List<String> getArgumentDesc() {
        return DESC;
    }
}
