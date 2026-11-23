package com.familion.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FamilionBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(FamilionBffApplication.class, args);
    }
}
