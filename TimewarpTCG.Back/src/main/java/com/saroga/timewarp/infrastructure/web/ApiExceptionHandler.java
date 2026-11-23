package com.saroga.timewarp.infrastructure.web;

import com.saroga.timewarp.infrastructure.security.InvalidTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Respuestas de error genericas: nunca se devuelven trazas ni detalles internos al cliente (se registran en el servidor). */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<?> invalidToken(InvalidTokenException e) {
        return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Petición mal formada."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception e) {
        // errores "normales" de Spring (404, 405, 400 por parametro que falta...): conservan su codigo, con mensaje generico
        if (e instanceof ErrorResponse) {
            HttpStatusCode status = ((ErrorResponse) e).getStatusCode();
            HttpStatus known = HttpStatus.resolve(status.value());
            return ResponseEntity.status(status).body(Map.of("error", known != null ? known.getReasonPhrase() : "Error"));
        }
        System.err.println("[ERROR] Excepcion no controlada en la API: " + e);
        e.printStackTrace();
        return ResponseEntity.status(500).body(Map.of("error", "Error interno del servidor."));
    }
}
