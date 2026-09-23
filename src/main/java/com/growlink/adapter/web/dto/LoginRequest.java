package com.growlink.adapter.web.dto;

import jakarta.validation.constraints.NotNull;

public record LoginRequest(@NotNull Long usuarioId) {
}
