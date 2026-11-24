package com.ecommerce.modules.customer.dto;

import com.ecommerce.modules.customer.entity.AddressType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAddressDto {
    private Long id;
    private String recipientName;
    private String phoneNumber;
    private String addressLine1;
    private String addressLine2;
    private String landmark;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private AddressType addressType;
    private boolean defaultShipping;
    private boolean defaultBilling;
}

