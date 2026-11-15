package com.taskscheduler.user;

import com.taskscheduler.exception.Exceptions.NotFoundException;
import com.taskscheduler.user.UserDtos.*;
import com.taskscheduler.viacep.ViaCepClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserService {

    private final UserRepository users;
    private final ViaCepClient viaCep;

    public UserService(UserRepository users, ViaCepClient viaCep) {
        this.users = users;
        this.viaCep = viaCep;
    }

    @Transactional(readOnly = true)
    public ProfileResponse profile(Long userId) { return ProfileResponse.from(find(userId)); }

    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = find(userId);
        user.setName(req.name().trim());
        return ProfileResponse.from(user);
    }

    // ---- addresses ----

    @Transactional(readOnly = true)
    public List<AddressResponse> addresses(Long userId) {
        return find(userId).getAddresses().stream().map(AddressResponse::from).toList();
    }

    public AddressResponse addAddress(Long userId, AddressRequest req) {
        User user = find(userId);
        Address address = new Address();
        address.setUser(user);
        apply(address, req);
        user.getAddresses().add(address);
        users.flush();
        return AddressResponse.from(address);
    }

    public AddressResponse updateAddress(Long userId, Long addressId, AddressRequest req) {
        Address address = findAddress(find(userId), addressId);
        apply(address, req);
        return AddressResponse.from(address);
    }

    public void deleteAddress(Long userId, Long addressId) {
        User user = find(userId);
        user.getAddresses().remove(findAddress(user, addressId));
    }

    /** The user only supplies CEP + number; street/city/etc. come from ViaCEP. */
    private void apply(Address address, AddressRequest req) {
        var found = viaCep.lookup(req.cep());
        address.setCep(found.cep());
        address.setStreet(found.logradouro());
        address.setNeighborhood(found.bairro());
        address.setCity(found.localidade());
        address.setState(found.uf());
        address.setNumber(req.number().trim());
        address.setComplement(req.complement());
    }

    // ---- phones ----

    @Transactional(readOnly = true)
    public List<PhoneResponse> phones(Long userId) {
        return find(userId).getPhones().stream().map(PhoneResponse::from).toList();
    }

    public PhoneResponse addPhone(Long userId, PhoneRequest req) {
        User user = find(userId);
        Phone phone = new Phone();
        phone.setUser(user);
        phone.setNumber(req.number());
        phone.setType(req.type());
        user.getPhones().add(phone);
        users.flush();
        return PhoneResponse.from(phone);
    }

    public PhoneResponse updatePhone(Long userId, Long phoneId, PhoneRequest req) {
        Phone phone = findPhone(find(userId), phoneId);
        phone.setNumber(req.number());
        phone.setType(req.type());
        return PhoneResponse.from(phone);
    }

    public void deletePhone(Long userId, Long phoneId) {
        User user = find(userId);
        user.getPhones().remove(findPhone(user, phoneId));
    }

    // ---- helpers (ownership is guaranteed because we only look inside the caller's own collections) ----

    private User find(Long userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private Address findAddress(User user, Long id) {
        return user.getAddresses().stream().filter(a -> a.getId().equals(id)).findFirst()
                .orElseThrow(() -> new NotFoundException("Address not found"));
    }

    private Phone findPhone(User user, Long id) {
        return user.getPhones().stream().filter(p -> p.getId().equals(id)).findFirst()
                .orElseThrow(() -> new NotFoundException("Phone not found"));
    }
}
