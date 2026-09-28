package com.ridelink.ride.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Geographic coordinates and optional address for pickup or destination")
public class Location {

    @NotNull(message = "Latitude is required")
    @Schema(description = "Latitude coordinate in decimal degrees", example = "37.7749")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @Schema(description = "Longitude coordinate in decimal degrees", example = "-122.4194")
    private Double longitude;

    @Schema(description = "Human-readable street address or landmark", example = "Market St & 4th St, San Francisco, CA")
    private String address;
}
