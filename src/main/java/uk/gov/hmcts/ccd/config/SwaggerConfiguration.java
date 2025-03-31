package uk.gov.hmcts.ccd.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class SwaggerConfiguration {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
              .info(new Info().title("CCD Message Publisher API")
              .description("CCD Message Publisher API")
              .version("v0.0.1"));
    }
}
