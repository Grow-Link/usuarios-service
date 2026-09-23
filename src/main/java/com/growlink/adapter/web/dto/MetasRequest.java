package com.growlink.adapter.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MetasRequest(@NotBlank @Size(max = 500) String metas) {
}
