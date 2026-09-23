package com.growlink.adapter.web.dto;

import com.growlink.domain.Nivel;
import jakarta.validation.constraints.NotNull;

public record NivelRequest(@NotNull Nivel nivel) {
}
