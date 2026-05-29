/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.amenity.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.amenity.entity.AmenityBooking;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link AmenityBooking} entity.
 *
 * <p>Extends {@link JpaSpecificationExecutor} for dynamic filter queries on the list endpoint.
 * Contains three key custom queries:
 * <ol>
 *   <li>{@link #findConflicting} — pessimistic-write lock for double-booking prevention.</li>
 *   <li>{@link #countDailyBookings} — daily limit enforcement per resident.</li>
 *   <li>{@link #findByAmenityAndDate} — availability calendar query.</li>
 * </ol>
 */
@Repository
public interface AmenityBookingRepository
        extends JpaRepository<AmenityBooking, UUID>, JpaSpecificationExecutor<AmenityBooking> {

    /**
     * Returns all PENDING or APPROVED bookings for the given amenity and date that overlap
     * the requested time window.
     *
     * <p>A pessimistic write lock is acquired on the matching rows to prevent concurrent
     * booking creation from both passing the conflict check. The overlap condition is
     * {@code existingStart < requestedEnd AND existingEnd > requestedStart}.
     *
     * @param amenityId the amenity UUID to check.
     * @param date      the booking date to check.
     * @param startTime the requested start time.
     * @param endTime   the requested end time.
     * @return list of conflicting bookings; empty means the slot is free.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM AmenityBooking b
            WHERE b.amenity.id = :amenityId
              AND b.bookingDate = :date
              AND b.status IN ('PENDING', 'APPROVED')
              AND b.startTime < :endTime
              AND b.endTime > :startTime
            """)
    List<AmenityBooking> findConflicting(
            @Param("amenityId") UUID amenityId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime);

    /**
     * Counts the non-cancelled and non-rejected bookings a resident has placed for any amenity
     * on the given date.
     *
     * <p>Used to enforce {@code amenity.maxDailyBookingsPerResident} before insertion.
     *
     * @param residentId the resident UUID.
     * @param date       the booking date.
     * @return count of active (PENDING / APPROVED / COMPLETED) bookings on that date.
     */
    @Query("""
            SELECT COUNT(b) FROM AmenityBooking b
            WHERE b.resident.id = :residentId
              AND b.bookingDate = :date
              AND b.status NOT IN ('REJECTED', 'CANCELLED')
            """)
    long countDailyBookings(
            @Param("residentId") UUID residentId,
            @Param("date") LocalDate date);

    /**
     * Returns all PENDING or APPROVED bookings for the given amenity on the given date,
     * used to build the availability calendar response.
     *
     * @param amenityId the amenity UUID.
     * @param date      the date to query.
     * @return list of active bookings for that day.
     */
    @Query("""
            SELECT b FROM AmenityBooking b
            WHERE b.amenity.id = :amenityId
              AND b.bookingDate = :date
              AND b.status IN ('PENDING', 'APPROVED')
            """)
    List<AmenityBooking> findByAmenityAndDate(
            @Param("amenityId") UUID amenityId,
            @Param("date") LocalDate date);

    /**
     * Counts all bookings for a specific amenity within the given date range.
     *
     * <p>Used by the amenity-usage report to compute {@code totalBookings}.
     *
     * @param amenityId the amenity UUID.
     * @param from      start date (inclusive).
     * @param to        end date (inclusive).
     * @return total booking count.
     */
    @Query("""
            SELECT COUNT(b) FROM AmenityBooking b
            WHERE b.amenity.id = :amenityId
              AND b.bookingDate >= :from
              AND b.bookingDate <= :to
            """)
    long countByAmenityAndPeriod(
            @Param("amenityId") UUID amenityId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Counts bookings for a specific amenity, date range, and status.
     *
     * <p>Used by the amenity-usage report for APPROVED/REJECTED/CANCELLED breakdowns.
     *
     * @param amenityId the amenity UUID.
     * @param from      start date (inclusive).
     * @param to        end date (inclusive).
     * @param status    booking status string (e.g. {@code "APPROVED"}).
     * @return count of bookings matching all criteria.
     */
    @Query(value = """
            SELECT COUNT(*) FROM amenity_bookings
            WHERE amenity_id = :amenityId
              AND booking_date >= :from
              AND booking_date <= :to
              AND status = CAST(:status AS booking_status)
            """, nativeQuery = true)
    long countByAmenityPeriodAndStatus(
            @Param("amenityId") UUID amenityId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") String status);

    /**
     * Returns the date with the highest booking count for the given amenity and period.
     *
     * <p>Used by the amenity-usage report to populate {@code peakDay}.
     *
     * @param amenityId the amenity UUID.
     * @param from      start date (inclusive).
     * @param to        end date (inclusive).
     * @return the peak {@link LocalDate}, or empty if no bookings exist in the period.
     */
    @Query("""
            SELECT b.bookingDate FROM AmenityBooking b
            WHERE b.amenity.id = :amenityId
              AND b.bookingDate >= :from
              AND b.bookingDate <= :to
            GROUP BY b.bookingDate
            ORDER BY COUNT(b) DESC
            """)
    List<LocalDate> findPeakDay(
            @Param("amenityId") UUID amenityId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Counts total bookings created in the current calendar month (any status).
     *
     * <p>Used by the dashboard KPI.
     *
     * @param from first day of the current month.
     * @param to   today or last day of month.
     * @return total booking count.
     */
    @Query("""
            SELECT COUNT(b) FROM AmenityBooking b
            WHERE b.bookingDate >= :from
              AND b.bookingDate <= :to
            """)
    long countBookingsInPeriod(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Counts bookings currently in PENDING status.
     *
     * <p>Used by the dashboard KPI.
     *
     * @return count of PENDING bookings.
     */
    @Query("SELECT COUNT(b) FROM AmenityBooking b WHERE b.status = 'PENDING'")
    long countPending();
}
