package com.yr.perftest.platform.seed;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class CanonicalRowEncoding {
    private static final int VERSION = 1;

    private CanonicalRowEncoding() {
    }

    public static byte[] encodeRow(Map<String, ?> row) {
        Objects.requireNonNull(row, "row");
        List<Map.Entry<String, ?>> fields = new ArrayList<>(row.entrySet());
        return encodeFields(fields);
    }

    public static byte[] encodeRow(List<?> values) {
        Objects.requireNonNull(values, "values");
        List<Map.Entry<String, ?>> fields = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            fields.add(new java.util.AbstractMap.SimpleImmutableEntry<>("#" + i, values.get(i)));
        }
        return encodeFields(fields);
    }

    private static byte[] encodeFields(List<Map.Entry<String, ?>> fields) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(VERSION);
            output.writeInt(fields.size());
            for (Map.Entry<String, ?> field : fields) {
                writeBytes(output, Objects.requireNonNull(field.getKey(), "column name")
                        .getBytes(StandardCharsets.UTF_8));
                writeValue(output, field.getValue());
            }
            output.flush();
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("canonical row encoding failed", ex);
        }
    }

    private static void writeValue(DataOutputStream output, Object value) throws IOException {
        if (value == null) {
            writeTypedBytes(output, "NULL", new byte[0]);
            return;
        }
        if (value instanceof byte[] binary) {
            writeTypedBytes(output, "BINARY", binary);
            return;
        }
        if (value instanceof ByteBuffer buffer) {
            ByteBuffer copy = buffer.asReadOnlyBuffer();
            byte[] binary = new byte[copy.remaining()];
            copy.get(binary);
            writeTypedBytes(output, "BINARY", binary);
            return;
        }
        if (value instanceof Byte[] binary) {
            byte[] bytes = new byte[binary.length];
            for (int i = 0; i < binary.length; i++) {
                bytes[i] = Objects.requireNonNull(binary[i], "binary value");
            }
            writeTypedBytes(output, "BINARY", bytes);
            return;
        }
        if (value instanceof Number number) {
            writeTypedBytes(output, "NUMERIC", normalizeNumber(number).getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (value instanceof TemporalAccessor temporal) {
            String representation = value.getClass().getName() + ":" + temporal;
            writeTypedBytes(output, "TEMPORAL", representation.getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            String representation = timestamp.getTime() + ":" + timestamp.getNanos();
            writeTypedBytes(output, "TEMPORAL", representation.getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (value instanceof Date date) {
            writeTypedBytes(output, "TEMPORAL",
                    Long.toString(date.getTime()).getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (value instanceof UUID uuid) {
            writeTypedBytes(output, "UUID", uuid.toString().getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (value instanceof CharSequence || value instanceof Character || value instanceof Enum<?>) {
            writeTypedBytes(output, "TEXT", value.toString().getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (value instanceof Boolean bool) {
            writeTypedBytes(output, "BOOLEAN", bool.toString().getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            writeTypedBytes(output, "MAP", encodeNestedMap(map));
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            writeTypedBytes(output, "LIST", encodeNestedValues(iterable));
            return;
        }
        if (value.getClass().isArray()) {
            List<Object> values = new ArrayList<>(Array.getLength(value));
            for (int i = 0; i < Array.getLength(value); i++) {
                values.add(Array.get(value, i));
            }
            writeTypedBytes(output, "LIST", encodeRow(values));
            return;
        }
        String representation = value.getClass().getName() + ":" + value;
        writeTypedBytes(output, "OBJECT", representation.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] encodeNestedMap(Map<?, ?> map) {
        List<Map.Entry<String, ?>> fields = new ArrayList<>(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            fields.add(new java.util.AbstractMap.SimpleImmutableEntry<>(
                    String.valueOf(entry.getKey()),
                    entry.getValue()
            ));
        }
        fields.sort(Comparator.comparing(Map.Entry::getKey));
        return encodeFields(fields);
    }

    private static byte[] encodeNestedValues(Iterable<?> values) {
        List<Object> list = new ArrayList<>();
        values.forEach(list::add);
        return encodeRow(list);
    }

    private static String normalizeNumber(Number number) {
        if (number instanceof Double || number instanceof Float) {
            double value = number.doubleValue();
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return number.toString();
            }
        }
        try {
            java.math.BigDecimal decimal = new java.math.BigDecimal(number.toString());
            if (decimal.signum() == 0) {
                return "0";
            }
            return decimal.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ex) {
            return number.toString();
        }
    }

    private static void writeTypedBytes(DataOutputStream output, String type, byte[] value) throws IOException {
        writeBytes(output, type.getBytes(StandardCharsets.UTF_8));
        writeBytes(output, value);
    }

    private static void writeBytes(DataOutputStream output, byte[] value) throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }
}
