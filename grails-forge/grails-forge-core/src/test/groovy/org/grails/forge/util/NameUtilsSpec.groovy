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

package org.grails.forge.util

import spock.lang.Specification

class NameUtilsSpec extends Specification {

    void "test an empty name throws a useful exception"() {
        when:
        NameUtils.parse(null)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == '"null" is not a valid app name'

        when:
        NameUtils.parse("")

        then:
        ex = thrown(IllegalArgumentException)
        ex.message == '"" is not a valid app name'
    }

    void "a directory name is the whole application name, a dot does not split a package off"() {
        when:
        def project = NameUtils.parseDirectoryName('bookstore.demo')

        then:
        project.name == 'bookstore.demo'
        project.packageName == 'bookstoredemo'
        project.className == 'BookstoreDemo'

        when: 'parse, as for a name given on the command, still splits the last piece off'
        def given = NameUtils.parse('bookstore.demo')

        then:
        given.name == 'demo'
        given.packageName == 'bookstore'
    }

    void "a directory name without a dot gives the same project as the same name given on the command"() {
        expect:
        NameUtils.parseDirectoryName(dir).name == NameUtils.parse(dir).name
        NameUtils.parseDirectoryName(dir).packageName == NameUtils.parse(dir).packageName

        where:
        dir << ['inplaceapp', 'my-app', 'App']
    }

    void "a directory name that gives no valid application or package is rejected"() {
        when:
        NameUtils.parseDirectoryName(dir)

        then:
        thrown(IllegalArgumentException)

        where:
        dir << ['', 'micronaut', '123']
    }
}
