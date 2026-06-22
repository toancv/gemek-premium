/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * JVM-wide monotonic counter for amenity booking test slots.
 *
 * <p>Shared across {@code AmenityControllerTest} and {@code AmenityBookingIntegrationTest}
 * so that each {@code @BeforeEach} invocation receives a unique {@code (dayOffset, hour)}
 * combination, preventing cross-class slot conflicts on shared amenities.
 *
 * <p>With 13 days × 14 hours = 182 unique combinations and at most ~20 test instances
 * per suite run, the counter never wraps within a single JVM run.
 */
public final class TestSlotCounter {

    /** Global counter — incremented once per amenity test instance. */
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private TestSlotCounter() {
        // utility class
    }

    /**
     * Returns the next counter value, unique within this JVM run.
     *
     * @return the incremented counter value.
     */
    public static int next() {
        return COUNTER.incrementAndGet();
    }
}
