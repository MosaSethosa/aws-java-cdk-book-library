package com.booklibrary.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "books")
public class Book {

  // READ_ONLY: returned in responses, ignored in request bodies. Clients can't
  // choose their own ID or timestamps, and the generated OpenAPI spec marks
  // these fields readOnly so they don't appear as inputs.
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "book_id", updatable = false, nullable = false)
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private UUID bookId;

  @NotBlank(message = "Title is required")
  @Column(nullable = false)
  private String title;

  @NotBlank(message = "Author is required")
  @Column(nullable = false)
  private String author;

  @Column private String genre;

  @NotNull(message = "Status is required")
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BookStatus status;

  @Column(name = "created_at", updatable = false)
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private Instant createdAt;

  @Column(name = "updated_at")
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public UUID getBookId() {
    return bookId;
  }

  public void setBookId(UUID bookId) {
    this.bookId = bookId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public String getGenre() {
    return genre;
  }

  public void setGenre(String genre) {
    this.genre = genre;
  }

  public BookStatus getStatus() {
    return status;
  }

  public void setStatus(BookStatus status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
