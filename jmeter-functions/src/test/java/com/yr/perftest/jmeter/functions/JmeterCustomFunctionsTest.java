package com.yr.perftest.jmeter.functions;

import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.Function;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void randomIdCardHasValidCheckDigit() throws Exception {
        RandomIdCard function = new RandomIdCard();
        function.setParameters(List.of());
        String value = function.execute(null, null);
        assertEquals(18, value.length());
        assertTrue(value.matches("^\\d{17}[\\dX]$"));
        assertEquals(checkDigit(value.substring(0, 17)), value.charAt(17));
    }

    @Test
    void randomIdCardPassesFullFormatValidation() throws Exception {
        RandomIdCard function = new RandomIdCard();
        function.setParameters(List.of());
        for (int i = 0; i < 200; i++) {
            String value = function.execute(null, null);
            assertTrue(RandomIdCard.isValidFormat(value), "invalid id: " + value);
        }
    }

    @Test
    void randomIdCardBirthYearsAreNotLimitedTo1970Through2005() throws Exception {
        RandomIdCard function = new RandomIdCard();
        function.setParameters(List.of());
        boolean before1970 = false;
        boolean after2005 = false;
        int currentYear = java.time.LocalDate.now().getYear();
        for (int i = 0; i < 3000; i++) {
            String value = function.execute(null, null);
            int year = Integer.parseInt(value.substring(6, 10));
            assertTrue(year >= 1900 && year <= currentYear, "year out of range: " + year);
            if (year < 1970) {
                before1970 = true;
            }
            if (year > 2005) {
                after2005 = true;
            }
            if (before1970 && after2005) {
                break;
            }
        }
        assertTrue(before1970, "expected some birth years before 1970");
        assertTrue(after2005, "expected some birth years after 2005");
    }

    @Test
    void areaCodeCatalogCoversAllMainlandProvinces() {
        Set<String> provinces = RandomIdCard.areaCodes().stream()
                .map(code -> code.substring(0, 2))
                .collect(Collectors.toSet());
        Set<String> expected = Set.of(
                "11", "12", "13", "14", "15",
                "21", "22", "23",
                "31", "32", "33", "34", "35", "36", "37",
                "41", "42", "43", "44", "45", "46",
                "50", "51", "52", "53", "54",
                "61", "62", "63", "64", "65"
        );
        assertEquals(expected, provinces);
        assertTrue(RandomIdCard.areaCodes().size() > 1000);
    }

    @Test
    void randomBankCardPassesLuhn() throws Exception {
        RandomBankCard function = new RandomBankCard();
        function.setParameters(List.of());
        String value = function.execute(null, null);
        assertTrue(value.matches("^\\d{16}$"));
        assertTrue(luhnValid(value));
    }

    @Test
    void randomNameIsNonEmpty() throws Exception {
        RandomName function = new RandomName();
        function.setParameters(List.of());
        String value = function.execute(null, null);
        assertTrue(value != null && !value.isBlank());
    }

    @Test
    void randomEmailContainsAt() throws Exception {
        RandomEmail function = new RandomEmail();
        function.setParameters(List.of());
        String value = function.execute(null, null);
        assertTrue(value != null && !value.isBlank());
        assertEquals(1, value.chars().filter(ch -> ch == '@').count());
    }

    @Test
    void md5ReturnsLowercaseHex() throws Exception {
        Md5 function = new Md5();
        function.setParameters(List.of(new CompoundVariable("hello")));
        assertEquals("5d41402abc4b2a76b9719d911017c592", function.execute(null, null));
    }

    @Test
    void sha256ReturnsLowercaseHex() throws Exception {
        Sha256 function = new Sha256();
        function.setParameters(List.of(new CompoundVariable("hello")));
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", function.execute(null, null));
    }

    @Test
    void base64RoundTripPreservesText() throws Exception {
        String original = "你好perf";
        Base64Encode encode = new Base64Encode();
        encode.setParameters(List.of(new CompoundVariable(original)));
        String encoded = encode.execute(null, null);

        Base64Decode decode = new Base64Decode();
        decode.setParameters(List.of(new CompoundVariable(encoded)));
        assertEquals(original, decode.execute(null, null));
    }

    @Test
    void urlEncodePercentEncodesNonAscii() throws Exception {
        UrlEncode function = new UrlEncode();
        function.setParameters(List.of(new CompoundVariable("a 测")));
        String value = function.execute(null, null);
        assertTrue(value.contains("%"));
        assertTrue(value.startsWith("a+") || value.startsWith("a%20"));
    }

    @Test
    void functionsJsonKeysMatchRegisteredImplementations() throws Exception {
        Set<String> registered = ServiceLoader.load(Function.class).stream()
                .map(ServiceLoader.Provider::get)
                .map(Function::getReferenceKey)
                .collect(Collectors.toSet());
        assertFalse(registered.isEmpty());

        String json = new String(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("functions.json"))
                        .readAllBytes(),
                StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("\"key\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        Set<String> catalog = new HashSet<>();
        while (matcher.find()) {
            catalog.add(matcher.group(1));
        }
        assertFalse(catalog.isEmpty());
        for (String key : catalog) {
            assertTrue(registered.contains(key), "missing implementation for key: " + key);
        }
    }

    private static boolean luhnValid(String number) {
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = number.charAt(i) - '0';
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }

    private static char checkDigit(String body) {
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] codes = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (body.charAt(i) - '0') * weights[i];
        }
        return codes[sum % 11];
    }
}
