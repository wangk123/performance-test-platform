package com.yr.perftest.jmeter.functions;

import org.apache.jmeter.engine.util.CompoundVariable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JmeterCustomFunctionsTest {

    @Test
    void randomMobileGeneratesElevenDigitNumber() throws Exception {
        RandomMobile function = new RandomMobile();
        function.setParameters(List.of());
        String value = function.execute(null, null);
        assertTrue(value.matches("^1\\d{10}$"));
    }

    @Test
    void randomStringUsesRequestedLength() throws Exception {
        RandomString function = new RandomString();
        function.setParameters(List.of(new CompoundVariable("12")));
        String value = function.execute(null, null);
        assertEquals(12, value.length());
    }

    @Test
    void randomStringRejectsInvalidLength() {
        RandomString function = new RandomString();
        assertThrows(Exception.class, () -> function.setParameters(List.of(new CompoundVariable("0"))));
    }
}
