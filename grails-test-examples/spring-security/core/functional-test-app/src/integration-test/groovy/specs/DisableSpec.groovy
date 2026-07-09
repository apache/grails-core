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
package specs

import pages.IndexPage
import spock.lang.IgnoreIf

import grails.testing.mixin.integration.Integration

@Integration
@IgnoreIf({ System.getProperty('TESTCONFIG') != 'misc' })
class DisableSpec extends AbstractHyphenatedSecuritySpec {

    void 'lock account'() {
        given:
        def username = 'admin'

        when:
        login(username)

        then:
        at(IndexPage)

        when:
        go('secure-annotated')

        then:
        waitFor { pageSource.contains('you have ROLE_ADMIN') }

        when:
        logout()

        then:
        getUserProperty(username, 'accountLocked') == 'false'

        when:
        setUserProperty(username, 'accountLocked', true)

        then:
        getUserProperty(username, 'accountLocked') == 'true'

        when:
        login(username)

        then:
        waitFor { pageSource.contains('accountLocked') }

        // reset
        when:
        setUserProperty(username, 'accountLocked', false)

        then:
        getUserProperty(username, 'accountLocked') == 'false'
    }

    void 'disable account'() {
        given:
        def username = 'admin'

        when:
        login(username)

        then:
        at(IndexPage)

        when:
        go('secure-annotated')

        then:
        waitFor { pageSource.contains('you have ROLE_ADMIN') }

        when:
        logout()

        then:
        getUserProperty(username, 'enabled') == 'true'

        when:
        setUserProperty(username, 'enabled', false)

        then:
        getUserProperty(username, 'enabled') == 'false'

        when:
        login(username)

        then:
        waitFor { pageSource.contains('accountDisabled') }

        // reset
        when:
        setUserProperty(username, 'enabled', true)

        then:
        getUserProperty(username, 'enabled') == 'true'
    }

    void 'expire account'() {
        given:
        def username = 'admin'

        when:
        login(username)

        then:
        at(IndexPage)

        when:
        go('secure-annotated')

        then:
        waitFor { pageSource.contains('you have ROLE_ADMIN') }

        when:
        logout()

        then:
        getUserProperty(username, 'accountExpired') == 'false'

        when:
        setUserProperty(username, 'accountExpired', true)

        then:
        getUserProperty(username, 'accountExpired') == 'true'

        when:
        login(username)

        then:
        waitFor { pageSource.contains('accountExpired') }

        // reset
        when:
        setUserProperty(username, 'accountExpired', false)

        then:
        getUserProperty(username, 'accountExpired') == 'false'
    }

    void 'expire password'() {
        given:
        def username = 'admin'

        when:
        login(username)

        then:
        at(IndexPage)

        when:
        go('secure-annotated')

        then:
        waitFor { pageSource.contains('you have ROLE_ADMIN') }

        when:
        logout()

        then:
        getUserProperty(username, 'passwordExpired') == 'false'

        when:
        setUserProperty(username, 'passwordExpired', true)

        then:
        getUserProperty(username, 'passwordExpired') == 'true'

        when:
        login(username)

        then:
        waitFor { pageSource.contains('passwordExpired') }

        // reset
        when:
        setUserProperty(username, 'passwordExpired', false)

        then:
        getUserProperty(username, 'passwordExpired') == 'false'
    }

    private void setUserProperty(String user, String propertyName, value) {
        go("hack/set-user-property?user=$user&$propertyName=$value")
    }
}
