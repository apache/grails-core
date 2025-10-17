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
package org.grails.datastore.gorm.mongo

import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.types.ObjectId
import spock.lang.Issue

/**
 * Created by graemerocher on 25/03/14.
 */
class SimpleHasManySpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.addAllDomainClasses([Book, Chapter])
    }

    @Issue('GPMONGODB-337')
    void "Test save and retrieve one-to-many"() {

        when: "A domain model is persisted"
        def c1 = new Chapter(title: "first")
        def c2 = new Chapter(title: "second")
        c1.save()
        c2.save()

        def book = new Book(name: "mybook")
        book.save(flush: true)
        if (!book.chapters) {
            book.chapters = []
        }
        book.chapters.add(c1)
        book.chapters.add(c2)
        book.save(flush: true)
        manager.session.clear()


        book = Book.get(book.id)
        def chapters = [] as List
        book.chapters.each { it ->
            def chapter = [:] as Map
            chapter.title = it.title
            chapters << chapter
        }

        then: "The retrieved data is correct"
        chapters.find { it.title == 'first' }
        chapters.find { it.title == 'second' }

    }

    void "test changes to items in hasMany collection not persisted"() {
        given: "create a book with some chapters"
        Book book = new Book(name: "Walking the Himalayas")
        book.addToChapters(title: "Fourteen Year Later")
        book.addToChapters(title: "We Are the Pilgrims, Master")
        book.addToChapters(title: "Kabul")
        book.save(flush: true)
        manager.session.clear()

        when: "Update the title of second chapter"
        Book book1 = Book.findByName("Walking the Himalayas")
        book1.chapters.first().title = "Fourteen Years Later"
        book1.save(flush: true)
        manager.session.clear()

        then:
        Book.findByName("Walking the Himalayas").chapters.first().title == "Fourteen Years Later"
    }
}

@Entity
class Book implements Serializable {

    ObjectId id
    Long version

    String name
    List<Chapter> chapters

    static hasMany = [chapters: Chapter]
}

@Entity
class Chapter implements Serializable {

    ObjectId id
    Long version

    String title
}

