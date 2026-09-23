package com.growlink.domain;

// son las secciones que puede mostrar el home, segun el rol
// PERFIL y TRIVIA las ve cualquiera, el resto depende del rol
public enum Seccion {
    TRIVIA,
    PERFIL,
    CURSOS,
    PREGUNTAS_TRIVIA,
    DAR_DE_BAJA_CURSOS,
    DASHBOARD_METRICAS
}
