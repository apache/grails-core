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
package org.grails.datastore.gorm

import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform
import spock.lang.Issue

/**
 * ensure that detached criteria ast transformations work on annotated jpa entities
 */
@ApplyDetachedCriteriaTransform
@Issue('GRAILS-9750')
class DetachedCriteriaJpaEntitySpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([Todo])
    }

    def "test a where query on a jpa entity"() {
        given: "a todo"
        new Todo(title: "todo").save(flush: true)
        manager.session.clear()

        when: "query without restrictions"
        def results = Todo.findAll {}

        then: "one todo"
        results.size() == 1

        when: "query with matching restrictions"
        results = Todo.findAll {
            title == "todo"
        }

        then: "one todo"
        results.size() == 1

        when: "query with not matching restrictions"
        results = Todo.findAll {
            title == "no match"
        }

        then: "no todo"
        results.size() == 0
    }

}

@jakarta.persistence.Entity
@grails.persistence.Entity
class Todo {
    Long id
    String title
}