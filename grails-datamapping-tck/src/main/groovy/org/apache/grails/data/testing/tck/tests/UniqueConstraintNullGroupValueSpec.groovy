/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.grails.data.testing.tck.tests

import spock.lang.IgnoreIf
import spock.lang.Issue

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.UniqueNullFolder
import org.apache.grails.data.testing.tck.domains.UniqueNullLanguage
import org.apache.grails.data.testing.tck.domains.UniqueNullMulti
import org.apache.grails.data.testing.tck.domains.UniqueNullRoute

/**
 * A null value of a property in the group of a {@code unique} constraint is compared as a value:
 * it only conflicts with the rows whose value of that property is also null.
 *
 * The simple in-memory datastore of the TCK does not register the {@code unique} constraint, so this specification
 * does not run there; {@code UniqueConstraintSpec} of {@code grails-datamapping-core-test} covers it.
 */
@IgnoreIf({ Boolean.getBoolean('simple.gorm.suite') })
@Issue('https://github.com/apache/grails-core/issues/14503')
class UniqueConstraintNullGroupValueSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.registerDomainClasses(UniqueNullRoute, UniqueNullLanguage, UniqueNullFolder, UniqueNullMulti)
    }

    void 'a null group value does not conflict with a row that has a group value'() {
        given:
        new UniqueNullRoute(url: '/test1/**', httpMethod: 'POST').save(failOnError: true, flush: true)

        when:
        def route = new UniqueNullRoute(url: '/test1/**')

        then:
        route.validate()
        route.save(failOnError: true, flush: true)
        UniqueNullRoute.count() == 2
    }

    void 'a null group value conflicts with a row whose group value is null'() {
        given:
        new UniqueNullRoute(url: '/test1/**').save(failOnError: true, flush: true)

        when:
        def duplicate = new UniqueNullRoute(url: '/test1/**')

        then:
        !duplicate.validate()
        duplicate.errors.getFieldError('url').code == 'unique'
    }

    void 'a group value conflicts with a row that has the same group value but not with one whose group value is null'() {
        given:
        new UniqueNullRoute(url: '/test1/**').save(failOnError: true, flush: true)
        new UniqueNullRoute(url: '/test1/**', httpMethod: 'POST').save(failOnError: true, flush: true)

        expect:
        !new UniqueNullRoute(url: '/test1/**', httpMethod: 'POST').validate()
        new UniqueNullRoute(url: '/test1/**', httpMethod: 'GET').validate()
    }

    void 'a language without a country does not conflict with the same language of a country'() {
        given:
        new UniqueNullLanguage(language: 'en', country: 'US').save(failOnError: true, flush: true)

        expect:
        new UniqueNullLanguage(language: 'en').validate()
        !new UniqueNullLanguage(language: 'en', country: 'US').validate()
    }

    void 'two languages without a country conflict'() {
        given:
        new UniqueNullLanguage(language: 'en').save(failOnError: true, flush: true)

        when:
        def duplicate = new UniqueNullLanguage(language: 'en')

        then:
        !duplicate.validate()
        duplicate.errors.getFieldError('language').code == 'unique'
    }

    void 'top level folders with the same name conflict'() {
        given:
        new UniqueNullFolder(name: 'Root').save(failOnError: true, flush: true)

        when:
        def duplicate = new UniqueNullFolder(name: 'Root')

        then:
        !duplicate.validate()
        duplicate.errors.getFieldError('name').code == 'unique'
    }

    void 'a top level folder does not conflict with a folder of the same name under a parent'() {
        given:
        def other = new UniqueNullFolder(name: 'Other').save(failOnError: true, flush: true)
        new UniqueNullFolder(name: 'Root', parent: other).save(failOnError: true, flush: true)

        when:
        def root = new UniqueNullFolder(name: 'Root')

        then:
        root.validate()
        root.save(failOnError: true, flush: true)
    }

    void 'a folder does not conflict with a top level folder of the same name'() {
        given:
        def other = new UniqueNullFolder(name: 'Other').save(failOnError: true, flush: true)
        new UniqueNullFolder(name: 'Root').save(failOnError: true, flush: true)

        expect:
        new UniqueNullFolder(name: 'Root', parent: other).validate()
    }

    void 'folders with the same name under the same parent conflict'() {
        given:
        def other = new UniqueNullFolder(name: 'Other').save(failOnError: true, flush: true)
        new UniqueNullFolder(name: 'Root', parent: other).save(failOnError: true, flush: true)

        when:
        def duplicate = new UniqueNullFolder(name: 'Root', parent: other)

        then:
        !duplicate.validate()
        duplicate.errors.getFieldError('name').code == 'unique'
    }

    void 'clearing the group value of a saved row validates against the rows whose group value is null'() {
        given:
        new UniqueNullLanguage(language: 'en').save(failOnError: true, flush: true)
        def french = new UniqueNullLanguage(language: 'en', country: 'FR').save(failOnError: true, flush: true)

        when:
        french.country = null

        then:
        !french.validate()
        french.errors.getFieldError('language').code == 'unique'
    }

    void 'setting the group value of a saved row to a free value validates'() {
        given:
        new UniqueNullLanguage(language: 'en').save(failOnError: true, flush: true)
        def other = new UniqueNullLanguage(language: 'en', country: 'FR').save(failOnError: true, flush: true)

        when:
        other.country = 'US'

        then:
        other.validate()
    }

    void 'every null member of the group is compared as a value'() {
        given:
        new UniqueNullMulti(code: 'A', region: 'EU').save(failOnError: true, flush: true)
        new UniqueNullMulti(code: 'A', channel: 'web').save(failOnError: true, flush: true)
        new UniqueNullMulti(code: 'A', region: 'EU', channel: 'web').save(failOnError: true, flush: true)
        new UniqueNullMulti(code: 'A').save(failOnError: true, flush: true)

        expect: 'a row that repeats the null and non null members of an existing row conflicts'
        !new UniqueNullMulti(code: 'A', region: 'EU').validate()
        !new UniqueNullMulti(code: 'A', channel: 'web').validate()
        !new UniqueNullMulti(code: 'A', region: 'EU', channel: 'web').validate()
        !new UniqueNullMulti(code: 'A').validate()

        and: 'a row that differs in any member does not'
        new UniqueNullMulti(code: 'A', region: 'US').validate()
        new UniqueNullMulti(code: 'A', channel: 'app').validate()
        new UniqueNullMulti(code: 'A', region: 'US', channel: 'web').validate()
        new UniqueNullMulti(code: 'B').validate()
    }
}
