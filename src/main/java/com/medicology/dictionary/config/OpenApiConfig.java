package com.medicology.dictionary.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Notification Service API",
        version = "v1",
        description = "OpenAPI docs for notification-service-medicology",
        contact = @Contact(name = "Medicology"),
        license = @License(name = "Proprietary")
    )
)
public class OpenApiConfig {
}
