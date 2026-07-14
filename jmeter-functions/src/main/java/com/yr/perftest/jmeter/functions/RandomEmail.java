package com.yr.perftest.jmeter.functions;

import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.AbstractFunction;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomEmail extends AbstractFunction {
    private static final String KEY = "__randomEmail";
    private static final List<String> DESC = List.of("Generate random email address");
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final String[] DOMAINS = {"example.com", "test.local", "mail.test"};

    @Override
    public String execute(SampleResult previousResult, Sampler currentSampler) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int length = random.nextInt(6, 12);
        StringBuilder local = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            local.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return local + "@" + DOMAINS[random.nextInt(DOMAINS.length)];
    }

    @Override
    public void setParameters(Collection<CompoundVariable> parameters) throws InvalidVariableException {
        if (!parameters.isEmpty()) {
            throw new InvalidVariableException(KEY + " does not accept parameters");
        }
    }

    @Override
    public String getReferenceKey() {
        return "randomEmail";
    }

    @Override
    public List<String> getArgumentDesc() {
        return DESC;
    }
}
