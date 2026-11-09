package com.growlink.application;

import com.growlink.adapter.persistence.UsuarioRepository;
import com.growlink.adapter.security.OidcTokenVerificador;
import com.growlink.domain.Rol;
import com.growlink.domain.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

// Lo que pasa entre "el token de Microsoft es valido" y "esta es la persona de GrowLink":
//
//   primer ingreso    se crea el usuario local con el sub del token, su correo y su nombre. Rol
//                     USUARIO y rolElegido=false, para que el frontend le pregunte que quiere ser.
//   ingreso repetido  se busca por el sub y se devuelve el mismo usuario, nunca uno nuevo.
//   eleccion de rol   la persona puede moverse entre USUARIO y PUBLICADOR cuando quiera.
//   administradores   ADMIN no se escoge: lo da la lista de correos AUTH_ADMIN_EMAILS.
//
// Por que el sub y no el correo: el correo de una cuenta se puede cambiar, y el de una cuenta
// borrada se le puede dar a otra persona. El sub no cambia nunca, y es lo unico que el proveedor
// garantiza estable. El correo se guarda igual porque es lo que se le muestra a la persona y lo
// que se compara contra la lista de administradores.
@Service
public class IngresoService {

    private static final Logger log = LoggerFactory.getLogger(IngresoService.class);

    private final UsuarioRepository usuarioRepository;
    private final UsuarioLocalCreador creador;

    // Los correos que quedan como ADMIN, en minusculas para comparar sin distinguir mayusculas.
    // Esta en una variable de entorno y no en la base a proposito: quien es administrador es una
    // decision del despliegue, no un dato que se pueda cambiar desde la aplicacion (si se pudiera,
    // el primer ADMIN podria repartir el rol, y ADMIN es justo el rol que nadie debe poder darse).
    private final Set<String> correosDeAdministradores;

    public IngresoService(UsuarioRepository usuarioRepository, UsuarioLocalCreador creador,
                          @Value("${auth.admin-emails:}") String adminEmails) {
        this.usuarioRepository = usuarioRepository;
        this.creador = creador;
        this.correosDeAdministradores = Arrays.stream(adminEmails.split(","))
                .map(correo -> correo.trim().toLowerCase(Locale.ROOT))
                .filter(correo -> !correo.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (!correosDeAdministradores.isEmpty()) {
            log.info("AUTH_ADMIN_EMAILS trae {} correo(s) con rol ADMIN", correosDeAdministradores.size());
        }
    }

    /**
     * El usuario local de quien trae este token: el que ya existia, o uno nuevo si es su primer
     * ingreso. Lo llama OidcAuthenticationFilter en cada peticion, asi que el camino normal (la
     * persona ya existe y nada cambio) no escribe en la base.
     */
    @Transactional
    public Usuario ingresar(OidcTokenVerificador.Identidad identidad) {
        return usuarioRepository.findByProveedorSub(identidad.sub())
                .map(usuario -> actualizarSiHaceFalta(usuario, identidad))
                .orElseGet(() -> crearODevolverElQueSeAdelanto(identidad));
    }

    // Si dos peticiones de la misma persona llegan juntas en su primer ingreso, las dos intentan
    // crear el usuario y una choca con la restriccion unica de proveedor_sub: ese choque NO es un
    // error, solo significa que la otra ya lo creo, asi que se vuelve a leer. Es el mismo trato que
    // le da PerfilService al perfil que todavia no existe.
    private Usuario crearODevolverElQueSeAdelanto(OidcTokenVerificador.Identidad identidad) {
        boolean esAdministrador = esCorreoDeAdministrador(identidad.correo());
        Usuario nuevo = Usuario.delProveedor(identidad.sub(), identidad.correo(),
                nombreParaMostrar(identidad),
                esAdministrador ? Rol.ADMIN : Rol.USUARIO,
                esAdministrador);
        try {
            Usuario creado = creador.crear(nuevo);
            log.info("Primer ingreso: usuario local {} creado para el sub {} con rol {}",
                    creado.getId(), identidad.sub(), creado.getRol());
            return creado;
        } catch (DataIntegrityViolationException e) {
            // otra peticion de la misma persona se adelanto: el usuario ya existe
            return usuarioRepository.findByProveedorSub(identidad.sub()).orElseThrow();
        }
    }

    // El nombre y el correo los manda el proveedor, no esta aplicacion: si alla cambiaron, aqui se
    // refrescan. Solo se guarda cuando algo cambio de verdad, porque esto corre en CADA peticion y
    // escribir siempre convertiria cada lectura en una escritura.
    private Usuario actualizarSiHaceFalta(Usuario usuario, OidcTokenVerificador.Identidad identidad) {
        boolean cambio = usuario.refrescarDatosDelProveedor(identidad.correo(), identidad.nombre());
        // La lista de administradores se revisa en cada ingreso, no solo al crear: asi agregar un
        // correo a AUTH_ADMIN_EMAILS surte efecto con que la persona vuelva a entrar, sin tocar la
        // base. Quitarlo de la lista NO le baja el rol: eso se hace a mano, para no dejar al
        // sistema sin administradores por una variable de entorno mal copiada en un despliegue.
        if (usuario.getRol() != Rol.ADMIN && esCorreoDeAdministrador(usuario.getCorreo())) {
            usuario.marcarComoAdmin();
            cambio = true;
            log.info("El usuario {} queda como ADMIN: su correo esta en AUTH_ADMIN_EMAILS", usuario.getId());
        }
        return cambio ? usuarioRepository.save(usuario) : usuario;
    }

    /**
     * La persona escoge su rol: USUARIO o PUBLICADOR, y puede cambiarlo cuando quiera. ADMIN no se
     * escoge nunca por aqui, y un ADMIN no puede bajarse el rol.
     */
    @Transactional
    public Usuario elegirRol(Long usuarioId, Rol rol) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UsuarioNoEncontradoException(usuarioId));

        if (rol == Rol.ADMIN) {
            // Si su correo esta en la lista ya es ADMIN (se lo pone ingresar): pedirlo no cambia
            // nada y no es un intento de escalar privilegios. Para cualquier otro, 403.
            if (esCorreoDeAdministrador(usuario.getCorreo())) {
                return usuario;
            }
            throw new RolNoPermitidoException("El rol ADMIN no se puede escoger.",
                    "el usuario " + usuarioId + " (" + correoParaElLog(usuario)
                            + ") pidio el rol ADMIN y su correo no esta en AUTH_ADMIN_EMAILS");
        }
        if (usuario.getRol() == Rol.ADMIN) {
            throw new RolNoPermitidoException("Un ADMIN no puede cambiarse el rol por aqui.",
                    "el usuario " + usuarioId + " es ADMIN y pidio bajarse a " + rol);
        }
        usuario.escogerRol(rol);
        return usuarioRepository.save(usuario);
    }

    @Transactional(readOnly = true)
    public Usuario obtener(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UsuarioNoEncontradoException(usuarioId));
    }

    private boolean esCorreoDeAdministrador(String correo) {
        return correo != null && correosDeAdministradores.contains(correo.trim().toLowerCase(Locale.ROOT));
    }

    // Un token sin correo (hay cuentas sin correo publicado) y sin nombre tiene que poder entrar
    // igual: el nombre es obligatorio en la base, asi que se arma uno con lo que haya.
    private static String nombreParaMostrar(OidcTokenVerificador.Identidad identidad) {
        if (identidad.nombre() != null) {
            return identidad.nombre();
        }
        if (identidad.correo() != null) {
            return identidad.correo();
        }
        return "Usuario " + identidad.sub();
    }

    private static String correoParaElLog(Usuario usuario) {
        return usuario.getCorreo() == null ? "sin correo" : usuario.getCorreo();
    }
}
