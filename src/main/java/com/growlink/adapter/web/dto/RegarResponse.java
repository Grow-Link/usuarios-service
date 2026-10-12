package com.growlink.adapter.web.dto;

import com.growlink.application.JardinService.RegarResultado;

public record RegarResponse(int gotas, PlantaResponse planta) {
    public static RegarResponse from(RegarResultado r) {
        return new RegarResponse(r.gotas(), PlantaResponse.from(r.planta()));
    }
}
