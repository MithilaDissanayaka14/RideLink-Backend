package com.ridelink.ride.exception;

public class NoDriversAvailableException extends RuntimeException {

    public NoDriversAvailableException(String message) {
        super(message);
    }

    public NoDriversAvailableException() {
        super("No available eligible drivers found in your pickup area. Please try again later.");
    }
}
