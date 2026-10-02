package com.growlink.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    @Column(nullable = true)
    private String cargo;

    protected Usuario() {
        // JPA
    }

    public Usuario(String nombre, Rol rol, String cargo) {
        this.nombre = nombre;
        this.rol = rol;
        this.cargo = cargo;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public Rol getRol() {
        return rol;
    }

    public String getCargo() {
        return cargo;
    }
}
