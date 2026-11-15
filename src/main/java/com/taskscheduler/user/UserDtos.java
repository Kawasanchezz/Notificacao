package com.taskscheduler.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class UserDtos {
    private UserDtos() {}

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 120) String name) {}

    public record AddressRequest(
            @NotBlank @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP must have 8 digits (e.g. 01310-100)") String cep,
            @NotBlank @Size(max = 20) String number,
            @Size(max = 120) String complement) {}

    public record AddressResponse(Long id, String cep, String street, String neighborhood, String city,
                                  String state, String number, String complement) {
        static AddressResponse from(Address a) {
            return new AddressResponse(a.getId(), a.getCep(), a.getStreet(), a.getNeighborhood(), a.getCity(),
                    a.getState(), a.getNumber(), a.getComplement());
        }
    }

    public record PhoneRequest(
            @NotBlank @Pattern(regexp = "^\\+?\\d{10,15}$", message = "Phone must contain 10-15 digits, optional leading +") String number,
            @NotNull Phone.Type type) {}

    public record PhoneResponse(Long id, String number, Phone.Type type) {
        static PhoneResponse from(Phone p) { return new PhoneResponse(p.getId(), p.getNumber(), p.getType()); }
    }

    public record ProfileResponse(Long id, String name, String email,
                                  List<AddressResponse> addresses, List<PhoneResponse> phones) {
        static ProfileResponse from(User u) {
            return new ProfileResponse(u.getId(), u.getName(), u.getEmail(),
                    u.getAddresses().stream().map(AddressResponse::from).toList(),
                    u.getPhones().stream().map(PhoneResponse::from).toList());
        }
    }
}
