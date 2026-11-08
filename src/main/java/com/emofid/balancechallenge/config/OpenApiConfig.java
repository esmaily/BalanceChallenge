package com.emofid.balancechallenge.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI balanceOpenApi() {
		return new OpenAPI().info(new Info()
				.title("Balance Challenge API")
				.version("v1")
				.description("Concurrent and idempotent account balance operations"));
	}
}
