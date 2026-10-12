package com.growlink.domain;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

// los 3 checkpoints de HU-04 viven aqui
// estaCompleto() es la unica regla que dice si el camino ya se cerro
@Entity
@Table(name = "perfil")
public class Perfil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long usuarioId;

    @Column(length = 500)
    private String metas;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "perfil_interes", joinColumns = @JoinColumn(name = "perfil_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "interes")
    private Set<Interes> intereses = new HashSet<>();

    @Enumerated(EnumType.STRING)
    private Nivel nivel;

    // el tiempo que la persona puede dedicarle (opcional, los dos o ninguno): lo usa el roadmap para no pasarse de horas
    private Integer horasPorSemana;

    private Integer semanas;

    // HU-22: sube de a uno con un UPDATE atomico (ver PerfilRepository), nunca desde aqui
    // es Integer con default en la tabla para que no se rompan los perfiles que ya existian
    @Column(columnDefinition = "integer default 0")
    private Integer triviasGanadas = 0;

    // jardin: sube con cada trivia ganada (misma operacion atomica que triviasGanadas) y baja al regar una
    // planta (ver PerfilRepository); Integer con default por la misma razon que triviasGanadas
    @Column(columnDefinition = "integer default 0")
    private Integer gotas = 0;

    protected Perfil() {
        // JPA
    }

    public Perfil(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public void actualizarMetas(String metas) {
        this.metas = metas;
    }

    public void actualizarIntereses(Set<Interes> intereses) {
        this.intereses = new HashSet<>(intereses);
    }

    public void actualizarNivel(Nivel nivel) {
        this.nivel = nivel;
    }

    public void actualizarTiempo(Integer horasPorSemana, Integer semanas) {
        this.horasPorSemana = horasPorSemana;
        this.semanas = semanas;
    }

    public boolean estaCompleto() {
        return metas != null && !metas.isBlank() && !intereses.isEmpty() && nivel != null;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getMetas() {
        return metas;
    }

    public Set<Interes> getIntereses() {
        return intereses;
    }

    public Nivel getNivel() {
        return nivel;
    }

    public Integer getHorasPorSemana() {
        return horasPorSemana;
    }

    public Integer getSemanas() {
        return semanas;
    }

    public int getTriviasGanadas() {
        return triviasGanadas == null ? 0 : triviasGanadas;
    }

    public int getGotas() {
        return gotas == null ? 0 : gotas;
    }

    // solo para el DataSeeder: deja al usuario de demostracion con gotas sin jugar nada
    public void sumarGotas(int cantidad) {
        this.gotas = getGotas() + cantidad;
    }
}
