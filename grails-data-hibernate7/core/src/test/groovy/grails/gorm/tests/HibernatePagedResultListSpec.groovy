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

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import org.grails.orm.hibernate.query.HibernatePagedResultList
import spock.lang.Issue

class HibernatePagedResultListSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(HPBook)
    }

    void "test HibernatePagedResultList totalCount with HQL query"() {
        given:
        (1..10).each { i -> new HPBook(title: "Book $i").save() }
        session.flush()
        session.clear()

        when:
        def results = HPBook.list(max: 3, offset: 2, sort: "id")

        then:
        results instanceof HibernatePagedResultList
        results.size() == 3
        results.totalCount == 10
        results.max == 3
        results.offset == 2
        results[0].title == "Book 3"
        results[1].title == "Book 4"
        results[2].title == "Book 5"
    }

    void "test HibernatePagedResultList totalCount with Criteria query"() {
        given:
        new HPBook(title: "The Stand").save()
        new HPBook(title: "The Shining").save()
        new HPBook(title: "Carrie").save()
        session.flush()
        session.clear()

        when:
        def results = HPBook.createCriteria().list(max: 2) {
            like("title", "The %")
            order("title")
        }

        then:
        results instanceof HibernatePagedResultList
        results.size() == 2
        results.totalCount == 2
        results.max == 2
        results.offset == 0
        results[0].title == "The Shining"
        results[1].title == "The Stand"
    }

    void "test HibernatePagedResultList serialization"() {
        given:
        (1..5).each { i -> new HPBook(title: "Book $i").save() }
        session.flush()
        session.clear()

        when:
        def results = HPBook.list(max: 2, offset: 1, sort: "id")
        results.totalCount // Ensure initialized before serialization

        // Serialize
        def baos = new ByteArrayOutputStream()
        def oos = new ObjectOutputStream(baos)
        oos.writeObject(results)
        oos.close()

        // Deserialize
        def bais = new ByteArrayInputStream(baos.toByteArray())
        def ois = new ObjectInputStream(bais)
        def deserializedResults = (HibernatePagedResultList) ois.readObject()
        ois.close()

        then:
        deserializedResults.size() == 2
        deserializedResults.totalCount == 5
        deserializedResults.max == 2
        deserializedResults.offset == 1
        deserializedResults[0].title == "Book 2"
        deserializedResults[1].title == "Book 3"
    }

    void "test constructor with generic Query"() {
        given:
        def mockQuery = Mock(org.grails.datastore.mapping.query.Query)
        mockQuery.getEntity() >> null
        mockQuery.getMax() >> 10
        mockQuery.getOffset() >> null
        mockQuery.list() >> ["a", "b"]

        when:
        def results = new HibernatePagedResultList(mockQuery)

        then:
        results.size() == 2
        results.max == 10
        results.offset == 0
        results.totalCount == 0 // countViaHql returns 0 if entity is null
    }
}

@Entity
class HPBook implements HibernateEntity<HPBook>, Serializable {
    Long id
    String title
}
