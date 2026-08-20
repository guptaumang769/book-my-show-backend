package com.umang.bookmyshow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bookMyShowOpenApi() {
        return new OpenAPI().info(new Info()
                .title("BookMyShow API")
                .description("Ticket-booking backend: catalog, seat locking, bookings, payments.")
                .version("v1")
                .license(new License().name("MIT")));
    }
}
