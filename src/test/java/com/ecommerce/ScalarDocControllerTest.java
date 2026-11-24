package com.ecommerce;

import com.ecommerce.common.web.ScalarDocController;
import com.ecommerce.security.jwt.JwtAuthenticationFilter;
import com.ecommerce.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScalarDocController.class)
@AutoConfigureMockMvc(addFilters = false)
class ScalarDocControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("Should return Scalar UI HTML for /scalar")
    void testScalarUi() throws Exception {
        mockMvc.perform(get("/scalar"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("@scalar/api-reference")));
    }

    @Test
    @DisplayName("Should return Scalar UI HTML for /scalar/")
    void testScalarTrailingSlash() throws Exception {
        mockMvc.perform(get("/scalar/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("@scalar/api-reference")));
    }
}
