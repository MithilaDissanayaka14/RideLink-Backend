package com.ridelink.driver.service;

import com.ridelink.driver.dto.CreateDriverRequest;
import com.ridelink.driver.dto.DriverResponse;
import com.ridelink.driver.dto.UpdateLocationRequest;
import com.ridelink.driver.exception.ResourceNotFoundException;
import com.ridelink.driver.model.Driver;
import com.ridelink.driver.model.DriverStatus;
import com.ridelink.driver.model.Location;
import com.ridelink.driver.model.Vehicle;
import com.ridelink.driver.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRepository driverRepository;

    /**
     * Register a new driver profile.
     * Checks if a profile already exists for the given userId. If so, throws IllegalArgumentException.
     * Initializes default status to OFFLINE and saves driver in driver_db.
     */
    public DriverResponse registerDriver(CreateDriverRequest request) {
        log.info("Processing driver registration for userId: {}", request.getUserId());

        if (driverRepository.findByUserId(request.getUserId()).isPresent()) {
            log.error("Driver profile already exists for userId: {}", request.getUserId());
            throw new IllegalArgumentException("Driver profile already exists for userId: " + request.getUserId());
        }

        Vehicle vehicle = null;
        if (request.getVehicle() != null) {
            vehicle = Vehicle.builder()
                    .vehicleNumber(request.getVehicle().getVehicleNumber())
                    .model(request.getVehicle().getModel())
                    .vehicleType(request.getVehicle().getVehicleType())
                    .color(request.getVehicle().getColor())
                    .build();
        }

        Driver driver = Driver.builder()
                .userId(request.getUserId())
                .fullName(request.getFullName())
                .phoneNumber(request.getPhoneNumber())
                .licenseNumber(request.getLicenseNumber())
                .vehicle(vehicle)
                .status(DriverStatus.OFFLINE)
                .build();

        Driver savedDriver = driverRepository.save(driver);
        log.info("Driver successfully registered with ID: {}", savedDriver.getId());
        return DriverResponse.fromEntity(savedDriver);
    }

    /**
     * Update driver operational availability status (AVAILABLE, ON_TRIP, OFFLINE).
     */
    public DriverResponse updateStatus(String driverId, DriverStatus status) {
        log.info("Updating status for driver ID: {} to {}", driverId, status);

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> {
                    log.error("Driver not found with id: {}", driverId);
                    return new ResourceNotFoundException("Driver not found with id: " + driverId);
                });

        driver.setStatus(status);
        Driver updatedDriver = driverRepository.save(driver);
        log.info("Driver ID: {} status updated to {}", driverId, status);
        return DriverResponse.fromEntity(updatedDriver);
    }

    /**
     * Update simulated latitude, longitude, and serviceArea.
     */
    public DriverResponse updateLocation(String driverId, UpdateLocationRequest request) {
        log.info("Updating location for driver ID: {} - Lat: {}, Lng: {}, Area: {}",
                driverId, request.getLatitude(), request.getLongitude(), request.getServiceArea());

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> {
                    log.error("Driver not found with id: {}", driverId);
                    return new ResourceNotFoundException("Driver not found with id: " + driverId);
                });

        Location location = Location.builder()
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .serviceArea(request.getServiceArea())
                .build();

        driver.setCurrentLocation(location);
        Driver updatedDriver = driverRepository.save(driver);
        log.info("Driver ID: {} location updated successfully", driverId);
        return DriverResponse.fromEntity(updatedDriver);
    }

    /**
     * Query drivers whose status is AVAILABLE and match the specified serviceArea
     * (or return all available drivers if serviceArea is null or empty).
     * Returns an empty list if none are found.
     */
    public List<DriverResponse> getAvailableDrivers(String serviceArea) {
        log.info("Querying available drivers for serviceArea: '{}'", serviceArea);

        List<Driver> drivers;
        if (serviceArea != null && !serviceArea.trim().isEmpty()) {
            drivers = driverRepository.findByStatusAndCurrentLocation_ServiceArea(DriverStatus.AVAILABLE, serviceArea.trim());
        } else {
            drivers = driverRepository.findByStatus(DriverStatus.AVAILABLE);
        }

        if (drivers == null || drivers.isEmpty()) {
            log.info("No available drivers found for serviceArea: '{}'", serviceArea);
            return Collections.emptyList();
        }

        log.info("Found {} available driver(s) for serviceArea: '{}'", drivers.size(), serviceArea);
        return drivers.stream()
                .map(DriverResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve driver operational details by ID.
     */
    public DriverResponse getDriverById(String driverId) {
        log.info("Fetching driver operational profile for ID: {}", driverId);

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> {
                    log.error("Driver not found with id: {}", driverId);
                    return new ResourceNotFoundException("Driver not found with id: " + driverId);
                });

        return DriverResponse.fromEntity(driver);
    }
}
