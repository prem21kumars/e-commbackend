package com.ecommerce;

import com.ecommerce.common.web.ApiVersionCompatibilityFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiVersionCompatibilityFilterTest {

    @InjectMocks
    private ApiVersionCompatibilityFilter filter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private RequestDispatcher requestDispatcher;

    @Test
    @DisplayName("Should rewrite /api/products to /api/v1/products and forward")
    void shouldRewriteNonVersionedApiPath() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/products");
        when(request.getRequestDispatcher("/api/v1/products")).thenReturn(requestDispatcher);

        filter.doFilter(request, response, filterChain);

        verify(requestDispatcher).forward(request, response);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Should pass /api/v1/products through without rewriting")
    void shouldNotRewriteVersionedApiPath() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/products");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(request, never()).getRequestDispatcher(anyString());
    }

    @Test
    @DisplayName("Should pass non-api paths through without rewriting")
    void shouldNotRewriteNonApiPaths() throws Exception {
        when(request.getRequestURI()).thenReturn("/actuator/health");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(request, never()).getRequestDispatcher(anyString());
    }
}

