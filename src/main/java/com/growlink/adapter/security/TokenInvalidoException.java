package com.growlink.adapter.security;

// El token de Microsoft falta, esta vencido, viene firmado con otra llave, o no es para nosotros
// (emisor o audiencia que no cuadran). El motivo va en el mensaje para que quede en el log del
// servidor con el X-Request-Id de la peticion; al cliente solo le vuelve un 401 pelado, sin
// detalles que le sirvan para adivinar que parte de su token hay que arreglar.
public class TokenInvalidoException extends RuntimeException {

    public TokenInvalidoException(String motivo) {
        super(motivo);
    }

    public TokenInvalidoException(String motivo, Throwable causa) {
        super(motivo, causa);
    }
}
