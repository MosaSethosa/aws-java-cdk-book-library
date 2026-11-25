package com.booklibrary.service;

import com.booklibrary.model.Book;
import com.booklibrary.model.BookStatus;
import com.booklibrary.repository.BookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class BookService {

  private final BookRepository bookRepository;

  public BookService(BookRepository bookRepository) {
    this.bookRepository = bookRepository;
  }

  @Transactional
  public Book createBook(Book book) {
    return bookRepository.save(book);
  }

  public List<Book> listBooks(BookStatus status) {
    return status != null ? bookRepository.findByStatus(status) : bookRepository.findAll();
  }

  public Optional<Book> getBook(UUID id) {
    return bookRepository.findById(id);
  }

  @Transactional
  public Optional<Book> updateBook(UUID id, Book updates) {
    return bookRepository
        .findById(id)
        .map(
            existing -> {
              if (updates.getTitle() != null) existing.setTitle(updates.getTitle());
              if (updates.getAuthor() != null) existing.setAuthor(updates.getAuthor());
              if (updates.getGenre() != null) existing.setGenre(updates.getGenre());
              if (updates.getStatus() != null) existing.setStatus(updates.getStatus());
              return bookRepository.save(existing);
            });
  }

  @Transactional
  public boolean deleteBook(UUID id) {
    if (bookRepository.existsById(id)) {
      bookRepository.deleteById(id);
      return true;
    }
    return false;
  }
}
