package com.growlink.adapter.web.dto;

import com.growlink.domain.Interes;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record InteresesRequest(@NotEmpty Set<Interes> intereses) {
}
