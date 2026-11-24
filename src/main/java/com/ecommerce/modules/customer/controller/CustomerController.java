package com.ecommerce.modules.customer.controller;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.modules.customer.dto.*;
import com.ecommerce.modules.customer.service.CustomerService;
import com.ecommerce.security.principal.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customer Management", description = "Endpoints for managing customer profile and shipping addresses")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get Customer Profile", description = "Retrieves profile and address list of the currently authenticated customer")
    public ResponseEntity<ApiResponse<CustomerProfileDto>> getMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
        CustomerProfileDto profile = customerService.getProfile(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update Customer Profile", description = "Updates personal contact information and preferences")
    public ResponseEntity<ApiResponse<CustomerProfileDto>> updateMyProfile(@AuthenticationPrincipal UserPrincipal principal,
                                                                           @Valid @RequestBody UpdateProfileRequest request) {
        CustomerProfileDto updated = customerService.updateProfile(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", updated));
    }

    @GetMapping("/me/addresses")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get Customer Addresses", description = "Retrieves all saved delivery and billing addresses for the customer")
    public ResponseEntity<ApiResponse<List<CustomerAddressDto>>> getMyAddresses(@AuthenticationPrincipal UserPrincipal principal) {
        List<CustomerAddressDto> addresses = customerService.getAddresses(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(addresses));
    }

    @PostMapping("/me/addresses")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create Address", description = "Adds a new delivery address")
    public ResponseEntity<ApiResponse<CustomerAddressDto>> createAddress(@AuthenticationPrincipal UserPrincipal principal,
                                                                         @Valid @RequestBody CreateAddressRequest request) {
        CustomerAddressDto address = customerService.createAddress(principal.getId(), request);
        return new ResponseEntity<>(ApiResponse.success("Address created successfully", address), HttpStatus.CREATED);
    }

    @PutMapping("/me/addresses/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update Address", description = "Updates an existing customer delivery address")
    public ResponseEntity<ApiResponse<CustomerAddressDto>> updateAddress(@AuthenticationPrincipal UserPrincipal principal,
                                                                         @PathVariable Long id,
                                                                         @Valid @RequestBody UpdateAddressRequest request) {
        CustomerAddressDto updated = customerService.updateAddress(principal.getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Address updated successfully", updated));
    }

    @DeleteMapping("/me/addresses/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete Address", description = "Deletes a customer address by id")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable Long id) {
        customerService.deleteAddress(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.messageOnly("Address deleted successfully"));
    }

    @PatchMapping("/me/addresses/{id}/default")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Set Default Shipping Address", description = "Marks an address as primary default shipping address")
    public ResponseEntity<ApiResponse<CustomerAddressDto>> setDefaultShipping(@AuthenticationPrincipal UserPrincipal principal,
                                                                              @PathVariable Long id) {
        CustomerAddressDto updated = customerService.setDefaultShipping(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Default shipping address updated", updated));
    }
}

