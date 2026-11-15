/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package grails.gorm.tests

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import spock.lang.Issue

import jakarta.persistence.criteria.JoinType

/**
 * Criteria shapes reported against Hibernate: an order inside an association block that must not change the join
 * type the criteria asked for.
 */
class ReportedCriteriaShapesHibernateSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(ShapeBook, ShapeAuthor)
    }

    @Issue('https://github.com/apache/grails-core/issues/14543')
    void "an order in an association block keeps the left join the criteria asked for"() {
        given:
        ShapeAuthor writer = new ShapeAuthor(name: 'a').save(failOnError: true)
        new ShapeBook(title: 'with', author: writer).save(failOnError: true)
        new ShapeBook(title: 'without').save(flush: true, failOnError: true)
        manager.session.clear()

        when:
        List<String> titles = new DetachedCriteria(ShapeBook).build {
            author {
                order('name')
            }
            join('author', JoinType.LEFT)
        }.list()*.title.sort()

        then: 'the book without an author is still there'
        titles == ['with', 'without']
    }
}

@Entity
class ShapeAuthor {
    String name
}

@Entity
class ShapeBook {
    String title
    ShapeAuthor author

    static constraints = {
        author nullable: true
    }
}
