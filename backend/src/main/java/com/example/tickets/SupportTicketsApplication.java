package com.example.tickets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SupportTicketsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportTicketsApplication.class, args);
    }
}
