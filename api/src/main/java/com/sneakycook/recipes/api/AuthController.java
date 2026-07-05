package com.sneakycook.recipes.api;

import com.sneakycook.recipes.api.generated.AuthApi;
import com.sneakycook.recipes.api.generated.model.LoginRequest;
import com.sneakycook.recipes.api.generated.model.RegisterRequest;
import com.sneakycook.recipes.api.generated.model.TokenResponse;
import com.sneakycook.recipes.api.generated.model.UserResponse;
import com.sneakycook.recipes.application.AuthenticateUser;
import com.sneakycook.recipes.application.RegisterUser;
import com.sneakycook.recipes.domain.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP edge for accounts and tokens [REQ-17]. Implements the interface
 * generated from {@code recipe-api.yaml}; credentials are verified by the
 * application layer, token signing happens in {@link JwtTokenService}.
 */
@RestController
public class AuthController implements AuthApi {

    private final RegisterUser registerUser;
    private final AuthenticateUser authenticateUser;
    private final JwtTokenService tokens;
    private final AuthApiMapper mapper;

    public AuthController(
            RegisterUser registerUser,
            AuthenticateUser authenticateUser,
            JwtTokenService tokens,
            AuthApiMapper mapper) {
        this.registerUser = registerUser;
        this.authenticateUser = authenticateUser;
        this.tokens = tokens;
        this.mapper = mapper;
    }

    /** [REQ-17] 201 with the created account; 409 via {@code UsernameTakenException}. */
    @Override
    public ResponseEntity<UserResponse> register(RegisterRequest registerRequest) {
        User user = registerUser.execute(registerRequest.getUsername(), registerRequest.getPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toApi(user));
    }

    /** [REQ-17] 200 with a bearer token; 401 via {@code InvalidCredentialsException}. */
    @Override
    public ResponseEntity<TokenResponse> login(LoginRequest loginRequest) {
        User user = authenticateUser.execute(loginRequest.getUsername(), loginRequest.getPassword());
        JwtTokenService.IssuedToken issued = tokens.issue(user.id());
        return ResponseEntity.ok(new TokenResponse(issued.accessToken(), "Bearer", issued.expiresInSeconds()));
    }
}
