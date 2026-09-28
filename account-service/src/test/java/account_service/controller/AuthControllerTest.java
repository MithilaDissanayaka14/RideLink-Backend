package account_service.controller;

import account_service.dto.LoginRequest;
import account_service.dto.LoginResponse;
import account_service.dto.RegisterRequest;
import account_service.dto.UserResponse;
import account_service.exception.AccountSuspendedException;
import account_service.exception.GlobalExceptionHandler;
import account_service.exception.InvalidCredentialsException;
import account_service.model.Role;
import account_service.model.UserStatus;
import account_service.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Validation Error: Register with invalid/missing fields returns 400 Bad Request with field errors")
    void register_ValidationError_Returns400WithDetails() throws Exception {
        RegisterRequest invalidRequest = RegisterRequest.builder()
                .fullName("") // blank
                .email("not-an-email") // invalid email
                .password("123") // too short (< 6)
                .phoneNumber("") // blank
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed for one or more fields"))
                .andExpect(jsonPath("$.details.email").exists())
                .andExpect(jsonPath("$.details.password").exists());
    }

    @Test
    @DisplayName("Negative Scenario: Login with SUSPENDED account returns 403 Forbidden")
    void login_SuspendedAccount_Returns403Forbidden() throws Exception {
        LoginRequest request = new LoginRequest("suspended@ridelink.com", "Secret123!");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new AccountSuspendedException("Account is inactive or suspended"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Account is inactive or suspended"));
    }

    @Test
    @DisplayName("Negative Scenario: Login with DEACTIVATED account returns 403 Forbidden")
    void login_DeactivatedAccount_Returns403Forbidden() throws Exception {
        LoginRequest request = new LoginRequest("deactivated@ridelink.com", "Secret123!");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new AccountSuspendedException("Account is inactive or suspended"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Account is inactive or suspended"));
    }

    @Test
    @DisplayName("Negative Scenario: Login with invalid credentials returns 401 Unauthorized")
    void login_InvalidCredentials_Returns401Unauthorized() throws Exception {
        LoginRequest request = new LoginRequest("wrong@ridelink.com", "badpass");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("Success Scenario: Login with valid credentials returns 200 OK")
    void login_Success_Returns200WithToken() throws Exception {
        LoginRequest request = new LoginRequest("valid@ridelink.com", "Password123!");
        UserResponse user = UserResponse.builder()
                .id("u1")
                .email("valid@ridelink.com")
                .role(Role.PASSENGER)
                .status(UserStatus.ACTIVE)
                .build();
        LoginResponse response = LoginResponse.builder()
                .accessToken("mock-jwt-token")
                .tokenType("Bearer")
                .expiresInMs(86400000L)
                .user(user)
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("mock-jwt-token"))
                .andExpect(jsonPath("$.data.user.email").value("valid@ridelink.com"));
    }
}
