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
 * Domain class for advanced GORM query tests - Book entity.
 */
class GormBook {

    String title
    String genre
    BigDecimal price
    Integer pageCount
    Integer publicationYear
    boolean inPrint = true
    Double rating
    
    Date dateCreated
    Date lastUpdated

    static belongsTo = [author: Author]

    static constraints = {
        title blank: false, size: 1..200
        genre inList: ['Fiction', 'Non-Fiction', 'Science', 'History', 'Biography', 'Fantasy', 'Mystery', 'Romance']
        price min: 0.0
        pageCount nullable: true, min: 1
        publicationYear nullable: true, min: 1450, max: 2100
        rating nullable: true, min: 0.0d, max: 5.0d
    }

    static mapping = {
        table 'gorm_books'
    }

    static namedQueries = {
        inGenre { String genreName ->
            eq 'genre', genreName
        }
        publishedAfter { Integer year ->
            gt 'publicationYear', year
        }
        pricedBetween { BigDecimal min, BigDecimal max ->
            between 'price', min, max
        }
        highlyRated {
            ge 'rating', 4.0d
        }
        availableInPrint {
            eq 'inPrint', true
        }
    }
}
