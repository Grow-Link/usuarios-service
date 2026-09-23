package com.growlink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// quitamos esto porque no manejamos password real, todo el login es por token
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class GrowlinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrowlinkApplication.class, args);
    }
}
