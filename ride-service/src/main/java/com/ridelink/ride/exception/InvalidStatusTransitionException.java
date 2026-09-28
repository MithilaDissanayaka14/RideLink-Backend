package com.ridelink.ride.exception;

import com.ridelink.ride.model.RideStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }

    public InvalidStatusTransitionException(RideStatus currentStatus, RideStatus targetStatus) {
        super(String.format("Invalid ride status transition from %s to %s", currentStatus, targetStatus));
    }
}
