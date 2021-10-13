package com.praisesystem.backend.auth;

import com.praisesystem.backend.auth.dto.AuthenticationRequestDto;
import com.praisesystem.backend.auth.dto.AuthenticationResponseDto;
import com.praisesystem.backend.auth.dto.GetNonceResponseDto;
import com.praisesystem.backend.common.validators.EthereumAddress;
import com.praisesystem.backend.configuration.configs.security.jwt.JwtTokenProvider;
import com.praisesystem.backend.users.dto.UserDto;
import com.praisesystem.backend.users.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Tag(name = "Authentication Controller")
@Slf4j
@Validated
@RestController
@AllArgsConstructor
@RequestMapping("/api/auth")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthController {

    AuthenticationManager authenticationManager;
    JwtTokenProvider jwtTokenProvider;
    UserService userService;

    @Operation(description = "Authentication")
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public AuthenticationResponseDto auth(@Valid @RequestBody AuthenticationRequestDto request) {
        log.info("[AUTH CONTROLLER] Authentication request for Ethereum address ({})", request.getEthereumAddress());
        try {
            String ethereumAddress = request.getEthereumAddress();
            String message = request.getMessage();
            String signature = request.getSignature();

            // Find or create user
            UserDto user = userService.findByEthereumAddress(ethereumAddress);
            String userNonce = user.getNonce();

            // Check if message contains user nonce && pubkey && signature is correct
            AuthParamsValidator.validateMessageEthereumAddress(message, ethereumAddress);
            AuthParamsValidator.validateMessageNonce(message, userNonce);
            AuthParamsValidator.validateMessageSignature(ethereumAddress, message, signature);

            // Authenticate user and update nonce
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(ethereumAddress, ""));
            String token = jwtTokenProvider.generateToken(ethereumAddress, user.getRoles());
            userService.updateNonceByEthereumAddress(user.getEthereumAddress());

            log.info("[AUTH CONTROLLER] Successful authentication for Ethereum address ({})", request.getEthereumAddress());
            return new AuthenticationResponseDto(token, ethereumAddress);
        } catch (AuthenticationException | AccessDeniedException e) {
            throw new BadCredentialsException("Bad signature"); // TODO: 02.10.2021 Create custom exception
        }
    }

    @Operation(description = "Get nonce")
    @GetMapping(value = "/nonce", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetNonceResponseDto nonce(@Valid @EthereumAddress @RequestParam("ethereumAddress") String ethereumAddress) {
        log.info("[AUTH CONTROLLER] Nonce request for Ethereum address ({})", ethereumAddress);
        UserDto user = userService.findByEthereumAddress(ethereumAddress);
        return new GetNonceResponseDto(user.getEthereumAddress(), user.getNonce());
    }
}
