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

    void "a new application is refused a package or a name that is the name of a device on Windows: #name"() {
        when:
        NameUtils.parse(name)

        then:
        IllegalArgumentException e = thrown()
        e.message == message

        when: 'an application there already is, is taken as it is'
        def project = NameUtils.parse(name, false)

        then:
        project.packageName == packageName

        where:
        name                 | packageName    || message
        'com.aux.shop'       | 'com.aux'      || 'The package com.aux has the part aux, which is the name of a device on Windows, so the project could not be checked out there'
        'CON.acme.shop'      | 'CON.acme'     || 'The package CON.acme has the part CON, which is the name of a device on Windows, so the project could not be checked out there'
        'com.acme.com1.shop' | 'com.acme.com1' || 'The package com.acme.com1 has the part com1, which is the name of a device on Windows, so the project could not be checked out there'
        'com.acme.nul'       | 'com.acme'     || 'The name nul is the name of a device on Windows, so a directory of that name could not be made there'
    }

    void "a name that only starts like the name of a device is taken: #name"() {
        expect:
        NameUtils.parse(name).packageName == packageName

        where:
        name               | packageName
        'com.auxiliary.shop' | 'com.auxiliary'
        'com.console.shop' | 'com.console'
        'com.com10.shop'   | 'com.com10'
        'com.acme.null'    | 'com.acme'
    }

    void "what is not one line of text, or is a path, is no name for a new application: #what"() {
        when:
        NameUtils.parse(name)

        then:
        IllegalArgumentException e = thrown()
        e.message == message
        e.message.readLines().size() == 1
        !e.message.contains('\u001b')

        where:
        what                  | name                              || message
        'an escape sequence'  | 'com.ex\u001b[31mample.shop'       || '"com.ex?[31mample.shop" is not a valid app name: it has a character that is not shown, such as a control character'
        'a line break'        | 'com.example.sh\nop'               || '"com.example.sh?op" is not a valid app name: it has a character that is not shown, such as a control character'
        'a character that is not seen' | 'com.example.sh\u200Bop'  || '"com.example.sh?op" is not a valid app name: it has a character that is not shown, such as a control character'
        'a slash'             | 'com.example.sh/op'               || '"com.example.sh/op" is not a valid app name: it has a path separator'
        'a backslash'         | 'com.example.sh\\op'              || '"com.example.sh\\op" is not a valid app name: it has a path separator'
        'a path'              | '../../etc/passwd'                || '"../../etc/passwd" is not a valid app name: it has a path separator'
        'a colon'             | 'com.example.sh:op'               || '"sh:op" is not a valid app name: not every file system takes it as the name of a directory'
        'a space at its end'  | 'com.example.shop '               || '"shop " is not a valid app name: not every file system takes it as the name of a directory'
        'markup of the command line' | 'com.@|red x|@.shop'       || 'Cannot create a valid package name for [com.@ |red x|@]. Please specify a name that is also a valid Java package.'
        'a very long name'    | 'com.example.' + ('x' * 300)      || '"' + ('x' * 80) + '..." is not a valid app name: not every file system takes it as the name of a directory'
    }

    void "the names that were names stay names: #name"() {
        expect:
        NameUtils.parse(name).name == appName

        where:
        name                    | appName
        'com.acme.my-shop'      | 'my-shop'
        'com.Acme_1.$x.shop'    | 'shop'
        'my-shop'               | 'my-shop'
        'com.acme.shop_2'       | 'shop_2'
    }

    void "a package does not end with a dot: #name"() {
        when:
        NameUtils.parse(name)

        then:
        thrown(IllegalArgumentException)

        expect:
        !NameUtils.isValidJavaPackage('com.acme.')
        !NameUtils.isValidJavaPackage('.com.acme')
        NameUtils.isValidJavaPackage('com.acme')

        where:
        name << ['com.acme..shop', '.com.acme.shop', 'com..acme.shop']
    }
}
