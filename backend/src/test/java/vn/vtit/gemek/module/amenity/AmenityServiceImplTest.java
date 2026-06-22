/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.amenity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.vtit.gemek.module.amenity.dto.CreateBookingRequest;
import vn.vtit.gemek.module.amenity.entity.Amenity;
import vn.vtit.gemek.module.amenity.entity.AmenityBooking;
import vn.vtit.gemek.module.amenity.repository.AmenityBookingRepository;
import vn.vtit.gemek.module.amenity.repository.AmenityRepository;
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.resident.entity.ResidentType;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AmenityServiceImpl} — multi-residency safe-temporary attribution (P1).
 *
 * <p>[PLANNED] The real "which apartment is a booking charged to" rule is pending CTO ruling. For P1
 * the booking is attributed to a deterministically-selected residency (primary contact first, else
 * latest move-in, else id) so the call-site never throws under multi-residency. These tests prove the
 * deterministic selection; single-residency behavior is unchanged.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AmenityServiceImplTest {

    @Mock private AmenityRepository amenityRepository;
    @Mock private AmenityBookingRepository bookingRepository;
    @Mock private ResidentRepository residentRepository;
    @Mock private UserRepository userRepository;

    private AmenityServiceImpl service;

    private UUID principalId;
    private UUID amenityId;
    private Amenity amenity;

    @BeforeEach
    void setUp() {
        service = new AmenityServiceImpl(
                amenityRepository, bookingRepository, residentRepository, userRepository);

        principalId = UUID.randomUUID();
        amenityId = UUID.randomUUID();

        amenity = new Amenity();
        amenity.setId(amenityId);
        amenity.setName("Pool");
        amenity.setOpeningTime(LocalTime.of(6, 0));
        amenity.setClosingTime(LocalTime.of(22, 0));
        amenity.setMaxDailyBookingsPerResident((short) 5);
        amenity.setRequiresApproval(false);

        when(amenityRepository.findById(amenityId)).thenReturn(Optional.of(amenity));
        when(bookingRepository.countDailyBookings(any(), any())).thenReturn(0L);
        when(bookingRepository.findConflicting(any(), any(), any(), any())).thenReturn(List.of());
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("createBooking — multi-residency: booking is attributed to the primary-or-latest residency (first of ordered list)")
    void createBooking_multiResidency_attributesToSelectedResidency() {
        // Repository returns residencies pre-ordered (primary, then latest move-in, then id);
        // the service takes the FIRST, deterministically — here the primary-contact residency.
        Resident primary = residency("0901000001", true, LocalDate.of(2026, 1, 1));
        Resident other = residency("0901000002", false, LocalDate.of(2026, 5, 1));
        when(residentRepository.findAllActiveByUserId(principalId))
                .thenReturn(List.of(primary, other));

        CreateBookingRequest req = new CreateBookingRequest();
        req.setAmenityId(amenityId);
        req.setBookingDate(LocalDate.now().plusDays(1));
        req.setStartTime(LocalTime.of(8, 0));
        req.setEndTime(LocalTime.of(9, 0));

        service.createBooking(req, principalId);

        ArgumentCaptor<AmenityBooking> captor = ArgumentCaptor.forClass(AmenityBooking.class);
        org.mockito.Mockito.verify(bookingRepository).save(captor.capture());
        AmenityBooking saved = captor.getValue();
        // Attributed to the FIRST (primary) residency and its apartment — not the other one.
        assertThat(saved.getResident()).isSameAs(primary);
        assertThat(saved.getApartment()).isSameAs(primary.getApartment());
    }

    /**
     * Builds an active residency with its own apartment and user.
     *
     * @param phone          the user's phone.
     * @param primaryContact whether this residency is the apartment's primary contact.
     * @param moveInDate     the move-in date.
     * @return the active {@link Resident}.
     */
    private Resident residency(String phone, boolean primaryContact, LocalDate moveInDate) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Resident " + phone);
        user.setPhone(phone);

        Apartment apartment = new Apartment();
        apartment.setId(UUID.randomUUID());
        apartment.setUnitNumber("A-" + phone.substring(phone.length() - 2));

        Resident resident = new Resident();
        resident.setId(UUID.randomUUID());
        resident.setUser(user);
        resident.setApartment(apartment);
        resident.setType(ResidentType.OWNER);
        resident.setPrimaryContact(primaryContact);
        resident.setMoveInDate(moveInDate);
        return resident;
    }
}
