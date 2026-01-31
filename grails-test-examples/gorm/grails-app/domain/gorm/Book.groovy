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

import static grails.gorm.hibernate.mapping.MappingBuilder.*

/**
 * Enhanced Book domain class with more field types for testing
 * scaffolding and fields plugin rendering.
 */
class Book {

    String title
    String isbn
    String description
    Integer pageCount
    BigDecimal price
    Date publicationDate
    Boolean inStock = true

    // Association - belongsTo Author
    Author author

    static belongsTo = [author: Author]

    static constraints = {
        title blank: false, size: 1..255
        isbn nullable: true, matches: /^(?:\d{10}|\d{13})$/
        description nullable: true, maxSize: 1000, widget: 'textarea'
        pageCount nullable: true, min: 1
        price nullable: true, min: 0.0
        publicationDate nullable: true
        inStock nullable: false
        author nullable: true
    }

    static mapping = orm {
        autowire true
        description type: 'text'
    }

    String toString() {
        title
    }
}
