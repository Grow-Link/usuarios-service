package com.growlink.domain;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Los 3 checkpoints de HU-04. estaCompleto() es la unica fuente de verdad de
 * "cuando se cierra el camino" - ni el frontend ni otro endpoint deberian
 * reimplementar esa regla por su cuenta.
 */
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
}
