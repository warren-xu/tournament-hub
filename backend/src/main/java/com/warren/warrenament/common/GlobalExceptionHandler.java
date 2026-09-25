package com.warren.warrenament.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exceptions.NotFoundException.class)
    public ResponseEntity<ApiError> onNotFound(Exceptions.NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("not_found", ex.getMessage()));
    }

    @ExceptionHandler(Exceptions.BadRequestException.class)
    public ResponseEntity<ApiError> onBadRequest(Exceptions.BadRequestException ex) {
        return ResponseEntity.badRequest().body(ApiError.of("bad_request", ex.getMessage()));
    }

    @ExceptionHandler(Exceptions.ForbiddenException.class)
    public ResponseEntity<ApiError> onForbidden(Exceptions.ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("forbidden", ex.getMessage()));
    }

    @ExceptionHandler(Exceptions.BidRejectedException.class)
    public ResponseEntity<ApiError> onBidRejected(Exceptions.BidRejectedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("bid_rejected", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiError.of("validation_failed", detail));
    }
}
