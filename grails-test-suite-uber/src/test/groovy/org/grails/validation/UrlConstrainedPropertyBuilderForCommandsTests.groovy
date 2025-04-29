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
package org.grails.validation

import grails.persistence.Entity
import grails.testing.gorm.DomainUnitTest
import grails.validation.Validateable
import spock.lang.Specification

class UrlConstrainedPropertyBuilderForCommandsTests extends Specification implements DomainUnitTest<FooConstraintsPerson> {

    void 'test empty url constraint'() {
        given:
        def cmd = new UrlConstraintsCommand()

        expect:
        cmd.validate()
    }

    void 'test a valid url'() {
        given:
        def cmd = new UrlConstraintsCommand(url: 'http://grails.org')

        expect:
        cmd.validate()
    }

    void 'test an invalid url'() {
        given:
        def cmd = new UrlConstraintsCommand(url: 'http://foo')

        expect:
        !cmd.validate()
        cmd.hasErrors()
        cmd.errors.errorCount == 1
        cmd.errors.getFieldErrors('url').size() == 1
        cmd.errors.getFieldErrors('url')[0].rejectedValue == 'http://foo'
    }
}

@Entity
class FooConstraintsPerson {
    String url

    static constraints = {
        url nullable: true, url: true
    }
}

class UrlConstraintsCommand implements Validateable {
    String url

    static constraints = {
        importFrom FooConstraintsPerson
    }
}
