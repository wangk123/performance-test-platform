package com.yr.perftest.jmeter.functions;

import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.AbstractFunction;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomMobile extends AbstractFunction {
    private static final String KEY = "__randomMobile";
    private static final List<String> DESC = List.of("Generate random Chinese mobile number");
    private static final String[] PREFIXES = {"130", "131", "132", "133", "135", "136", "137", "138", "139",
            "150", "151", "152", "157", "158", "159", "186", "187", "188", "189"};

    @Override
    public String execute(SampleResult previousResult, Sampler currentSampler) {
        String prefix = PREFIXES[ThreadLocalRandom.current().nextInt(PREFIXES.length)];
        int suffix = ThreadLocalRandom.current().nextInt(100_000_000);
        return prefix + String.format("%08d", suffix);
    }

    @Override
    public void setParameters(Collection<CompoundVariable> parameters) throws InvalidVariableException {
        if (!parameters.isEmpty()) {
            throw new InvalidVariableException(KEY + " does not accept parameters");
        }
    }

    @Override
    public String getReferenceKey() {
        return "randomMobile";
    }

    @Override
    public List<String> getArgumentDesc() {
        return DESC;
    }
}
