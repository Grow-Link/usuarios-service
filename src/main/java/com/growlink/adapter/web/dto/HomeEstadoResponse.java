package com.growlink.adapter.web.dto;

import com.growlink.application.HomeService;
import com.growlink.domain.EstadoHome;
import com.growlink.domain.Seccion;

import java.util.Set;

public record HomeEstadoResponse(EstadoHome estado, Set<Seccion> secciones) {
    public static HomeEstadoResponse from(HomeService.ResumenHome resumen) {
        return new HomeEstadoResponse(resumen.estado(), resumen.secciones());
    }
}
