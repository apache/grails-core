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

package functionaltests.gorm

/**
 * Domain class for advanced GORM query tests.
 */
class Author {

    String name
    String country
    Integer birthYear
    boolean active = true
    
    Date dateCreated
    Date lastUpdated

    static hasMany = [books: GormBook]

    static constraints = {
        name blank: false, size: 1..100
        country nullable: true
        birthYear nullable: true, min: 1800, max: 2100
    }

    static mapping = {
        books cascade: 'all-delete-orphan'
    }

    static namedQueries = {
        activeAuthors {
            eq 'active', true
        }
        fromCountry { String countryName ->
            eq 'country', countryName
        }
        bornAfter { Integer year ->
            gt 'birthYear', year
        }
        prolificAuthors {
            // Authors with more than 2 books
            sizeGt 'books', 2
        }
    }
}
