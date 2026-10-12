package com.growlink.domain;

import jakarta.persistence.*;

import java.time.Instant;

// nace como semilla (espacio null) cuando se completa un roadmap, con la categoria principal de ese roadmap.
// al plantarla en un espacio libre (0 a 5) se vuelve planta, sin cambiar de fila ni de id.
// Las acciones de verdad (plantar/regar) se hacen con los UPDATE atomicos de SemillaRepository, nunca
// leyendo esta entidad y volviendo a guardarla: los mutadores de aqui son solo para el seed de demostracion.
@Entity
@Table(name = "semilla", uniqueConstraints = {
        @UniqueConstraint(name = "uk_semilla_usuario_roadmap", columnNames = {"usuario_id", "roadmap_id"}),
        @UniqueConstraint(name = "uk_semilla_usuario_espacio", columnNames = {"usuario_id", "espacio"})
})
public class Semilla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "roadmap_id", nullable = false)
    private Long roadmapId;

    @Column(nullable = false)
    private String categoria;

    private String titulo;

    @Column(nullable = false)
    private Instant obtenidaEn;

    // null mientras es semilla; al plantarla queda fijo entre 0 y 5 (unico por usuario)
    private Integer espacio;

    @Column(nullable = false)
    private int riegos = 0;

    private Instant plantadaEn;

    private Instant florecidaEn;

    protected Semilla() {
        // JPA
    }

    public Semilla(Long usuarioId, Long roadmapId, String categoria, String titulo) {
        this.usuarioId = usuarioId;
        this.roadmapId = roadmapId;
        this.categoria = categoria;
        this.titulo = titulo;
        this.obtenidaEn = Instant.now();
    }

    // solo para el DataSeeder: deja al usuario de demostracion con una planta ya plantada sin jugar nada
    public void plantar(int espacio) {
        this.espacio = espacio;
        this.plantadaEn = Instant.now();
    }

    // solo para el DataSeeder, ver nota de arriba
    public void regar() {
        this.riegos++;
        if (this.riegos >= 3) {
            this.florecidaEn = Instant.now();
        }
    }

    public Etapa getEtapa() {
        return Etapa.deRiegos(riegos);
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getRoadmapId() {
        return roadmapId;
    }

    public String getCategoria() {
        return categoria;
    }

    public String getTitulo() {
        return titulo;
    }

    public Instant getObtenidaEn() {
        return obtenidaEn;
    }

    public Integer getEspacio() {
        return espacio;
    }

    public int getRiegos() {
        return riegos;
    }

    public Instant getPlantadaEn() {
        return plantadaEn;
    }

    public Instant getFlorecidaEn() {
        return florecidaEn;
    }
}
