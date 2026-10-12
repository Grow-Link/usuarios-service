package com.growlink.adapter.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PlantarRequest(@NotNull @Min(0) @Max(5) Integer espacio) {
}
