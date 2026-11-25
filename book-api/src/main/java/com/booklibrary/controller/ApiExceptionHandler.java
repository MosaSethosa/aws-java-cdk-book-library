package com.booklibrary.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

/**
 * Puts the reason of a {@link ResponseStatusException} in the response body as JSON. Spring Boot
 * leaves the body empty by default, so callers would only see the status code.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {

    String message =
        StringUtils.hasText(e.getReason()) ? e.getReason() : e.getStatusCode().toString();

    return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", message));
  }
}
