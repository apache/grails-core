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
package grails.test.mixin

import grails.persistence.Entity
import grails.testing.gorm.DataTest
import spock.lang.Issue
import spock.lang.Specification

/**
 * @author Graeme Rocher
 */
class PartialMockWithManyToManySpec extends Specification implements DataTest {

    void setupSpec() {
        mockDomains BlogUser, Post
    }

    @Issue('GRAILS-10022')
    def "Test that a partially mocked domain containing a many-to-many doesn't produce an error"() {
        given: "A user with posts in the db"
            BlogUser chuck = new BlogUser(loginId: "chuck_norris").save(failOnError: true, flush: true)

        when: "An associated domain that has a many-to-many relationship to another entity is"
            def p = new Post(content: "blah")
            chuck.addToPosts(p)
            chuck.save(flush: true)
            chuck.discard()
            chuck = BlogUser.findByLoginId("chuck_norris")

        then: "The domain model is persisted without issue"
            chuck != null
            chuck.posts.size() == 1

    }
}

@Entity
class BlogUser {

    String loginId

    static hasMany = [ posts : Post, tags : Tag, following : User ]
}

@Entity
class Post {

    String content

    static belongsTo = [ user : BlogUser ]

    static hasMany = [ tags : Tag ]
}
@Entity
class Tag {

    String name
    User user

    static hasMany = [ posts : Post ]
    static belongsTo = [ User, Post ]

}
