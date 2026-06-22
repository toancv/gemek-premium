/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.vehicle;

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
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.apartment.repository.ApartmentRepository;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.vehicle.entity.Vehicle;
import vn.vtit.gemek.module.vehicle.mapper.VehicleMapper;
import vn.vtit.gemek.module.vehicle.repository.VehicleRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VehicleServiceImpl} — multi-residency per-context owns-check (P1).
 *
 * <p>The resident owns-check is now a per-apartment membership query
 * ({@code existsActiveByUserIdAndApartmentId}); these tests prove a RESIDENT may act on a vehicle
 * IFF they actively reside in THAT vehicle's apartment — independent of any other residency they hold.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VehicleServiceImplTest {

    @Mock private VehicleRepository vehicleRepository;
    @Mock private ResidentRepository residentRepository;
    @Mock private ApartmentRepository apartmentRepository;
    @Mock private VehicleMapper vehicleMapper;

    private VehicleServiceImpl service;

    private UUID vehicleId;
    private UUID apartmentId;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        service = new VehicleServiceImpl(
                vehicleRepository, residentRepository, apartmentRepository, vehicleMapper);

        vehicleId = UUID.randomUUID();
        apartmentId = UUID.randomUUID();

        Apartment apartment = new Apartment();
        apartment.setId(apartmentId);

        vehicle = new Vehicle();
        vehicle.setId(vehicleId);
        vehicle.setApartment(apartment);

        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
    }

    @Test
    @DisplayName("getVehicle — RESIDENT not residing in the vehicle's apartment throws FORBIDDEN")
    void getVehicle_residentNotInApartment_throwsForbidden() {
        UUID principalId = UUID.randomUUID();
        when(residentRepository.existsActiveByUserIdAndApartmentId(principalId, apartmentId))
                .thenReturn(false);

        assertThatThrownBy(() -> service.getVehicle(vehicleId, principalId, "RESIDENT"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("getVehicle — RESIDENT actively residing in the vehicle's apartment (per-context) is allowed")
    void getVehicle_residentInThatApartment_allowed() {
        UUID principalId = UUID.randomUUID();
        // Active residency in THIS apartment — passes regardless of any other apartments held.
        when(residentRepository.existsActiveByUserIdAndApartmentId(principalId, apartmentId))
                .thenReturn(true);

        assertThatNoException()
                .isThrownBy(() -> service.getVehicle(vehicleId, principalId, "RESIDENT"));
    }
}
