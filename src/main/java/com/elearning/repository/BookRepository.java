package com.elearning.repository;

import com.elearning.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {

    List<Book> findByAvailableTrue();

    List<Book> findByCategoryAndAvailableTrue(String category);

    List<Book> findByLanguageAndAvailableTrue(String language);

    @Query("SELECT b FROM Book b WHERE b.available = true AND " +
           "(LOWER(b.title) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(b.author) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(b.description) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<Book> search(@Param("q") String query);

    @Query("SELECT b FROM Book b WHERE b.available = true AND " +
           "(:category IS NULL OR b.category = :category) AND " +
           "(:language IS NULL OR b.language = :language) AND " +
           "(:q IS NULL OR LOWER(b.title) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(b.author) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<Book> findWithFilters(@Param("category") String category,
                               @Param("language") String language,
                               @Param("q") String q);

    List<Book> findByCategoryAndAvailableTrueOrderByYearDesc(String category);
}
