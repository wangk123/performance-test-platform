package com.yr.perftest.jmeter.functions;

import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.AbstractFunction;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomBankCard extends AbstractFunction {
    private static final String KEY = "__randomBankCard";
    private static final List<String> DESC = List.of("Generate random bank card number with Luhn check");
    private static final String BIN = "622202";

    @Override
    public String execute(SampleResult previousResult, Sampler currentSampler) {
        StringBuilder body = new StringBuilder(BIN);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (body.length() < 15) {
            body.append(random.nextInt(10));
        }
        return body.toString() + luhnCheckDigit(body.toString());
    }

    static int luhnCheckDigit(String partial) {
        int sum = 0;
        boolean alternate = true;
        for (int i = partial.length() - 1; i >= 0; i--) {
            int digit = partial.charAt(i) - '0';
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            alternate = !alternate;
        }
        return (10 - (sum % 10)) % 10;
    }

    @Override
    public void setParameters(Collection<CompoundVariable> parameters) throws InvalidVariableException {
        if (!parameters.isEmpty()) {
            throw new InvalidVariableException(KEY + " does not accept parameters");
        }
    }

    @Override
    public String getReferenceKey() {
        return "randomBankCard";
    }

    @Override
    public List<String> getArgumentDesc() {
        return DESC;
    }
}
