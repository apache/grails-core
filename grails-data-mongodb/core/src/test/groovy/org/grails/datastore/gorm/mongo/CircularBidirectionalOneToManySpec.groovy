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

import grails.gorm.annotation.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue

/**
 * Created by graemerocher on 24/08/2016.
 */
class CircularBidirectionalOneToManySpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([Comment])
    }

    void "Test store and retrieve circular one-to-many association"() {
        given: "A circular one-to-many"
        new Comment(text: "Hello")
                .addToReplies(text: "World")
                .addToReplies(text: "!")
                .save(flush: true)


        manager.session.clear()

        when: "The entity is loaded"
        def first = Comment.get(1L)

        then: "The association is valid"
        first.text == "Hello"
        first.replies.size() == 2
        first.replies.any { it.text == "World" }
        first.replies.any { it.text == "!" }

    }

    @Issue('https://github.com/grails/gorm-mongodb/issues/7')
    void "Test that deleting a child doesn't not delete the parent in a circular association"() {
        given: "A circular one-to-many"
        new Comment(text: "Hello")
                .addToReplies(text: "World")
                .addToReplies(text: "!")
                .save(flush: true)

        manager.session.clear()

        when: "A child is deleted"
        Comment.findByText("World").delete(flush: true)
        manager.session.clear()

        then: "The parent wasn't deleted"
        Comment.count() == 2
    }
}

@Entity
class Comment {
    Long id
    String text
    List<Comment> replies
    static belongsTo = [parent: Comment]
    static hasMany = [replies: Comment]
}