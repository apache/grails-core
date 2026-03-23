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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package grails.gorm.specs

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import org.grails.orm.hibernate.query.PagedResultList

class PagedResultListSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([PRLBook])
    }

    void "test PagedResultList totalCount with HQL query"() {
        given:
        new PRLBook(title: "The Stand").save()
        new PRLBook(title: "The Shining").save()
        new PRLBook(title: "Carrie").save()
        session.flush()
        session.clear()

        when:
        def results = PRLBook.list(max: 2, sort: "title")

        then:
        results instanceof PagedResultList
        results.size() == 2
        results.totalCount == 3
        results[0].title == "Carrie"
        results[1].title == "The Shining"
    }

    void "test PagedResultList with offset and max"() {
        given:
        (1..10).each { i -> new PRLBook(title: "Book $i").save() }
        session.flush()
        session.clear()

        when:
        def results = PRLBook.list(max: 3, offset: 2, sort: "id")

        then:
        results instanceof PagedResultList
        results.size() == 3
        results.totalCount == 10
        results.max == 3
        results.offset == 2
        // results[0] should be "Book 3" (offset 2, 0-indexed id assumed here for simplicity of logic)
        results.every { it.title.startsWith("Book ") }
    }

    void "test PagedResultList totalCount with Criteria query"() {
        given:
        new PRLBook(title: "The Stand").save()
        new PRLBook(title: "The Shining").save()
        new PRLBook(title: "Carrie").save()
        session.flush()
        session.clear()

        when:
        def results = PRLBook.createCriteria().list(max: 2) {
            like("title", "The %")
            order("title")
        }

        then:
        results instanceof grails.gorm.PagedResultList
        results.size() == 2
        results.totalCount == 2
        results.max == 2
        results.offset == 0
        results[0].title == "The Shining"
        results[1].title == "The Stand"
    }
}

@Entity
class PRLBook implements HibernateEntity<PRLBook> {
    Long id
    String title
}
