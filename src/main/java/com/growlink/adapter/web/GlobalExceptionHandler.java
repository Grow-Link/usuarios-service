package com.growlink.adapter.web;

import com.growlink.application.JardinConflictoException;
import com.growlink.application.JardinNoEncontradoException;
import com.growlink.application.RolNoPermitidoException;
import com.growlink.application.UsuarioNoEncontradoException;
import com.growlink.config.MetricasDeSeguridad;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MetricasDeSeguridad metricas;

    public GlobalExceptionHandler(MetricasDeSeguridad metricas) {
        this.metricas = metricas;
    }

    record ErrorBody(String message) {
    }

    @ExceptionHandler(UsuarioNoEncontradoException.class)
    public ResponseEntity<ErrorBody> handleNotFound(UsuarioNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody(e.getMessage()));
    }

    // Pedir un rol que no corresponde (ADMIN sin estar en la lista, o un ADMIN bajandose el rol) no
    // es un error cualquiera: es un intento de escalar privilegios, asi que queda contado y con una
    // linea de log con el X-Request-Id. Al cliente le vuelve solo el mensaje corto; el detalle de
    // quien lo pidio y con que correo se queda en el servidor.
    @ExceptionHandler(RolNoPermitidoException.class)
    public ResponseEntity<ErrorBody> handleRolNoPermitido(RolNoPermitidoException e, HttpServletRequest request) {
        metricas.prohibido(request.getMethod(), request.getRequestURI(), e.getDetalle());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorBody(e.getMessage()));
    }

    @ExceptionHandler(JardinNoEncontradoException.class)
    public ResponseEntity<ErrorBody> handleJardinNoEncontrado(JardinNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody(e.getMessage()));
    }

    @ExceptionHandler(JardinConflictoException.class)
    public ResponseEntity<ErrorBody> handleJardinConflicto(JardinConflictoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new ErrorBody(message));
    }
}
