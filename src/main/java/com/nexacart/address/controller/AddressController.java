package com.nexacart.address.controller;

import com.nexacart.address.dto.AddressRequest;
import com.nexacart.address.dto.AddressResponse;
import com.nexacart.address.service.AddressService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/addresses")
@Tag(name = "Addresses")
@SecurityRequirement(name = "bearerAuth")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public List<AddressResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return addressService.list(userId(jwt));
    }

    @GetMapping("/{addressId}")
    public AddressResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID addressId) {
        return addressService.get(userId(jwt), addressId);
    }

    @PostMapping
    @ApiResponse(responseCode = "201", description = "Address created")
    public ResponseEntity<AddressResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddressRequest request
    ) {
        AddressResponse created = addressService.create(userId(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/addresses/" + created.id())).body(created);
    }

    @PutMapping("/{addressId}")
    public AddressResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID addressId,
            @Valid @RequestBody AddressRequest request
    ) {
        return addressService.update(userId(jwt), addressId, request);
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID addressId) {
        addressService.delete(userId(jwt), addressId);
    }

    @PatchMapping("/{addressId}/default")
    public AddressResponse setDefault(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID addressId) {
        return addressService.setDefault(userId(jwt), addressId);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
