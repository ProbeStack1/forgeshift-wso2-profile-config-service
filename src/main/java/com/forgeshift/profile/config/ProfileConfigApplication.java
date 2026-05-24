package com.forgeshift.profile.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ConfigurationPropertiesScan("com.forgeshift.profile.config.config")
@EnableAsync
public class ProfileConfigApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProfileConfigApplication.class, args);
    }
}
