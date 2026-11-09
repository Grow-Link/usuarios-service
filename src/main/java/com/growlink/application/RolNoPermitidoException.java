package com.growlink.application;

// Alguien pidio un rol que no le corresponde: ADMIN sin estar en AUTH_ADMIN_EMAILS, o un ADMIN
// tratando de bajarse el rol. Se traduce a 403 (ver GlobalExceptionHandler): la persona esta bien
// autenticada, lo que pidio es lo que no se puede.
//
// Van dos textos a proposito: al cliente se le dice lo justo, y el detalle (quien lo pidio y con
// que correo) queda en el log del servidor, que es donde sirve para ver si alguien esta insistiendo.
public class RolNoPermitidoException extends RuntimeException {

    private final String detalle;

    public RolNoPermitidoException(String mensajeParaElCliente, String detalleParaElLog) {
        super(mensajeParaElCliente);
        this.detalle = detalleParaElLog;
    }

    public String getDetalle() {
        return detalle;
    }
}
