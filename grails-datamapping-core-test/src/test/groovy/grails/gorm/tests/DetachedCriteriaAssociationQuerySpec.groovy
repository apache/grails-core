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
package grails.gorm.tests

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria
import org.grails.datastore.mapping.query.Query
import spock.lang.Issue

/**
 * Created by graemerocher on 02/11/16.
 */
class DetachedCriteriaAssociationQuerySpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {
    void setupSpec() {
        manager.addAllDomainClasses([BookA, Genre])
    }

    @Issue('https://github.com/grails/grails-data-mapping/issues/776')
    void "test that detached nested criteria work for association queries"() {
        when: "an object is queried with a detached association query"
        new BookA(genre: new Genre(description: "horror").save()).save(flush: true)
        DetachedCriteria<BookA> query = BookA.where {
            genre {
                or {
                    eq("id", 0)
                    eq("description", "horror")
                }
            }
        }
        BookA book = query.get()

        then: "The query worked"
        query.criteria.size() == 1
        query.criteria.get(0) instanceof DetachedAssociationCriteria
        query.criteria.get(0).association.name == 'genre'
        query.criteria.get(0).criteria.size() == 1
        query.criteria.get(0).criteria.get(0) instanceof Query.Disjunction
        query.criteria.get(0).criteria.get(0).criteria.size() == 2
        query.criteria.get(0).criteria.get(0).criteria.get(0) instanceof Query.Equals
        query.criteria.get(0).criteria.get(0).criteria.get(0).property == 'id'
        query.criteria.get(0).criteria.get(0).criteria.get(1) instanceof Query.Equals
        query.criteria.get(0).criteria.get(0).criteria.get(1).property == 'description'
        book != null
    }
}

@Entity
class BookA {
    Genre genre
}

@Entity
class Genre {
    String description
}