package com.booklibrary.repository;

import com.booklibrary.model.Book;
import com.booklibrary.model.BookStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookRepository extends JpaRepository<Book, UUID> {

  List<Book> findByStatus(BookStatus status);
}
