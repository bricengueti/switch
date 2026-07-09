package TNB.Switch.exception;

import TNB.Switch.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

    // 1. Capture des exceptions spécifiques au Switch (Device déconnecté, etc.)
    @ExceptionHandler(SwitchException.class)
    public ResponseEntity<ErrorResponse> handleSwitchException(SwitchException ex, WebRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        // Ajustement du statut HTTP selon le type d'erreur technique
        if (ex instanceof DeviceDisconnectedException) {
            status = HttpStatus.SERVICE_UNAVAILABLE; // 503
        } else if (ex instanceof TransactionNotFoundException) {
            status = HttpStatus.NOT_FOUND; // 404
        }

        ErrorResponse error = new ErrorResponse(ex.getErrorCode(), ex.getMessage());
        return new ResponseEntity<>(error, status);
    }

    // 2. Capture des contraintes d'idempotence ou doublons en base de données (Data Integrity)
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(Exception ex, WebRequest request) {
        ErrorResponse error = new ErrorResponse(
                "DUPLICATE_TRANSACTION_OR_KEY",
                "Une transaction avec cette clé d'idempotence ou cette référence existe déjà."
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT); // 409
    }

    // 3. Capture de toutes les autres exceptions non gérées (Sécurité / Boîte noire)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {
        ErrorResponse error = new ErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "Une erreur interne imprévue est survenue sur le Switch."
        );
        // Ici, il conviendra d'ajouter un logger pour que l'admin puisse voir la stacktrace réelle
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR); // 500
    }
}