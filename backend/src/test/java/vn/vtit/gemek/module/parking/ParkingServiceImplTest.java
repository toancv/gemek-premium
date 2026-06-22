/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.parking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.module.apartment.repository.ApartmentRepository;
import vn.vtit.gemek.module.parking.dto.CreateParkingSlotRequest;
import vn.vtit.gemek.module.parking.entity.ParkingSlotType;
import vn.vtit.gemek.module.parking.repository.GuestVehicleRepository;
import vn.vtit.gemek.module.parking.repository.ParkingAssignmentRepository;
import vn.vtit.gemek.module.parking.repository.ParkingSlotRepository;
import vn.vtit.gemek.module.user.repository.UserRepository;
import vn.vtit.gemek.module.vehicle.repository.VehicleRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ParkingServiceImpl} — specific error code guards.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParkingServiceImplTest {

    @Mock private ParkingSlotRepository slotRepository;
    @Mock private ParkingAssignmentRepository assignmentRepository;
    @Mock private GuestVehicleRepository guestVehicleRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private ApartmentRepository apartmentRepository;
    @Mock private UserRepository userRepository;

    private ParkingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ParkingServiceImpl(
                slotRepository, assignmentRepository, guestVehicleRepository,
                vehicleRepository, apartmentRepository, userRepository);
    }

    // =========================================================================
    // createSlot — duplicate slot number → SLOT_NUMBER_ALREADY_EXISTS
    // =========================================================================

    @Test
    @DisplayName("createSlot — duplicate slot number throws SLOT_NUMBER_ALREADY_EXISTS")
    void createSlot_duplicateSlotNumber_throwsSlotNumberAlreadyExists() {
        when(slotRepository.existsBySlotNumber("B1-001")).thenReturn(true);

        CreateParkingSlotRequest req = new CreateParkingSlotRequest("B1-001", "B1", ParkingSlotType.CAR, null);

        assertThatThrownBy(() -> service.createSlot(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SLOT_NUMBER_ALREADY_EXISTS));
    }
}
