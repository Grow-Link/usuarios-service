package com.growlink.adapter.web.dto;

import com.growlink.domain.Etapa;
import com.growlink.domain.Semilla;

import java.time.Instant;

public record PlantaResponse(Long id, String categoria, Long roadmapId, Integer espacio, int riegos,
                              int riegosNecesarios, Etapa etapa, Instant plantadaEn, Instant florecidaEn) {
    public static PlantaResponse from(Semilla s) {
        return new PlantaResponse(s.getId(), s.getCategoria(), s.getRoadmapId(), s.getEspacio(), s.getRiegos(),
                3, s.getEtapa(), s.getPlantadaEn(), s.getFlorecidaEn());
    }
}
