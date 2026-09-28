package com.ridelink.ride.service;

import com.ridelink.ride.client.DriverServiceClient;
import com.ridelink.ride.client.FareServiceClient;
import com.ridelink.ride.dto.CreateRideRequest;
import com.ridelink.ride.dto.DriverDto;
import com.ridelink.ride.dto.FareEstimateRequest;
import com.ridelink.ride.dto.FareEstimateResponse;
import com.ridelink.ride.dto.RideResponse;
import com.ridelink.ride.dto.UpdateRideStatusRequest;
import com.ridelink.ride.exception.InvalidStatusTransitionException;
import com.ridelink.ride.exception.NoDriversAvailableException;
import com.ridelink.ride.exception.RideNotFoundException;
import com.ridelink.ride.model.Ride;
import com.ridelink.ride.model.RideStatus;
import com.ridelink.ride.repository.RideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RideService {

    private final RideRepository rideRepository;
    private final FareServiceClient fareServiceClient;
    private final DriverServiceClient driverServiceClient;

    /**
     * Request a new ride:
     * 1. Call FareServiceClient to fetch estimated fare synchronously.
     * 2. Query DriverServiceClient to check for eligible available drivers.
     * 3. If no drivers are available, throw NoDriversAvailableException.
     * 4. Save ride in MongoDB with status REQUESTED.
     */
    public RideResponse createRide(CreateRideRequest request) {
        log.info("Processing ride creation request for passengerId: {}", request.getPassengerId());

        // 1. Synchronously fetch estimated fare from FareServiceClient
        Double estimatedFare = null;
        try {
            FareEstimateRequest fareRequest = FareEstimateRequest.builder()
                    .pickupLocation(request.getPickupLocation())
                    .destinationLocation(request.getDestinationLocation())
                    .distanceKm(request.getDistanceKm())
                    .build();

            FareEstimateResponse fareResponse = fareServiceClient.estimateFare(fareRequest);
            if (fareResponse != null && fareResponse.getEstimatedFare() != null) {
                estimatedFare = fareResponse.getEstimatedFare();
                log.info("Received estimated fare from fare-service: {}", estimatedFare);
            }
        } catch (Exception ex) {
            log.warn("FareServiceClient call failed ({}). Applying default distance-based estimation.", ex.getMessage());
            // Fallback estimation: base fee 3.00 + 1.50 per km
            estimatedFare = Math.round((3.0 + (request.getDistanceKm() * 1.5)) * 100.0) / 100.0;
        }

        // 2. Query DriverServiceClient for available eligible drivers near pickup location
        List<DriverDto> availableDrivers = null;
        try {
            Double lat = request.getPickupLocation() != null ? request.getPickupLocation().getLatitude() : null;
            Double lng = request.getPickupLocation() != null ? request.getPickupLocation().getLongitude() : null;
            availableDrivers = driverServiceClient.getAvailableDrivers(lat, lng);
            log.info("DriverServiceClient returned {} available drivers", availableDrivers != null ? availableDrivers.size() : 0);
        } catch (Exception ex) {
            log.warn("DriverServiceClient call encountered an issue: {}", ex.getMessage());
        }

        // 3. Negative scenario check: If no drivers are available, throw NoDriversAvailableException
        if (availableDrivers == null || availableDrivers.isEmpty()) {
            log.warn("No available drivers found in pickup area for passenger: {}", request.getPassengerId());
            throw new NoDriversAvailableException("No eligible drivers available in this area");
        }

        // 4. Save ride in MongoDB with status REQUESTED
        Ride ride = Ride.builder()
                .passengerId(request.getPassengerId())
                .pickupLocation(request.getPickupLocation())
                .destinationLocation(request.getDestinationLocation())
                .distanceKm(request.getDistanceKm())
                .estimatedFare(estimatedFare)
                .status(RideStatus.REQUESTED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Ride savedRide = rideRepository.save(ride);
        log.info("Ride successfully created with ID: {} and status: {}", savedRide.getId(), savedRide.getStatus());

        return RideResponse.fromEntity(savedRide);
    }

    /**
     * Validate and assign a driver to a ride:
     * Transition allowed strictly from REQUESTED -> ASSIGNED.
     */
    public RideResponse assignDriver(String rideId, String driverId) {
        log.info("Assigning driver {} to ride {}", driverId, rideId);

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId, true));

        if (ride.getStatus() != RideStatus.REQUESTED) {
            log.error("Cannot assign driver to ride {} because current status is {}", rideId, ride.getStatus());
            throw new InvalidStatusTransitionException(ride.getStatus(), RideStatus.ASSIGNED);
        }

        ride.setDriverId(driverId);
        ride.setStatus(RideStatus.ASSIGNED);
        ride.setUpdatedAt(Instant.now());

        Ride updatedRide = rideRepository.save(ride);
        log.info("Ride {} transitioned to ASSIGNED with driver {}", rideId, driverId);

        return RideResponse.fromEntity(updatedRide);
    }

    /**
     * Strictly enforce lifecycle transition rules:
     * - ASSIGNED -> ACCEPTED
     * - ACCEPTED -> IN_PROGRESS
     * - IN_PROGRESS -> COMPLETED
     * - Any state prior to COMPLETED -> CANCELLED
     * Throw InvalidStatusTransitionException if an invalid state jump is attempted.
     */
    public RideResponse updateRideStatus(String rideId, UpdateRideStatusRequest request) {
        log.info("Updating status of ride {} to {}", rideId, request.getStatus());

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId, true));

        RideStatus currentStatus = ride.getStatus();
        RideStatus targetStatus = request.getStatus();

        if (targetStatus == null) {
            throw new IllegalArgumentException("Target ride status cannot be null");
        }

        boolean isValidTransition = false;

        switch (targetStatus) {
            case ACCEPTED:
                isValidTransition = (currentStatus == RideStatus.ASSIGNED);
                break;
            case IN_PROGRESS:
                isValidTransition = (currentStatus == RideStatus.ACCEPTED);
                break;
            case COMPLETED:
                isValidTransition = (currentStatus == RideStatus.IN_PROGRESS);
                break;
            case CANCELLED:
                // Any state prior to COMPLETED can transition to CANCELLED
                isValidTransition = (currentStatus != RideStatus.COMPLETED && currentStatus != RideStatus.CANCELLED);
                break;
            case ASSIGNED:
                isValidTransition = (currentStatus == RideStatus.REQUESTED);
                break;
            default:
                isValidTransition = false;
        }

        if (!isValidTransition) {
            log.error("Invalid status transition attempted on ride {}: {} -> {}", rideId, currentStatus, targetStatus);
            throw new InvalidStatusTransitionException(currentStatus, targetStatus);
        }

        // Assign driverId if transitioning to ASSIGNED or ACCEPTED with driver specified
        if (request.getDriverId() != null && !request.getDriverId().isBlank()) {
            ride.setDriverId(request.getDriverId());
        }

        // Set cancellation reason when cancelling
        if (targetStatus == RideStatus.CANCELLED && request.getReason() != null) {
            ride.setCancellationReason(request.getReason());
        }

        // Finalize fare when ride completes
        if (targetStatus == RideStatus.COMPLETED && ride.getFinalFare() == null) {
            ride.setFinalFare(ride.getEstimatedFare());
        }

        ride.setStatus(targetStatus);
        ride.setUpdatedAt(Instant.now());

        Ride updatedRide = rideRepository.save(ride);
        log.info("Ride {} successfully updated to status: {}", rideId, targetStatus);

        return RideResponse.fromEntity(updatedRide);
    }

    /**
     * Return ride details by ID.
     */
    public RideResponse getRideById(String rideId) {
        log.info("Fetching ride details for ID: {}", rideId);
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId, true));
        return RideResponse.fromEntity(ride);
    }

    /**
     * Return ride history for a passenger.
     */
    public List<RideResponse> getRidesByPassenger(String passengerId) {
        log.info("Fetching ride history for passengerId: {}", passengerId);
        List<Ride> rides = rideRepository.findByPassengerId(passengerId);
        return rides.stream()
                .map(RideResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
