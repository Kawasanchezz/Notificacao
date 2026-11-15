package com.taskscheduler.user;

import com.taskscheduler.user.UserDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "Profile")
public class UserController {

    private final UserService service;

    public UserController(UserService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Get my profile, including addresses and phones")
    public ProfileResponse profile(@AuthenticationPrincipal Long userId) { return service.profile(userId); }

    @PutMapping
    @Operation(summary = "Update my profile")
    public ProfileResponse update(@AuthenticationPrincipal Long userId, @Valid @RequestBody UpdateProfileRequest req) {
        return service.updateProfile(userId, req);
    }

    @GetMapping("/addresses")
    @Operation(summary = "List my addresses")
    public List<AddressResponse> addresses(@AuthenticationPrincipal Long userId) { return service.addresses(userId); }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an address", description = "Send CEP + number; street, neighborhood, city and state are filled from ViaCEP.")
    public AddressResponse addAddress(@AuthenticationPrincipal Long userId, @Valid @RequestBody AddressRequest req) {
        return service.addAddress(userId, req);
    }

    @PutMapping("/addresses/{id}")
    @Operation(summary = "Replace an address (CEP is looked up again)")
    public AddressResponse updateAddress(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                         @Valid @RequestBody AddressRequest req) {
        return service.updateAddress(userId, id, req);
    }

    @DeleteMapping("/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an address")
    public void deleteAddress(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        service.deleteAddress(userId, id);
    }

    @GetMapping("/phones")
    @Operation(summary = "List my phone numbers")
    public List<PhoneResponse> phones(@AuthenticationPrincipal Long userId) { return service.phones(userId); }

    @PostMapping("/phones")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a phone number")
    public PhoneResponse addPhone(@AuthenticationPrincipal Long userId, @Valid @RequestBody PhoneRequest req) {
        return service.addPhone(userId, req);
    }

    @PutMapping("/phones/{id}")
    @Operation(summary = "Replace a phone number")
    public PhoneResponse updatePhone(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                     @Valid @RequestBody PhoneRequest req) {
        return service.updatePhone(userId, id, req);
    }

    @DeleteMapping("/phones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a phone number")
    public void deletePhone(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        service.deletePhone(userId, id);
    }
}
