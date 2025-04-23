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

package grails301.domain.save.npe

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import spock.lang.*

@Integration(applicationClass = Application)
@Rollback
class BookSpecSpec extends Specification {

    void "It works well if there is no validate error in both Java 7 and 8"() {
        given:
        def b = new Book(title: "OK")

        when:
        b.save()

        then:
        Book.count() == 1
    }

    void "It works well if there is a validate error: maxSize"() {
        given:
        def b = new Book(title: "TOO_LONG")

        when:
        b.save()

        then:
        b.hasErrors() // But NPE occurs in Java 7!
    }

    void "It works well if there is a validate error: nullable"() {
        given:
        def b = new Book()

        when:
        b.save()

        then:
        b.hasErrors() // But NPE occurs in Java 7!
    }
}
