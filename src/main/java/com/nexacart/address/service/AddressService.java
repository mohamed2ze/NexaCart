package com.nexacart.address.service;

import com.nexacart.address.dto.AddressRequest;
import com.nexacart.address.dto.AddressResponse;
import com.nexacart.address.entity.Address;
import com.nexacart.address.repository.AddressRepository;
import com.nexacart.common.exception.ApiException;
import com.nexacart.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public AddressService(AddressRepository addressRepository, UserRepository userRepository) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(UUID userId) {
        return addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId).stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AddressResponse get(UUID userId, UUID addressId) {
        return AddressResponse.from(getOwned(userId, addressId));
    }

    @Transactional
    public AddressResponse create(UUID userId, AddressRequest request) {
        boolean firstAddress = addressRepository.countByUserId(userId) == 0;
        boolean makeDefault = request.defaultAddress() == null ? firstAddress : request.defaultAddress();
        if (makeDefault) {
            addressRepository.clearDefaultForUser(userId);
        }
        Address address = new Address();
        address.setUser(userRepository.getReferenceById(userId));
        apply(address, request);
        address.setDefaultAddress(makeDefault);
        Address saved = addressRepository.saveAndFlush(address);
        ensureDefault(userId);
        return AddressResponse.from(saved);
    }

    @Transactional
    public AddressResponse update(UUID userId, UUID addressId, AddressRequest request) {
        Address address = getOwned(userId, addressId);
        boolean wasDefault = address.isDefaultAddress();
        apply(address, request);
        if (request.defaultAddress() != null) {
            if (request.defaultAddress()) {
                addressRepository.clearDefaultForUser(userId);
                address.setDefaultAddress(true);
            } else if (wasDefault) {
                address.setDefaultAddress(false);
            }
        }
        Address saved = addressRepository.saveAndFlush(address);
        ensureDefault(userId);
        return AddressResponse.from(saved);
    }

    @Transactional
    public void delete(UUID userId, UUID addressId) {
        Address address = getOwned(userId, addressId);
        addressRepository.delete(address);
        addressRepository.flush();
        ensureDefault(userId);
    }

    @Transactional
    public AddressResponse setDefault(UUID userId, UUID addressId) {
        Address address = getOwned(userId, addressId);
        if (!address.isDefaultAddress()) {
            addressRepository.clearDefaultForUser(userId);
            address.setDefaultAddress(true);
            addressRepository.saveAndFlush(address);
        }
        return AddressResponse.from(address);
    }

    @Transactional(readOnly = true)
    public Address getOwned(UUID userId, UUID addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> ApiException.notFound("ADDRESS_NOT_FOUND", "Address not found"));
    }

    private void apply(Address address, AddressRequest request) {
        address.setRecipientName(request.recipientName().trim());
        address.setLine1(request.line1().trim());
        address.setLine2(trimToNull(request.line2()));
        address.setCity(request.city().trim());
        address.setState(trimToNull(request.state()));
        address.setPostalCode(trimToNull(request.postalCode()));
        address.setCountry(request.country().trim());
        address.setPhone(trimToNull(request.phone()));
    }

    private void ensureDefault(UUID userId) {
        if (addressRepository.existsByUserIdAndDefaultAddressTrue(userId)) {
            return;
        }
        List<Address> addresses = addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId);
        if (!addresses.isEmpty()) {
            Address address = addresses.getFirst();
            address.setDefaultAddress(true);
            addressRepository.save(address);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
