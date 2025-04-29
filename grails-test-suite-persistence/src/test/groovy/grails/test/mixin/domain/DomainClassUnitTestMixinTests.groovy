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
package grails.test.mixin.domain

import grails.persistence.Entity
import grails.testing.gorm.DataTest
import spock.lang.Specification

class DomainClassUnitTestMixinTests extends Specification implements DataTest {

    void testBackReferenceAssignment() {
        mockDomains Writer, Publication

        when:
        def publication = new Publication(title: 'Some Paper')
        def writer = new Writer(name: 'Some Writer')

        writer.addToPublications(publication)

        then:
        publication.ghostWriter == null
        writer.is(publication.writer)
    }

    void testWithTransaction() {
        mockDomain Writer
        def bodyInvoked = false

        when:
        def w = new Writer(name: "Stephen King")
        w.save(flush:true)

        Writer.withTransaction {
            bodyInvoked = true
        }

        then:
        bodyInvoked
    }
}

@Entity
class Writer {
    String name
    static hasMany = [publications: Publication]
}

@Entity
class Publication {
    String title
    static belongsTo = [writer: Writer]
    Writer ghostWriter
}
