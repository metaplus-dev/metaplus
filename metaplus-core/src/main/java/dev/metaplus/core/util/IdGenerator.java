package dev.metaplus.core.util;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates compact base62 identifiers with a stable prefix and ordered time component.
 *
 * Example:
 * <pre>{@code
 * String id = IdGenerator.newId20("table");
 * // e.g. mpA1b2C3d4E5f6G7h8
 * }</pre>
 */
@Slf4j
public class IdGenerator {

    private static final char[] BASE62 =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private static final AtomicLong mixedCounter = new AtomicLong(
            ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE);

    /**
     * Returns a deterministic 2-character base62 code for the input key.
     */
    private static char[] getKeyCode(String key) {
        if (null == key) return new char[]{'0', '0'};
        char[] keyCode = new char[2];
        int hashCode = (key.hashCode() + 1_000_003) & Integer.MAX_VALUE;
        keyCode[0] = BASE62[hashCode % 62];
        hashCode /= 62;
        keyCode[1] = BASE62[hashCode % 62];
        return keyCode;
    }

    /**
     * Builds a 20-character id: prefix(2) + key code(2) + node part(4)
     * + timestamp(8) + sequence(4).
     */
    public static String newId20(String key) {
        char[] buffer = new char[20];

        buffer[0] = 'm';
        buffer[1] = 'p';

        char[] keyCode = getKeyCode(key);
        buffer[2] = keyCode[0];
        buffer[3] = keyCode[1];

        ThreadLocalRandom random = ThreadLocalRandom.current();
        long nodePart = random.nextLong(14_776_336L); // 62^4
        for (int i = 4; i < 8; i++) {
            buffer[i] = BASE62[(int) (nodePart % 62)];
            nodePart /= 62;
        }

        long timestamp = Instant.now().toEpochMilli();
        for (int i = 8; i < 16; i++) {
            buffer[i] = BASE62[(int) (timestamp % 62)];
            timestamp /= 62;
        }

        long sequence = mixedCounter.getAndIncrement() & Long.MAX_VALUE;
        for (int i = 16; i < 20; i++) {
            buffer[35 - i] = BASE62[(int) (sequence % 62)];
            sequence /= 62;
        }

        return new String(buffer);
    }
}
