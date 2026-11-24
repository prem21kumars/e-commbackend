package com.ecommerce.modules.customer.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.modules.auth.entity.User;
import com.ecommerce.modules.auth.repository.UserRepository;
import com.ecommerce.modules.customer.dto.*;
import com.ecommerce.modules.customer.entity.CustomerAddress;
import com.ecommerce.modules.customer.entity.CustomerProfile;
import com.ecommerce.modules.customer.repository.CustomerAddressRepository;
import com.ecommerce.modules.customer.repository.CustomerProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final UserRepository userRepository;
    private final CustomerProfileRepository profileRepository;
    private final CustomerAddressRepository addressRepository;

    @Transactional(readOnly = true)
    public CustomerProfileDto getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        CustomerProfile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> CustomerProfile.builder().user(user).build());

        List<CustomerAddressDto> addresses = addressRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapAddressToDto)
                .collect(Collectors.toList());

        return mapProfileToDto(user, profile, addresses);
    }

    @Transactional
    public CustomerProfileDto updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            user.setPhoneNumber(request.getPhoneNumber().trim());
        }
        userRepository.save(user);

        CustomerProfile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> CustomerProfile.builder().user(user).build());

        if (request.getDateOfBirth() != null) {
            profile.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getGender() != null) {
            profile.setGender(request.getGender());
        }
        if (request.getAvatarUrl() != null) {
            profile.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getNewsletterSubscribed() != null) {
            profile.setNewsletterSubscribed(request.getNewsletterSubscribed());
        }
        profileRepository.save(profile);

        List<CustomerAddressDto> addresses = addressRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapAddressToDto)
                .collect(Collectors.toList());

        return mapProfileToDto(user, profile, addresses);
    }

    @Transactional(readOnly = true)
    public List<CustomerAddressDto> getAddresses(Long userId) {
        return addressRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapAddressToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public CustomerAddressDto createAddress(Long userId, CreateAddressRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (request.isDefaultShipping()) {
            addressRepository.resetDefaultShipping(userId);
        }
        if (request.isDefaultBilling()) {
            addressRepository.resetDefaultBilling(userId);
        }

        // If this is the user's first address, set as default shipping automatically
        boolean isFirst = addressRepository.findByUserIdOrderByCreatedAtDesc(userId).isEmpty();
        boolean setAsDefault = request.isDefaultShipping() || isFirst;

        CustomerAddress address = CustomerAddress.builder()
                .user(user)
                .recipientName(request.getRecipientName().trim())
                .phoneNumber(request.getPhoneNumber().trim())
                .addressLine1(request.getAddressLine1().trim())
                .addressLine2(request.getAddressLine2() != null ? request.getAddressLine2().trim() : null)
                .landmark(request.getLandmark() != null ? request.getLandmark().trim() : null)
                .city(request.getCity().trim())
                .state(request.getState().trim())
                .postalCode(request.getPostalCode().trim())
                .country(request.getCountry().trim())
                .addressType(request.getAddressType())
                .defaultShipping(setAsDefault)
                .defaultBilling(request.isDefaultBilling() || isFirst)
                .build();

        CustomerAddress saved = addressRepository.save(address);
        return mapAddressToDto(saved);
    }

    @Transactional
    public CustomerAddressDto updateAddress(Long userId, Long addressId, UpdateAddressRequest request) {
        CustomerAddress address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerAddress", "id", addressId));

        if (request.isDefaultShipping() && !address.isDefaultShipping()) {
            addressRepository.resetDefaultShipping(userId);
        }
        if (request.isDefaultBilling() && !address.isDefaultBilling()) {
            addressRepository.resetDefaultBilling(userId);
        }

        address.setRecipientName(request.getRecipientName().trim());
        address.setPhoneNumber(request.getPhoneNumber().trim());
        address.setAddressLine1(request.getAddressLine1().trim());
        address.setAddressLine2(request.getAddressLine2() != null ? request.getAddressLine2().trim() : null);
        address.setLandmark(request.getLandmark() != null ? request.getLandmark().trim() : null);
        address.setCity(request.getCity().trim());
        address.setState(request.getState().trim());
        address.setPostalCode(request.getPostalCode().trim());
        address.setCountry(request.getCountry().trim());
        address.setAddressType(request.getAddressType());
        address.setDefaultShipping(request.isDefaultShipping());
        address.setDefaultBilling(request.isDefaultBilling());

        CustomerAddress updated = addressRepository.save(address);
        return mapAddressToDto(updated);
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        CustomerAddress address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerAddress", "id", addressId));
        addressRepository.delete(address);
    }

    @Transactional
    public CustomerAddressDto setDefaultShipping(Long userId, Long addressId) {
        CustomerAddress address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerAddress", "id", addressId));

        addressRepository.resetDefaultShipping(userId);
        address.setDefaultShipping(true);
        CustomerAddress updated = addressRepository.save(address);
        return mapAddressToDto(updated);
    }

    private CustomerProfileDto mapProfileToDto(User user, CustomerProfile profile, List<CustomerAddressDto> addresses) {
        return CustomerProfileDto.builder()
                .id(profile.getId())
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .dateOfBirth(profile.getDateOfBirth())
                .gender(profile.getGender())
                .avatarUrl(profile.getAvatarUrl())
                .loyaltyPoints(profile.getLoyaltyPoints())
                .newsletterSubscribed(profile.isNewsletterSubscribed())
                .addresses(addresses)
                .build();
    }

    private CustomerAddressDto mapAddressToDto(CustomerAddress addr) {
        return CustomerAddressDto.builder()
                .id(addr.getId())
                .recipientName(addr.getRecipientName())
                .phoneNumber(addr.getPhoneNumber())
                .addressLine1(addr.getAddressLine1())
                .addressLine2(addr.getAddressLine2())
                .landmark(addr.getLandmark())
                .city(addr.getCity())
                .state(addr.getState())
                .postalCode(addr.getPostalCode())
                .country(addr.getCountry())
                .addressType(addr.getAddressType())
                .defaultShipping(addr.isDefaultShipping())
                .defaultBilling(addr.isDefaultBilling())
                .build();
    }
}

