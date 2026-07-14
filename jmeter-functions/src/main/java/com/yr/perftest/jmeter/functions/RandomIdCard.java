package com.yr.perftest.jmeter.functions;

import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.AbstractFunction;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class RandomIdCard extends AbstractFunction {
    private static final String KEY = "__randomIdCard";
    private static final List<String> DESC = List.of("Generate random Chinese ID card number");
    private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
    private static final List<String> AREA_CODES = loadAreaCodes();
    private static final Set<String> AREA_CODE_SET = Set.copyOf(AREA_CODES);

    @Override
    public String execute(SampleResult previousResult, Sampler currentSampler) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String area = AREA_CODES.get(random.nextInt(AREA_CODES.size()));
        LocalDate birth = randomBirthDate(random);
        int seq = random.nextInt(0, 1000);
        String body = area + String.format("%04d%02d%02d%03d",
                birth.getYear(), birth.getMonthValue(), birth.getDayOfMonth(), seq);
        return body + checkDigit(body);
    }

    static List<String> areaCodes() {
        return AREA_CODES;
    }

    static boolean isValidFormat(String id) {
        if (id == null || !id.matches("^\\d{17}[\\dX]$")) {
            return false;
        }
        if (!AREA_CODE_SET.contains(id.substring(0, 6))) {
            return false;
        }
        try {
            LocalDate.parse(id.substring(6, 14), java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeException exception) {
            return false;
        }
        return checkDigit(id.substring(0, 17)) == id.charAt(17);
    }

    static char checkDigit(String body) {
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (body.charAt(i) - '0') * WEIGHTS[i];
        }
        return CHECK_CODES[sum % 11];
    }

    private static LocalDate randomBirthDate(ThreadLocalRandom random) {
        long min = LocalDate.of(1900, 1, 1).toEpochDay();
        long max = LocalDate.now().toEpochDay();
        return LocalDate.ofEpochDay(random.nextLong(min, max + 1));
    }

    private static List<String> loadAreaCodes() {
        InputStream stream = RandomIdCard.class.getClassLoader().getResourceAsStream("id-card-area-codes.txt");
        if (stream == null) {
            throw new IllegalStateException("missing resource id-card-area-codes.txt");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            List<String> codes = reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toList());
            if (codes.isEmpty()) {
                throw new IllegalStateException("empty id-card-area-codes.txt");
            }
            return Collections.unmodifiableList(codes);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to load id-card-area-codes.txt", exception);
        }
    }

    @Override
    public void setParameters(Collection<CompoundVariable> parameters) throws InvalidVariableException {
        if (!parameters.isEmpty()) {
            throw new InvalidVariableException(KEY + " does not accept parameters");
        }
    }

    @Override
    public String getReferenceKey() {
        return "randomIdCard";
    }

    @Override
    public List<String> getArgumentDesc() {
        return DESC;
    }
}
