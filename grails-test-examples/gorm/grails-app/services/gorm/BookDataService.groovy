/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package gorm

import grails.gorm.services.Service
import grails.gorm.services.Where
import grails.gorm.services.Query
import grails.gorm.transactions.Transactional

/**
 * GORM Data Service for Book domain class.
 * Demonstrates abstract class-based data services with automatic implementation
 * of CRUD methods and dynamic finders.
 *
 * Data Services are a feature of GORM that generates implementations
 * at compile time, reducing boilerplate code.
 */
@Service(Book)
abstract class BookDataService {

    // ==========================================
    // Basic CRUD operations - auto-generated
    // ==========================================

    abstract Book get(Serializable id)

    abstract List<Book> list(Map args)

    abstract Long count()

    abstract void delete(Serializable id)

    abstract Book save(Book book)

    // ==========================================
    // Dynamic finders - auto-implemented
    // ==========================================

    abstract Book findByTitle(String title)

    abstract List<Book> findAllByTitle(String title)

    abstract List<Book> findAllByInStock(Boolean inStock)

    // Count queries
    abstract Number countByInStock(Boolean inStock)

    // ==========================================
    // Custom @Where queries
    // ==========================================

    @Where({ title ==~ pattern })
    abstract List<Book> findByTitlePattern(String pattern)

    @Where({ pageCount >= minPages && pageCount <= maxPages })
    abstract List<Book> findByPageRange(Integer minPages, Integer maxPages)

    @Where({ price < maxPrice && inStock == true })
    abstract List<Book> findAffordableInStockBooks(BigDecimal maxPrice)

    // ==========================================
    // Custom @Query (HQL) queries
    // ==========================================

    @Query("from Book b where b.title like :pattern order by b.title")
    abstract List<Book> searchByTitleHql(String pattern)

    @Query("select count(b) from Book b where b.author is not null")
    abstract Number countBooksWithAuthor()

    @Query("select distinct b.title from Book b")
    abstract List<String> findAllDistinctTitles()

    // Update operations with @Query
    @Query("update Book b set b.inStock = :inStock where b.id = :id")
    abstract Number updateStockStatus(Serializable id, Boolean inStock)

    // Delete with @Where
    @Where({ title == titleToDelete })
    abstract Number deleteByTitle(String titleToDelete)

    // Projection queries
    @Query("select b.title, b.price from Book b where b.price is not null")
    abstract List<Object[]> findTitlesAndPrices()

    @Query("select avg(b.price) from Book b where b.price is not null")
    abstract BigDecimal findAveragePrice()

    @Query("select max(b.pageCount) from Book b")
    abstract Integer findMaxPageCount()

    // ==========================================
    // Manual implementations for complex operations
    // ==========================================

    /**
     * Check if a book with the given title exists.
     * Manually implemented since existsBy* is not auto-generated.
     */
    @Transactional(readOnly = true)
    boolean existsByTitle(String title) {
        findByTitle(title) != null
    }

    /**
     * Find a book by ISBN using HQL since ISBN is nullable.
     */
    @Transactional(readOnly = true)
    Book findByIsbnValue(String isbn) {
        if (isbn == null) return null
        Book.executeQuery("from Book b where b.isbn = :isbn", [isbn: isbn]).find()
    }

    /**
     * Count books matching a title pattern (using LIKE).
     */
    @Transactional(readOnly = true)
    Number countByTitleLike(String pattern) {
        Book.executeQuery("select count(b) from Book b where b.title like :pattern", [pattern: pattern])[0]
    }

    /**
     * Find books by title and optional ISBN.
     */
    @Transactional(readOnly = true)
    Book findByTitleAndOptionalIsbn(String title, String isbn) {
        if (isbn) {
            Book.executeQuery("from Book b where b.title = :title and b.isbn = :isbn",
                [title: title, isbn: isbn]).find()
        } else {
            findByTitle(title)
        }
    }
}
