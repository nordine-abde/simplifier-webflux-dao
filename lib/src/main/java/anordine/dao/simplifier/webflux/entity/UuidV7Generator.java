package anordine.dao.simplifier.webflux.entity;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates RFC 9562 UUIDv7 identifiers.
 */
final class UuidV7Generator {

    private static final long VERSION_7_BITS = 0x7000L;
    private static final long VARIANT_BITS = 0x8000_0000_0000_0000L;
    private static final long RANDOM_B_MASK = 0x3fff_ffff_ffff_ffffL;
    private static final long COUNTER_MASK = 0xfffL;
    private static final int COUNTER_BITS = 12;
    private static final AtomicLong LAST_STATE = new AtomicLong();

    private UuidV7Generator() {
    }

    static UUID generate() {
        long state = nextState();
        long timestampMillis = state >>> COUNTER_BITS;
        long counter = state & COUNTER_MASK;

        long mostSignificantBits = (timestampMillis << 16) | VERSION_7_BITS | counter;
        long leastSignificantBits = VARIANT_BITS
                | (ThreadLocalRandom.current().nextLong() & RANDOM_B_MASK);

        return new UUID(mostSignificantBits, leastSignificantBits);
    }

    private static long nextState() {
        while (true) {
            long previous = LAST_STATE.get();
            long previousTimestampMillis = previous >>> COUNTER_BITS;
            long currentTimestampMillis = System.currentTimeMillis();
            long nextTimestampMillis = Math.max(currentTimestampMillis, previousTimestampMillis);
            long nextCounter;

            if (nextTimestampMillis == previousTimestampMillis) {
                long previousCounter = previous & COUNTER_MASK;
                if (previousCounter == COUNTER_MASK) {
                    nextTimestampMillis = waitForNextMillis(previousTimestampMillis);
                    nextCounter = randomCounter();
                } else {
                    nextCounter = previousCounter + 1;
                }
            } else {
                nextCounter = randomCounter();
            }

            long next = (nextTimestampMillis << COUNTER_BITS) | nextCounter;
            if (LAST_STATE.compareAndSet(previous, next)) {
                return next;
            }
        }
    }

    private static long randomCounter() {
        return ThreadLocalRandom.current().nextInt((int) COUNTER_MASK + 1);
    }

    private static long waitForNextMillis(long previousTimestampMillis) {
        long currentTimestampMillis;
        do {
            Thread.onSpinWait();
            currentTimestampMillis = System.currentTimeMillis();
        } while (currentTimestampMillis <= previousTimestampMillis);
        return currentTimestampMillis;
    }
}
