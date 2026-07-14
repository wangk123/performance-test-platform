package com.yr.perftest.jmeter.functions;

import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.AbstractFunction;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class Base64Decode extends AbstractFunction {
    private static final String KEY = "__base64Decode";
    private static final List<String> DESC = List.of("Base64 text to decode");
    private String text = "";

    @Override
    public String execute(SampleResult previousResult, Sampler currentSampler) {
        try {
            byte[] decoded = Base64.getDecoder().decode(text);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(KEY + " invalid base64: " + text, exception);
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
        return "base64Decode";
    }

    @Override
    public List<String> getArgumentDesc() {
        return DESC;
    }
}
