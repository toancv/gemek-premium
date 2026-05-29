/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.module.amenity.entity.Amenity;
import vn.vtit.gemek.module.amenity.repository.AmenityBookingRepository;
import vn.vtit.gemek.module.amenity.repository.AmenityRepository;
import vn.vtit.gemek.module.apartment.repository.ApartmentRepository;
import vn.vtit.gemek.module.contractor.entity.Contract;
import vn.vtit.gemek.module.contractor.repository.ContractRepository;
import vn.vtit.gemek.module.report.dto.AmenityUsageReportResponse;
import vn.vtit.gemek.module.report.dto.ContractsExpiringResponse;
import vn.vtit.gemek.module.report.dto.DashboardResponse;
import vn.vtit.gemek.module.report.dto.ResidentReportResponse;
import vn.vtit.gemek.module.report.dto.TicketReportResponse;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.ticket.repository.TicketRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of {@link ReportService}.
 *
 * <p>All methods are read-only transactions; no mutations occur. Aggregations are
 * pushed to the database via native queries for performance. Results are assembled
 * into DTOs in Java to avoid coupling the DB schema to JSON field names.
 */
@Service
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

    // SECURITY-FIX: allowlist for groupBy to prevent logic/query injection via unchecked parameter
    private static final Set<String> ALLOWED_GROUP_BY = Set.of("month", "category", "status", "assignee");

    private final ApartmentRepository apartmentRepository;
    private final TicketRepository ticketRepository;
    private final AmenityRepository amenityRepository;
    private final AmenityBookingRepository amenityBookingRepository;
    private final ContractRepository contractRepository;
    private final ResidentRepository residentRepository;

    /**
     * Constructs the service with all required repository dependencies.
     *
     * @param apartmentRepository      apartment data access.
     * @param ticketRepository         ticket data access.
     * @param amenityRepository        amenity data access.
     * @param amenityBookingRepository amenity booking data access.
     * @param contractRepository       contract data access.
     * @param residentRepository       resident data access.
     */
    public ReportServiceImpl(
            ApartmentRepository apartmentRepository,
            TicketRepository ticketRepository,
            AmenityRepository amenityRepository,
            AmenityBookingRepository amenityBookingRepository,
            ContractRepository contractRepository,
            ResidentRepository residentRepository) {
        this.apartmentRepository = apartmentRepository;
        this.ticketRepository = ticketRepository;
        this.amenityRepository = amenityRepository;
        this.amenityBookingRepository = amenityBookingRepository;
        this.contractRepository = contractRepository;
        this.residentRepository = residentRepository;
    }

    /** {@inheritDoc} */
    @Override
    public DashboardResponse getDashboard() {
        log.debug("Building dashboard KPIs");

        // Apartment stats — count by status
        DashboardResponse.ApartmentStats apartmentStats = buildApartmentStats();

        // Ticket stats — open, in-progress, overdue, avg resolution, byCategory
        DashboardResponse.TicketStats ticketStats = buildTicketStats();

        // Amenity stats — bookings this month, pending approval
        LocalDate today = LocalDate.now();
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        long bookingsThisMonth = amenityBookingRepository.countBookingsInPeriod(firstOfMonth, today);
        long pendingApproval = amenityBookingRepository.countPending();
        DashboardResponse.AmenityStats amenityStats =
                new DashboardResponse.AmenityStats(bookingsThisMonth, pendingApproval);

        // Contract stats — active, expiring in 30 and 90 days
        long activeContracts = contractRepository.countActive();
        long expiringIn30 = contractRepository.countActiveExpiring(today, today.plusDays(30));
        long expiringIn90 = contractRepository.countActiveExpiring(today, today.plusDays(90));
        DashboardResponse.ContractStats contractStats =
                new DashboardResponse.ContractStats(activeContracts, expiringIn30, expiringIn90);

        return new DashboardResponse(apartmentStats, ticketStats, amenityStats, contractStats);
    }

    /** {@inheritDoc} */
    @Override
    public TicketReportResponse getTicketReport(
            LocalDate from,
            LocalDate to,
            String groupBy,
            String category,
            UUID apartmentId) {

        // Default to last 90 days when no range provided
        LocalDate effectiveTo = (to != null) ? to : LocalDate.now();
        LocalDate effectiveFrom = (from != null) ? from : effectiveTo.minusDays(90);

        OffsetDateTime fromDt = effectiveFrom.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toDt = effectiveTo.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        String effectiveGroupBy = (groupBy != null && !groupBy.isBlank()) ? groupBy : "month";

        // SECURITY-FIX: validate groupBy against allowlist before passing to repository query
        if (!ALLOWED_GROUP_BY.contains(effectiveGroupBy)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid groupBy value: " + effectiveGroupBy);
        }

        log.debug("Ticket report: from={} to={} groupBy={}", effectiveFrom, effectiveTo, effectiveGroupBy);

        // Summary row
        Object[] summaryRow = ticketRepository.getTicketSummary(fromDt, toDt, category, apartmentId);
        TicketReportResponse.Summary summary = buildTicketSummary(summaryRow);

        // Breakdown rows
        List<Object[]> breakdownRows = ticketRepository.getTicketBreakdown(
                fromDt, toDt, category, apartmentId, effectiveGroupBy);
        List<TicketReportResponse.BreakdownRow> breakdown = buildBreakdown(breakdownRows);

        return new TicketReportResponse(
                new TicketReportResponse.Period(effectiveFrom, effectiveTo),
                summary,
                breakdown
        );
    }

    /** {@inheritDoc} */
    @Override
    public AmenityUsageReportResponse getAmenityUsageReport(
            LocalDate from,
            LocalDate to,
            UUID amenityId) {

        LocalDate effectiveTo = (to != null) ? to : LocalDate.now();
        LocalDate effectiveFrom = (from != null) ? from : effectiveTo.minusDays(30);

        log.debug("Amenity usage report: from={} to={} amenityId={}", effectiveFrom, effectiveTo, amenityId);

        // Fetch amenities to report on
        List<Amenity> amenities;
        if (amenityId != null) {
            amenities = amenityRepository.findById(amenityId)
                    .map(List::of)
                    .orElse(List.of());
        } else {
            amenities = amenityRepository.findAll();
        }

        List<AmenityUsageReportResponse.AmenityRow> rows = new ArrayList<>();
        for (Amenity amenity : amenities) {
            long total = amenityBookingRepository.countByAmenityAndPeriod(
                    amenity.getId(), effectiveFrom, effectiveTo);
            // Skip amenities with no bookings in period
            if (total == 0) {
                continue;
            }
            long approved = amenityBookingRepository.countByAmenityPeriodAndStatus(
                    amenity.getId(), effectiveFrom, effectiveTo, "APPROVED")
                    + amenityBookingRepository.countByAmenityPeriodAndStatus(
                    amenity.getId(), effectiveFrom, effectiveTo, "COMPLETED");
            long rejected = amenityBookingRepository.countByAmenityPeriodAndStatus(
                    amenity.getId(), effectiveFrom, effectiveTo, "REJECTED");
            long cancelled = amenityBookingRepository.countByAmenityPeriodAndStatus(
                    amenity.getId(), effectiveFrom, effectiveTo, "CANCELLED");

            List<LocalDate> peakDays = amenityBookingRepository.findPeakDay(
                    amenity.getId(), effectiveFrom, effectiveTo, PageRequest.of(0, 1));
            LocalDate peakDay = peakDays.isEmpty() ? null : peakDays.get(0);

            double utilizationRate = total > 0 ? (double) approved / total : 0.0;

            rows.add(new AmenityUsageReportResponse.AmenityRow(
                    new AmenityUsageReportResponse.AmenitySummary(amenity.getId(), amenity.getName()),
                    total,
                    approved,
                    rejected,
                    cancelled,
                    peakDay,
                    utilizationRate
            ));
        }

        return new AmenityUsageReportResponse(
                new AmenityUsageReportResponse.Period(effectiveFrom, effectiveTo),
                rows
        );
    }

    /** {@inheritDoc} */
    @Override
    public ContractsExpiringResponse getContractsExpiring(int withinDays) {
        LocalDate today = LocalDate.now();
        LocalDate maxDate = today.plusDays(withinDays);

        log.debug("Contracts expiring within {} days (by {})", withinDays, maxDate);

        List<Contract> contracts = contractRepository.findActiveExpiringWithContractor(today, maxDate);
        List<ContractsExpiringResponse.ContractRow> rows = new ArrayList<>(contracts.size());

        for (Contract contract : contracts) {
            long daysToExpiry = today.until(contract.getEndDate(),
                    java.time.temporal.ChronoUnit.DAYS);
            rows.add(new ContractsExpiringResponse.ContractRow(
                    contract.getId(),
                    contract.getTitle(),
                    new ContractsExpiringResponse.ContractorRef(
                            contract.getContractor().getId(),
                            contract.getContractor().getCompanyName()
                    ),
                    contract.getEndDate(),
                    daysToExpiry,
                    contract.getContractValue(),
                    contract.getCurrency(),
                    contract.getStatus().name()
            ));
        }

        return new ContractsExpiringResponse(today, rows);
    }

    /** {@inheritDoc} */
    @Override
    public ResidentReportResponse getResidentReport(UUID blockId) {
        log.debug("Resident report: blockId={}", blockId);

        long totalApartments = apartmentRepository.countByOptionalBlock(blockId);
        Object[] demo = residentRepository.getResidentDemographics(blockId);

        // Row: [totalActive, owners, tenants, occupiedApartments]
        long totalActive = toLong(demo[0]);
        long owners = toLong(demo[1]);
        long tenants = toLong(demo[2]);
        long occupied = toLong(demo[3]);

        double occupancyRate = totalApartments > 0 ? (double) occupied / totalApartments : 0.0;
        double avgPerApartment = occupied > 0 ? (double) totalActive / occupied : 0.0;

        return new ResidentReportResponse(
                totalApartments,
                occupied,
                occupancyRate,
                totalActive,
                owners,
                tenants,
                avgPerApartment
        );
    }

    // ── private helpers ────────────────────────────────────────────────────────

    /**
     * Builds the apartment stats block for the dashboard.
     * Counts are derived from a single GROUP-BY query.
     *
     * @return populated {@link DashboardResponse.ApartmentStats}.
     */
    private DashboardResponse.ApartmentStats buildApartmentStats() {
        List<Object[]> statusCounts = apartmentRepository.countByStatus();
        long total = 0;
        long occupied = 0;
        long available = 0;
        long maintenance = 0;

        for (Object[] row : statusCounts) {
            String status = (String) row[0];
            long count = toLong(row[1]);
            total += count;
            // Match against ApartmentStatus enum names
            if ("OCCUPIED".equals(status)) {
                occupied = count;
            } else if ("AVAILABLE".equals(status)) {
                available = count;
            } else if ("MAINTENANCE".equals(status)) {
                maintenance = count;
            }
        }

        double occupancyRate = total > 0 ? (double) occupied / total : 0.0;
        return new DashboardResponse.ApartmentStats(total, occupied, available, maintenance, occupancyRate);
    }

    /**
     * Builds the ticket stats block for the dashboard.
     *
     * @return populated {@link DashboardResponse.TicketStats}.
     */
    private DashboardResponse.TicketStats buildTicketStats() {
        OffsetDateTime since30Days = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
        Object[] kpiRow = ticketRepository.getDashboardTicketKpis(since30Days);

        long open = toLong(kpiRow[0]);
        long inProgress = toLong(kpiRow[1]);
        long overdue = toLong(kpiRow[2]);
        double avgHours = toDouble(kpiRow[3]);

        // Category breakdown for active (NEW + IN_PROGRESS) tickets
        List<Object[]> categoryRows = ticketRepository.countActiveByCategory();
        Map<String, Long> byCategory = new HashMap<>();
        for (Object[] row : categoryRows) {
            byCategory.put((String) row[0], toLong(row[1]));
        }

        return new DashboardResponse.TicketStats(open, inProgress, overdue, avgHours, byCategory);
    }

    /**
     * Builds a {@link TicketReportResponse.Summary} from a raw aggregate row.
     *
     * <p>Row order: [total, completed, cancelled, inProgress, newCount, slaBreached, avgRating].
     *
     * @param row raw {@code Object[]} from the native query.
     * @return populated summary record.
     */
    private TicketReportResponse.Summary buildTicketSummary(Object[] row) {
        long total = toLong(row[0]);
        long completed = toLong(row[1]);
        long cancelled = toLong(row[2]);
        long inProgress = toLong(row[3]);
        long newCount = toLong(row[4]);
        long slaBreached = toLong(row[5]);
        double avgRating = toDouble(row[6]);

        double slaBreachRate = total > 0 ? (double) slaBreached / total : 0.0;
        return new TicketReportResponse.Summary(
                total, completed, cancelled, inProgress, newCount, slaBreachRate, avgRating);
    }

    /**
     * Converts breakdown query rows to DTO records.
     *
     * <p>Row order: [label, total, completed, slaBreached, avgRating].
     *
     * @param rows list of raw {@code Object[]} from the native query.
     * @return list of populated breakdown rows.
     */
    private List<TicketReportResponse.BreakdownRow> buildBreakdown(List<Object[]> rows) {
        List<TicketReportResponse.BreakdownRow> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            String label = row[0] != null ? row[0].toString() : "Unknown";
            long total = toLong(row[1]);
            long completed = toLong(row[2]);
            long slaBreached = toLong(row[3]);
            double avgRating = toDouble(row[4]);
            result.add(new TicketReportResponse.BreakdownRow(label, total, completed, slaBreached, avgRating));
        }
        return result;
    }

    /**
     * Safely converts a potentially-null or differently-typed numeric DB result to {@code long}.
     *
     * @param value the raw column value.
     * @return numeric value as {@code long}, or {@code 0} if {@code null}.
     */
    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    /**
     * Safely converts a potentially-null or differently-typed numeric DB result to {@code double}.
     *
     * @param value the raw column value.
     * @return numeric value as {@code double}, or {@code 0.0} if {@code null}.
     */
    private double toDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }
}
