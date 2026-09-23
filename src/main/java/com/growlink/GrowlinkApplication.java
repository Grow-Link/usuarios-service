package com.growlink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// UserDetailsServiceAutoConfiguration excluida: la autenticacion es 100% por
// token propio (ver TokenService), no hay password real que validar (HU-01).
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class GrowlinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrowlinkApplication.class, args);
    }
}
