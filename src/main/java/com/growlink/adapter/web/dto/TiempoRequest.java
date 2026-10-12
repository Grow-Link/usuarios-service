package com.growlink.adapter.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// los dos valores o ninguno: sin ellos se borra el limite de tiempo
public record TiempoRequest(@Min(1) @Max(60) Integer horasPorSemana, @Min(1) @Max(104) Integer semanas) {

    // si solo viene uno, es un 400 con mensaje (un error de la persona, no del servidor)
    @JsonIgnore
    @AssertTrue(message = "indica las horas por semana y el plazo, o ninguno de los dos")
    public boolean isCompleto() {
        return (horasPorSemana == null) == (semanas == null);
    }
}
