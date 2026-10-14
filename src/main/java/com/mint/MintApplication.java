package com.mint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Mint Application.
 * Spring Boot starts the embedded web server and configures components automatically.
 */
@SpringBootApplication
public class MintApplication {

    public static void main(String[] args) {
        SpringApplication.run(MintApplication.class, args);
    }
}
