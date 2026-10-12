package com.growlink.adapter.web.dto;

import com.growlink.application.JardinService.JardinEstado;

import java.util.List;

public record JardinResponse(int gotas, int espacios, List<SemillaResponse> semillas, List<PlantaResponse> plantas) {
    public static JardinResponse from(JardinEstado estado) {
        return new JardinResponse(estado.gotas(), estado.espacios(),
                estado.semillas().stream().map(SemillaResponse::from).toList(),
                estado.plantas().stream().map(PlantaResponse::from).toList());
    }
}
