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
import grails.gorm.transactions.Transactional
import groovy.transform.CompileStatic

/**
 * A @CompileStatic @Service abstract class that injects another @Service-typed
 * property (AuthorDataService). This exercises the fix where ServiceTransformation
 * previously generated a lazy getter on the abstract class referencing a 'datastore'
 * field that only exists on the generated implementation class, causing:
 *
 *   BUG! exception in phase 'instruction selection'
 *   Unexpected return statement at -1:-1 return authorDataService
 *
 * With the fix, the lazy getter is generated on the impl class instead,
 * allowing @CompileStatic to work correctly.
 *
 * @see org.grails.datastore.gorm.services.transform.ServiceTransformation
 */
@CompileStatic
@Service(Book)
abstract class CompileStaticBookService {

    AuthorDataService authorDataService

    abstract Book findByTitle(String title)
    abstract Book get(Serializable id)
    abstract Book save(Book book)

    abstract List<Book> list()

    abstract Long count()

    /**
     * Custom method that uses the injected AuthorDataService.
     * This is the key scenario that failed before the fix -
     * accessing authorDataService under @CompileStatic caused
     * the 'Unexpected return statement' compilation error.
     */
    @Transactional(readOnly = true)
    Map<String, Object> getBookWithAuthorDetails(Serializable bookId) {
        def book = get(bookId)
        if (book == null) {
            return null
        }
        Map<String, Object> result = [:]
        result.put('bookTitle', book.title)
        result.put('bookId', book.id)
        if (book.author != null) {
            def author = authorDataService.get(book.author.id)
            if (author != null) {
                result.put('authorName', author.name)
                result.put('authorId', author.id)
            }
        }
        return result
    }

    /**
     * Custom method demonstrating cross-service count.
     */
    @Transactional(readOnly = true)
    Map<String, Long> getCounts() {
        Map<String, Long> counts = [:]
        counts.put('books', count())
        counts.put('authors', authorDataService.count())
        return counts
    }
}
