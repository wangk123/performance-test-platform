package com.yr.perftest.platform.seed;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LogicalFingerprint {
    private LogicalFingerprint() {
    }

    public static String rowFingerprint(Map<String, ?> row) {
        return sha256(CanonicalRowEncoding.encodeRow(row));
    }

    public static String rowFingerprint(List<?> values) {
        return sha256(CanonicalRowEncoding.encodeRow(values));
    }

    public static String chunkFingerprint(Iterable<? extends Map<String, ?>> rows) {
        ChunkAccumulator accumulator = new ChunkAccumulator();
        for (Map<String, ?> row : rows) {
            accumulator.addRow(row);
        }
        return accumulator.finish();
    }

    public static String multisetFingerprint(Iterable<? extends Map<String, ?>> rows) {
        MultisetAccumulator accumulator = new MultisetAccumulator();
        for (Map<String, ?> row : rows) {
            accumulator.addRow(row);
        }
        return accumulator.finish();
    }

    public static String schemaFingerprint(Iterable<?> columns) {
        StringBuilder schema = new StringBuilder();
        for (Object column : columns) {
            String value = Objects.toString(column, "NULL");
            schema.append(value.length()).append(':').append(value);
        }
        return sha256(schema.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static String tableFingerprint(String schemaHash, long rowCount, String contentHash) {
        String value = "schema=" + lengthPrefixed(schemaHash)
                + "|rows=" + rowCount
                + "|content=" + lengthPrefixed(contentHash);
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] value) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public static MultisetAccumulator newMultisetAccumulator() {
        return new MultisetAccumulator();
    }

    public static ChunkAccumulator newChunkAccumulator() {
        return new ChunkAccumulator();
    }

    public static final class ChunkAccumulator {
        private final MessageDigest digest;
        private long rowCount;
        private String result;

        private ChunkAccumulator() {
            try {
                this.digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("SHA-256 is unavailable", ex);
            }
        }

        public void addRow(Map<String, ?> row) {
            ensureOpen();
            byte[] encoded = CanonicalRowEncoding.encodeRow(row);
            digest.update(intBytes(encoded.length));
            digest.update(encoded);
            rowCount++;
        }

        public void addRow(List<?> values) {
            ensureOpen();
            byte[] encoded = CanonicalRowEncoding.encodeRow(values);
            digest.update(intBytes(encoded.length));
            digest.update(encoded);
            rowCount++;
        }

        public long rowCount() {
            return rowCount;
        }

        public String finish() {
            if (result == null) {
                digest.update(longBytes(rowCount));
                result = hex(digest.digest());
            }
            return result;
        }

        private void ensureOpen() {
            if (result != null) {
                throw new IllegalStateException("chunk fingerprint already finished");
            }
        }
    }

    public static final class MultisetAccumulator {
        private long rowCount;
        private long sum;
        private long xor;
        private String result;

        private MultisetAccumulator() {
        }

        public void addRow(Map<String, ?> row) {
            if (result != null) {
                throw new IllegalStateException("multiset fingerprint already finished");
            }
            byte[] digest = digestBytes(CanonicalRowEncoding.encodeRow(row));
            long first = ByteBuffer.wrap(digest).getLong();
            long second = ByteBuffer.wrap(digest, Long.BYTES, Long.BYTES).getLong();
            rowCount++;
            sum += first;
            xor ^= second;
        }

        public long rowCount() {
            return rowCount;
        }

        public String finish() {
            if (result == null) {
                byte[] value = ByteBuffer.allocate(Long.BYTES * 3)
                        .putLong(rowCount)
                        .putLong(sum)
                        .putLong(xor)
                        .array();
                result = sha256(value);
            }
            return result;
        }
    }

    private static String lengthPrefixed(String value) {
        String nonNull = Objects.toString(value, "NULL");
        return nonNull.length() + ":" + nonNull;
    }

    private static byte[] intBytes(int value) {
        return new byte[]{
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
    }

    private static byte[] longBytes(long value) {
        byte[] bytes = new byte[Long.BYTES];
        for (int i = Long.BYTES - 1; i >= 0; i--) {
            bytes[i] = (byte) value;
            value >>>= 8;
        }
        return bytes;
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xF, 16));
            result.append(Character.forDigit(value & 0xF, 16));
        }
        return result.toString();
    }

    private static byte[] digestBytes(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
