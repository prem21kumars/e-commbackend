package com.ecommerce.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter providing seamless backward-compatible URL aliasing for frontend clients
 * requesting '/api/...' instead of canonical '/api/v1/...'.
 * Executed at HIGHEST_PRECEDENCE before Spring Security and Spring MVC Dispatcher.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiVersionCompatibilityFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (uri.startsWith("/api/") && !uri.startsWith("/api/v1/")) {
            String rewrittenUri = uri.replaceFirst("^/api/", "/api/v1/");
            request.getRequestDispatcher(rewrittenUri).forward(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }
}

