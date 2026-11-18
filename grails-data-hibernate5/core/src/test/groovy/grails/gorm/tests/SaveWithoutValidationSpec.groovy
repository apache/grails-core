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

import spock.lang.Issue

@Issue('https://github.com/apache/grails-core/issues/15276')
class SaveWithoutValidationSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(UnvalidatedNote)
    }

    void "an invalid instance saved with validate false is persisted by the flush"() {
        when:
        new UnvalidatedNote(title: '').save(validate: false, flush: true)
        session.clear()

        then:
        UnvalidatedNote.count() == 1

        when: 'a later valid save flushes while the invalid instance is still managed'
        UnvalidatedNote kept = UnvalidatedNote.list().first()
        kept.body = 'edited'
        new UnvalidatedNote(title: 'valid').save(failOnError: true, flush: true)

        then:
        UnvalidatedNote.count() == 2
    }
}

@Entity
class UnvalidatedNote {
    String title
    String body

    static constraints = {
        title blank: false
        body nullable: true
    }
}
