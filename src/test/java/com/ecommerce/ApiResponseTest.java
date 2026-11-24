package com.ecommerce;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.common.response.ErrorResponse;
import com.ecommerce.common.response.FieldErrorDetail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Common Response Envelope Tests")
class ApiResponseTest {

    @Test
    @DisplayName("Should create standard success response with payload")
    void testSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success("Test payload");

        assertTrue(response.isSuccess());
        assertEquals("Operation completed successfully", response.getMessage());
        assertEquals("Test payload", response.getData());
        assertNotNull(response.getTimestamp());
    }

    @Test
    @DisplayName("Should create structured error response")
    void testErrorResponse() {
        FieldErrorDetail fieldError = FieldErrorDetail.builder()
                .field("email")
                .rejectedValue("invalid-email")
                .message("Must be a valid email format")
                .build();

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(400)
                .errorCode("VALIDATION_FAILED")
                .message("Input validation error")
                .path("/api/v1/auth/register")
                .correlationId("corr-12345")
                .fieldErrors(List.of(fieldError))
                .build();

        assertFalse(errorResponse.isSuccess());
        assertEquals(400, errorResponse.getStatus());
        assertEquals("VALIDATION_FAILED", errorResponse.getErrorCode());
        assertEquals("/api/v1/auth/register", errorResponse.getPath());
        assertEquals("corr-12345", errorResponse.getCorrelationId());
        assertEquals(1, errorResponse.getFieldErrors().size());
        assertEquals("email", errorResponse.getFieldErrors().getFirst().getField());
    }
}

