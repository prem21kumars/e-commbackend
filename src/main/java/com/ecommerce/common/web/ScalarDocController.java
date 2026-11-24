package com.ecommerce.common.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Controller serving the Scalar API Reference and Interactive Development Console.
 * Available at: /scalar and /scalar.html
 */
@Controller
public class ScalarDocController {

    private volatile String scalarHtml;

    @GetMapping(value = {"/scalar", "/scalar/", "/scalar.html"}, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> getScalarUi() throws IOException {
        if (scalarHtml == null) {
            synchronized (this) {
                if (scalarHtml == null) {
                    ClassPathResource resource = new ClassPathResource("static/scalar/index.html");
                    scalarHtml = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                }
            }
        }
        return ResponseEntity.ok(scalarHtml);
    }
}
