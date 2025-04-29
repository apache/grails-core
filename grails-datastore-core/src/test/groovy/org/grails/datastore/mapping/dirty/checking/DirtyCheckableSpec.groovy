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
package org.grails.datastore.mapping.dirty.checking


import groovy.transform.Sortable
import spock.lang.Issue
import spock.lang.Specification

class DirtyCheckableSpec extends Specification {

    @Issue('https://github.com/grails/grails-data-mapping/issues/1231')
    def 'setting a field that implements Comparable dirty checks properly'() {
        given: 'a person'
        def person = new Person(name: 'John Doe')

        and: 'a blog post'
        def blogPost = new BlogPost(title: 'Hello World', content: 'some content')
        person.lastViewedPost = blogPost

        and: 'a second blog post is made'
        def anotherBlogPost = new BlogPost(title: blogPost.title, content: 'different content, same title')

        and: 'change list is reset'
        person.trackChanges()

        when: 'the lastViewedPost is set to something different'
        person.lastViewedPost = anotherBlogPost

        and: 'markDirty is called (normally would be weaved in by a DirtyCheckingTransformer)'
        person.markDirty('lastViewedPost', anotherBlogPost, blogPost)

        then:
        person.hasChanged()
        person.hasChanged('lastViewedPost')
        person.getOriginalValue('lastViewedPost').equals(blogPost)
    }

    def 'setting a field that is a boolean dirty checks properly'() {
        given: 'a class with a boolean property'
        def animal = new Animal()
        animal.trackChanges()

        when:"A boolean property is changed"
        animal.barks = true
        animal.markDirty("barks", true, false)

        then:"the property changed"
        animal.barks
        animal.hasChanged()
        animal.hasChanged("barks")

        when:"it is set to false"
        animal.trackChanges() // reset
        animal.barks = false
        animal.markDirty("barks", false, true)

        then:"the property changed"
        !animal.barks
        animal.hasChanged()
        animal.hasChanged("barks")

    }
}

class Animal implements DirtyCheckable {
    boolean barks
}

class Person implements DirtyCheckable {
    String name
    BlogPost lastViewedPost
}

@Sortable(includes = ['title'])
class BlogPost {
    String title
    String content
}
