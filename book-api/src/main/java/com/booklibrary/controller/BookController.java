package com.booklibrary.controller;

import com.booklibrary.model.Book;
import com.booklibrary.model.BookStatus;
import com.booklibrary.service.BookService;
import com.booklibrary.service.FeatureFlag;
import com.booklibrary.service.FeatureFlagService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * REST endpoints for books.
 *
 * <p>This class is the source of truth for the API: the OpenAPI spec is generated from it, and API
 * Gateway is built from that spec.
 *
 * <p>About the response annotations:
 *
 * <ul>
 *   <li>{@code @ResponseStatus} documents the success code. At runtime the ResponseEntity's status
 *       is what's actually sent.
 *   <li>{@code @ApiResponse} documents the error codes, which springdoc can't infer.
 *   <li>{@code content = @Content} marks responses with no documented body. Without it, springdoc
 *       would wrongly attach the Book schema to error responses.
 * </ul>
 */
@RestController
@RequestMapping("/books")
public class BookController {

  private final BookService bookService;
  private final FeatureFlagService featureFlags;

  public BookController(BookService bookService, FeatureFlagService featureFlags) {
    this.bookService = bookService;
    this.featureFlags = featureFlags;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @ApiResponse(
      responseCode = "400",
      description =
          "Invalid book: title, author or status missing, status not one of WANT_TO_READ, READING,"
              + " FINISHED, or the body isn't valid JSON",
      content = @Content)
  @ApiResponse(
      responseCode = "403",
      description = "Creating books is disabled by the enableCreateBook feature flag",
      content = @Content)
  public ResponseEntity<Book> createBook(@Valid @RequestBody Book book) {
    if (!featureFlags.isEnabled(FeatureFlag.CREATE_BOOK)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Creating books is disabled");
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(bookService.createBook(book));
  }

  @GetMapping
  @ApiResponse(responseCode = "200", description = "Books, optionally filtered by status")
  @ApiResponse(
      responseCode = "400",
      description = "status filter is not one of WANT_TO_READ, READING, FINISHED",
      content = @Content)
  public ResponseEntity<List<Book>> listBooks(@RequestParam(required = false) BookStatus status) {
    // When enableListFilter is off, the status filter is ignored.
    BookStatus effectiveStatus = featureFlags.isEnabled(FeatureFlag.LIST_FILTER) ? status : null;
    return ResponseEntity.ok(bookService.listBooks(effectiveStatus));
  }

  @GetMapping("/{id}")
  @ApiResponse(responseCode = "200", description = "The book")
  @ApiResponse(responseCode = "400", description = "id is not a valid UUID", content = @Content)
  @ApiResponse(responseCode = "404", description = "No book with this id", content = @Content)
  public ResponseEntity<Book> getBook(@PathVariable UUID id) {
    return bookService
        .getBook(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PutMapping("/{id}")
  @ApiResponse(responseCode = "200", description = "The updated book")
  @ApiResponse(
      responseCode = "400",
      description =
          "id is not a valid UUID, status is not a valid value, or the body isn't valid JSON",
      content = @Content)
  @ApiResponse(responseCode = "404", description = "No book with this id", content = @Content)
  public ResponseEntity<Book> updateBook(@PathVariable UUID id, @RequestBody Book updates) {
    return bookService
        .updateBook(id, updates)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiResponse(responseCode = "404", description = "No book with this id", content = @Content)
  @ApiResponse(
      responseCode = "403",
      description = "Deleting books is disabled by the enableDeleteBook feature flag",
      content = @Content)
  public ResponseEntity<Void> deleteBook(@PathVariable UUID id) {
    if (!featureFlags.isEnabled(FeatureFlag.DELETE_BOOK)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Deleting books is disabled");
    }
    return bookService.deleteBook(id)
        ? ResponseEntity.noContent().build()
        : ResponseEntity.notFound().build();
  }
}
