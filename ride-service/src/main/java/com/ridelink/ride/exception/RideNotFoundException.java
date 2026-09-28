package com.ridelink.ride.exception;

public class RideNotFoundException extends RuntimeException {

    public RideNotFoundException(String message) {
        super(message);
    }

    public RideNotFoundException(String rideId, boolean isId) {
        super("Ride not found with ID: " + rideId);
    }
}
