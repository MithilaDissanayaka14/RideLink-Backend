package account_service.controller;

import account_service.dto.ApiResponse;
import account_service.dto.UpdateProfileRequest;
import account_service.dto.UserResponse;
import account_service.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Endpoints for viewing and updating user profiles")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    @Operation(summary = "Get current user profile", description = "Retrieves profile details for the authenticated user based on JWT token")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUserProfile(Authentication authentication) {
        String currentUserId = authentication.getName();
        UserResponse response = userService.getUserProfile(currentUserId);
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user profile by ID", description = "Retrieves user profile details by ID for the authorized user or admin")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable String id, Authentication authentication) {
        validateUserAccess(id, authentication);
        UserResponse response = userService.getUserProfile(id);
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved successfully", response));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update current user profile", description = "Updates full name and phone number for the authenticated user")
    public ResponseEntity<ApiResponse<UserResponse>> updateCurrentUserProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        String currentUserId = authentication.getName();
        UserResponse response = userService.updateUserProfile(currentUserId, request);
        return ResponseEntity.ok(ApiResponse.success("User profile updated successfully", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user profile by ID", description = "Updates profile details by ID for the authorized user or admin")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserById(
            @PathVariable String id,
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        validateUserAccess(id, authentication);
        UserResponse response = userService.updateUserProfile(id, request);
        return ResponseEntity.ok(ApiResponse.success("User profile updated successfully", response));
    }

    private void validateUserAccess(String targetUserId, Authentication authentication) {
        String currentUserId = authentication.getName();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!currentUserId.equals(targetUserId) && !isAdmin) {
            throw new AccessDeniedException("You are not authorized to access or modify this profile");
        }
    }
}
