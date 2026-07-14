package com.yr.perftest.jmeter.functions;

import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.AbstractFunction;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomName extends AbstractFunction {
    private static final String KEY = "__randomName";
    private static final List<String> DESC = List.of("Generate random Chinese name");
    private static final String[] SURNAMES = {"王", "李", "张", "刘", "陈", "杨", "赵", "黄", "周", "吴"};
    private static final String[] GIVEN = {"伟", "芳", "娜", "敏", "静", "丽", "强", "磊", "军", "洋", "勇", "艳", "杰", "涛", "明"};

    @Override
    public String execute(SampleResult previousResult, Sampler currentSampler) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String surname = SURNAMES[random.nextInt(SURNAMES.length)];
        String given = GIVEN[random.nextInt(GIVEN.length)];
        if (random.nextBoolean()) {
            given += GIVEN[random.nextInt(GIVEN.length)];
        }
        return surname + given;
    }

    @Override
    public void setParameters(Collection<CompoundVariable> parameters) throws InvalidVariableException {
        if (!parameters.isEmpty()) {
            throw new InvalidVariableException(KEY + " does not accept parameters");
        }
    }

    @Override
    public String getReferenceKey() {
        return "randomName";
    }

    @Override
    public List<String> getArgumentDesc() {
        return DESC;
    }
}
