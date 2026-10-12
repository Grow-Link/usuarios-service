package com.growlink.adapter.web.dto;

import com.growlink.domain.Semilla;

import java.time.Instant;

public record SemillaResponse(Long id, String categoria, Long roadmapId, String titulo, Instant obtenidaEn) {
    public static SemillaResponse from(Semilla s) {
        return new SemillaResponse(s.getId(), s.getCategoria(), s.getRoadmapId(), s.getTitulo(), s.getObtenidaEn());
    }
}
