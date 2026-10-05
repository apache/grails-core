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

class HqlSelectResultTypeSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(HqlResultTypeAuthor, HqlResultTypeBook)
    }

    void setup() {
        HqlResultTypeAuthor austen = new HqlResultTypeAuthor(name: 'Austen').save(failOnError: true)
        HqlResultTypeAuthor tolkien = new HqlResultTypeAuthor(name: 'Tolkien').save(failOnError: true)
        new HqlResultTypeBook(title: 'Emma', author: austen).save(failOnError: true)
        new HqlResultTypeBook(title: 'Persuasion', author: austen).save(failOnError: true)
        new HqlResultTypeBook(title: 'The Hobbit', author: tolkien).save(failOnError: true, flush: true)
    }

    void 'executeQuery returns the entities of a joined alias in the select clause'() {
        when:
        List authors = HqlResultTypeBook.executeQuery(
                'select distinct a from HqlResultTypeBook b join b.author a order by a.name')

        then:
        authors*.name == ['Austen', 'Tolkien']
        authors.every { it instanceof HqlResultTypeAuthor }
    }

    void 'executeQuery with parameters returns the entity of a joined alias in the select clause'() {
        when:
        List authors = HqlResultTypeBook.executeQuery(
                'select a from HqlResultTypeBook b join b.author a where b.title = :title', [title: 'The Hobbit'])

        then:
        authors.size() == 1
        authors[0] instanceof HqlResultTypeAuthor
        authors[0].name == 'Tolkien'
    }

    void 'a select of the root alias or a property still returns entities or values'() {
        expect:
        HqlResultTypeBook.executeQuery('select b from HqlResultTypeBook b order by b.title')*.title == ['Emma', 'Persuasion', 'The Hobbit']
        HqlResultTypeBook.executeQuery('select b.title from HqlResultTypeBook b order by b.title') == ['Emma', 'Persuasion', 'The Hobbit']
        HqlResultTypeBook.executeQuery('select distinct b.author from HqlResultTypeBook b')*.name.sort() == ['Austen', 'Tolkien']
    }

    void 'a native query selecting all columns still returns entities'() {
        when:
        List books = HqlResultTypeBook.findAllWithSql('select * from hql_result_type_book order by title')

        then:
        books*.title == ['Emma', 'Persuasion', 'The Hobbit']
        books.every { it instanceof HqlResultTypeBook }
    }
}

@Entity
class HqlResultTypeAuthor {

    String name
}

@Entity
class HqlResultTypeBook implements HibernateEntity<HqlResultTypeBook> {

    String title
    HqlResultTypeAuthor author
}
