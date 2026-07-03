package com.yr.perftest.jmeter.functions;

import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.AbstractFunction;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomString extends AbstractFunction {
    private static final String KEY = "__randomString";
    private static final List<String> DESC = List.of("Length of random string");
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private int length = 8;

    @Override
    public String execute(SampleResult previousResult, Sampler currentSampler) {
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(ALPHABET.charAt(ThreadLocalRandom.current().nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }

    @Override
    public void setParameters(Collection<CompoundVariable> parameters) throws InvalidVariableException {
        if (parameters.isEmpty()) {
            length = 8;
            return;
        }
        String raw = parameters.iterator().next().execute().trim();
        try {
            length = Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw new InvalidVariableException(KEY + " length must be an integer: " + raw);
        }
        if (length < 1 || length > 1024) {
            throw new InvalidVariableException(KEY + " length must be between 1 and 1024");
        }
    }

    @Override
    public String getReferenceKey() {
        return "randomString";
    }

    @Override
    public List<String> getArgumentDesc() {
        return DESC;
    }
}
