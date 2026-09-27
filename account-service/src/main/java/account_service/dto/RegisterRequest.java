package account_service.dto;

import account_service.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload required to register a new user account")
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    @Schema(description = "Full name of the user", example = "John Doe")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Schema(description = "Unique email address", example = "john.doe@example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 50, message = "Password must be between 6 and 50 characters")
    @Schema(description = "Account password (min 6 characters)", example = "SecurePass123!")
    private String password;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[0-9. ()-]{7,25}$", message = "Please provide a valid phone number")
    @Schema(description = "Contact phone number with optional country code", example = "+1-555-123-4567")
    private String phoneNumber;

    @Schema(description = "Account role (defaults to PASSENGER)", example = "PASSENGER", allowableValues = {"PASSENGER", "DRIVER", "ADMIN"})
    private Role role;
}
