package com.elearning.repository;

import com.elearning.entity.BookAnnotation;
import com.elearning.entity.User;
import com.elearning.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookAnnotationRepository extends JpaRepository<BookAnnotation, Long> {
    List<BookAnnotation> findByUserAndBookOrderByPageNumberAscCreatedAtDesc(User user, Book book);
    List<BookAnnotation> findByUserAndBook_IdAndPageNumber(User user, Long bookId, Integer page);
    void deleteByIdAndUser(Long id, User user);
}
