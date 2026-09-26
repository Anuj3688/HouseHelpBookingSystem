package com.househelper.resources;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Tag(name = "API documentation")
public class HomeResource {

    @GetMapping("/")
    @Operation(summary = "Open API documentation", description = "Redirects to Swagger UI.")
    public String redirectToApiDocs() {
        return "redirect:/swagger-ui/index.html";
    }
}
