/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.ticket;

import vn.vtit.gemek.module.ticket.entity.TicketStatus;

import java.util.EnumMap;
import java.util.Map;

/**
 * Vietnamese display labels for {@link TicketStatus}, used in BE-generated
 * notification bodies (N3 P3).
 *
 * <p>Values are copied VERBATIM from the locked FE map
 * ({@code frontend/packages/ui/src/lib/enumLabels.ts}, {@code TicketStatus}
 * section) — DONE is «Hoàn tất», never «Hoàn thành». Any future label change
 * must update BOTH files together (DECISIONS.md, N3 P3 entry).
 */
public final class TicketStatusLabels {

    /** Status → locked VN label. Mirror of the FE enumLabels TicketStatus map. */
    private static final Map<TicketStatus, String> LABELS;

    static {
        // EnumMap for O(1) lookup; populated once at class load.
        LABELS = new EnumMap<>(TicketStatus.class);
        LABELS.put(TicketStatus.NEW, "Mới");
        LABELS.put(TicketStatus.ASSIGNED, "Đã phân công");
        LABELS.put(TicketStatus.IN_PROGRESS, "Đang xử lý");
        LABELS.put(TicketStatus.DONE, "Hoàn tất");
        LABELS.put(TicketStatus.CANCELLED, "Đã hủy");
    }

    /**
     * Returns the locked Vietnamese label for the given status.
     *
     * @param status the ticket status.
     * @return the VN display label.
     */
    public static String labelOf(TicketStatus status) {
        return LABELS.get(status);
    }

    /**
     * Utility class — not instantiable.
     */
    private TicketStatusLabels() {
    }
}
