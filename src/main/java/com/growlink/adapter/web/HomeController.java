package com.growlink.adapter.web;

import com.growlink.adapter.web.dto.HomeEstadoResponse;
import com.growlink.application.HomeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
@Tag(name = "Home", description = "Estado del home y secciones visibles según el rol")
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping("/estado")
    public HomeEstadoResponse estado(Authentication authentication) {
        Long usuarioId = Long.valueOf(authentication.getName());
        return HomeEstadoResponse.from(homeService.obtenerResumen(usuarioId));
    }
}
