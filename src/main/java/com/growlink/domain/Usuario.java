package com.growlink.domain;

import jakarta.persistence.*;

import java.time.Instant;

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

    // El identificador que Microsoft le da a la persona (claim sub del token). Es la llave con la
    // que se reconoce a quien vuelve a entrar, y es unica: dos usuarios locales con el mismo sub
    // serian la misma persona partida en dos cuentas. Los usuarios del login quemado (el modo
    // legacy, ver DataSeeder) no tienen proveedor, por eso admite nulos; en Postgres y en H2 la
    // restriccion unica deja pasar varios nulos.
    @Column(name = "proveedor_sub", unique = true)
    private String proveedorSub;

    // Sale del token (claim email o preferred_username). Puede faltar: hay cuentas sin correo
    // publicado, y eso no impide entrar. NO es unico a proposito: la identidad es el sub, y una
    // restriccion unica aqui dejaria fuera a quien entra con una cuenta nueva y el mismo correo.
    @Column(nullable = true)
    private String correo;

    // false mientras la persona no haya escogido entre USUARIO y PUBLICADOR (el frontend usa esto
    // para mostrarle la pantalla de escoger rol). Es Boolean y no boolean porque la columna se
    // agrega a una tabla que ya tiene filas: con ddl-auto=update, una columna nueva "not null" sin
    // valor por defecto no se puede agregar sobre datos existentes. Un nulo significa justo lo que
    // tiene que significar para esas filas viejas: todavia no escogio.
    @Column(name = "rol_elegido")
    private Boolean rolElegido;

    // Cuando se le vio actividad por ultima vez (con un margen: se refresca como mucho cada pocos minutos), para
    // saber cuando empieza una sesion nueva en el modo oidc (ver RegistroDeSesiones). Nulo = nunca se le vio
    // actividad con Microsoft.
    //
    // insertable/updatable = false A PROPOSITO: esta columna solo la escriben los UPDATE condicionales de
    // UsuarioRepository, nunca un save() de la entidad. Hibernate escribe todas las columnas al guardar, asi que si
    // la entidad pudiera escribirla, cualquier save() (elegir rol, refrescar el correo) le pondria encima el valor
    // viejo que leyo al principio de su peticion, y la siguiente peticion veria una inactividad que no hubo.
    @Column(name = "ultima_actividad", insertable = false, updatable = false)
    private Instant ultimaActividad;

    protected Usuario() {
        // JPA
    }

    public Usuario(String nombre, Rol rol, String cargo) {
        this.nombre = nombre;
        this.rol = rol;
        this.cargo = cargo;
    }

    // El usuario local de quien entra por primera vez con Microsoft. Arranca como USUARIO sin rol
    // elegido, salvo que el correo este en la lista de administradores (ver IngresoService, que es
    // quien decide eso: la entidad no conoce la configuracion).
    public static Usuario delProveedor(String proveedorSub, String correo, String nombre, Rol rol,
                                       boolean rolElegido) {
        Usuario usuario = new Usuario(nombre, rol, null);
        usuario.proveedorSub = proveedorSub;
        usuario.correo = correo;
        usuario.rolElegido = rolElegido;
        return usuario;
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

    public String getProveedorSub() {
        return proveedorSub;
    }

    public String getCorreo() {
        return correo;
    }

    public boolean isRolElegido() {
        return Boolean.TRUE.equals(rolElegido);
    }

    public Instant getUltimaActividad() {
        return ultimaActividad;
    }

    /** El rol que la persona escogio (USUARIO o PUBLICADOR): queda marcado como elegido. */
    public void escogerRol(Rol rol) {
        this.rol = rol;
        this.rolElegido = true;
    }

    /**
     * ADMIN no se escoge: lo pone el servicio cuando el correo esta en AUTH_ADMIN_EMAILS, y queda
     * con el rol ya elegido (a un administrador no se le pregunta que quiere ser).
     */
    public void marcarComoAdmin() {
        this.rol = Rol.ADMIN;
        this.rolElegido = true;
    }

    /**
     * Refresca lo que es del proveedor (el nombre y el correo los manda el token, no esta
     * aplicacion). Devuelve true si algo cambio, para no escribir en la base en cada peticion.
     */
    public boolean refrescarDatosDelProveedor(String correo, String nombre) {
        boolean cambio = false;
        if (correo != null && !correo.equals(this.correo)) {
            this.correo = correo;
            cambio = true;
        }
        if (nombre != null && !nombre.equals(this.nombre)) {
            this.nombre = nombre;
            cambio = true;
        }
        return cambio;
    }
}
