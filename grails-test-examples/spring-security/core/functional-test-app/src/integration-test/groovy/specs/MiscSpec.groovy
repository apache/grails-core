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

import geb.module.TextInput
import pages.IndexPage
import spock.lang.IgnoreIf
import spock.lang.Issue

import grails.testing.mixin.integration.Integration

@Integration
@IgnoreIf({ System.getProperty('TESTCONFIG') != 'misc' })
class MiscSpec extends AbstractHyphenatedSecuritySpec {

    void 'salted password'() {
        given:
        def username = 'testuser_books_and_movies'
        def passwordEncoder = createSha256Encoder()

        when:
        def hashedPassword = getUserProperty(username, 'password')
        def notSalted = passwordEncoder.encode('password')

        then:
        notSalted != hashedPassword
    }

    void 'switch user'() {
        when:
        login('admin')

        then:
        at(IndexPage)

        // verify logged in
        when:
        go('secure-annotated')

        then:
        waitFor { pageSource.contains('you have ROLE_ADMIN') }

        when:
        def auth = getSessionValue('SPRING_SECURITY_CONTEXT')

        then:
        with(auth) {
            contains('Username=admin')
            contains('Authenticated=true')
            contains('ROLE_ADMIN')
            contains('ROLE_USER') // new, added since inferred from role hierarchy
            !contains('ROLE_PREVIOUS_ADMINISTRATOR')
        }

        // switch via GET
        when:
        go('login/impersonate?username=testuser')

        then:
        waitFor { pageSource.contains('Error 404 Page Not Found') }

        // switch via POST
        when:
        go('misc-test/test')
        def input = $("#username").module(TextInput)
        input.text = 'testuser'
        $("#switchUserFormSubmitButton").click()

        then:
        waitFor { pageSource.contains('Available Controllers:') }

        // verify logged in as testuser

        when:
        auth = getSessionValue('SPRING_SECURITY_CONTEXT')

        then:
        with(auth) {
            contains('Username=testuser')
            contains('Authenticated=true')
            contains('ROLE_USER')
            contains('ROLE_PREVIOUS_ADMINISTRATOR')
        }

        when:
        go('secure-annotated/user-action')

        then:
        waitFor { pageSource.contains('you have ROLE_USER') }

        // verify not logged in as admin
        when:
        go('secure-annotated/admin-either')

        then:
        waitFor { pageSource.contains('Sorry, you\'re not authorized to view this page.') }

        // switch back via GET
        when:
        go('logout/impersonate')

        then:
        waitFor { pageSource.contains('Error 404 Page Not Found') }

        // switch via POST
        when:
        go('misc-test/test')
        $("#exitUserFormSubmitButton").click()

        then:
        waitFor { pageSource.contains('Available Controllers:') }

        // verify logged in as admin
        when:
        go('secure-annotated/admin-either')

        then:
        waitFor { pageSource.contains('you have ROLE_ADMIN') }

        when:
        auth = getSessionValue('SPRING_SECURITY_CONTEXT')

        then:
        with(auth) {
            contains('Username=admin')
            contains('Authenticated=true')
            contains('ROLE_ADMIN')
            contains('ROLE_USER')
            !contains('ROLE_PREVIOUS_ADMINISTRATOR')
        }
    }

    void 'hierarchical roles'() {
        when:
        login('admin')

        then:
        at(IndexPage)

        // verify logged in
        when:
        go('secure-annotated')

        then:
        waitFor { pageSource.contains('you have ROLE_ADMIN') }

        when:
        def auth = getSessionValue('SPRING_SECURITY_CONTEXT')

        then:
        with(auth) {
            contains('Authenticated=true')
            contains('ROLE_USER')
        }

        // now get an action that's ROLE_USER only
        when:
        go('secure-annotated/user-action')

        then:
        waitFor { pageSource.contains('you have ROLE_USER') }
    }

    void 'taglibs unauthenticated'() {
        when:
        go('misc-test/test')

        then:
        with(pageSource) {
            !contains('user and admin')
            !contains('user and admin and foo')
            contains('not user and not admin')
            !contains('user or admin')
            contains('accountNonExpired: "not logged in"')
            contains('id: "not logged in"')
            contains('Username is ""')
            !contains('logged in true')
            contains('logged in false')
            !contains('switched true')
            contains('switched false')
            contains('switched original username ""')

            !contains('access with role user: true')
            !contains('access with role admin: true')
            contains('access with role user: false')
            contains('access with role admin: false')

            contains('Can access /login/auth')
            !contains('Can access /secure-annotated')
            !contains('Cannot access /login/auth')
            contains('Cannot access /secure-annotated')

            contains('anonymous access: true')
            contains('Can access /misc-test/test')
            !contains('anonymous access: false')
            !contains('Cannot access /misc-test/test')
        }
    }

    void 'taglibs user'() {
        when:
        login('testuser')

        then:
        at(IndexPage)

        when:
        go('misc-test/test')

        then:
        with(pageSource) {
            !contains('user and admin')
            !contains('user and admin and foo')
            !contains('not user and not admin')
            contains('user or admin')
            contains('accountNonExpired: "true"')
            !contains('id: "not logged in"') // can't test on exact id, don't know what it is)
            contains('Username is "testuser"')
            contains('logged in true')
            !contains('logged in false')
            !contains('switched true')
            contains('switched false')
            contains('switched original username ""')

            contains('access with role user: true')
            !contains('access with role admin: true')
            !contains('access with role user: false')
            contains('access with role admin: false')

            contains('Can access /login/auth')
            !contains('Can access /secure-annotated')
            !contains('Cannot access /login/auth')
            contains('Cannot access /secure-annotated')

            contains('anonymous access: false')
            contains('Can access /misc-test/test')
            !contains('anonymous access: true')
        }
    }

    void 'taglibs admin'() {
        when:
        login('admin')

        then:
        at(IndexPage)

        when:
        go('misc-test/test')

        then:
        with(pageSource) {
            contains('user and admin')
            !contains('user and admin and foo')
            !contains('not user and not admin')
            contains('user or admin')
            contains('accountNonExpired: "true"')
            !contains('id: "not logged in"') // can't test on exact id, don't know what it is)
            contains('Username is "admin"')

            contains('logged in true')
            !contains('logged in false')
            !contains('switched true')
            contains('switched false')
            contains('switched original username ""')

            contains('access with role user: true')
            contains('access with role admin: true')
            !contains('access with role user: false')
            !contains('access with role admin: false')

            contains('Can access /login/auth')
            contains('Can access /secure-annotated')
            !contains('Cannot access /login/auth')
            !contains('Cannot access /secure-annotated')

            contains('anonymous access: false')
            contains('Can access /misc-test/test')
            !contains('anonymous access: true')
            !contains('Cannot access /misc-test/test')
        }
    }

    void 'controller methods unauthenticated'() {
        when:
        go('misc-test/test-controller-methods')

        then:
        with(pageSource) {
            contains('getPrincipal: org.springframework.security.core.userdetails.User')
            contains('Username=__grails.anonymous.user__')
            contains('Granted Authorities=[ROLE_ANONYMOUS]')
            contains('isLoggedIn: false')
            contains('loggedIn: false')
            contains('getAuthenticatedUser: null')
            contains('authenticatedUser: null')
        }
    }

    void 'controller methods authenticated'() {
        when:
        login('admin')

        then:
        at(IndexPage)

        when:
        go('misc-test/test-controller-methods')

        then:
        with(pageSource) {
            contains('getPrincipal: grails.plugin.springsecurity.userdetails.GrailsUser')
            contains('principal: grails.plugin.springsecurity.userdetails.GrailsUser')
            contains('Username=admin')
            contains('isLoggedIn: true')
            contains('loggedIn: true')
            contains('getAuthenticatedUser: TestUser(username:admin)')
            contains('authenticatedUser: TestUser(username:admin)')
        }
    }

    void 'test hyphenated'() {
        when:
        go('foo-bar')

        then:
        waitFor { pageSource.contains('Please Login') }

        when:
        to(IndexPage)

        and:
        go('foo-bar/index')

        then:
        waitFor { pageSource.contains('Please Login') }

        when:
        to(IndexPage)

        and:
        go('foo-bar/bar-foo')

        then:
        waitFor { pageSource.contains('Please Login') }

        when:
        logout()

        then:
        at(IndexPage)

        when:
        login('admin')

        then:
        at(IndexPage)

        when:
        go('foo-bar')

        then:
        waitFor { pageSource.contains('INDEX') }

        when:
        go('foo-bar/index')

        then:
        waitFor { pageSource.contains('INDEX') }

        when:
        go('foo-bar/bar-foo')

        then:
        waitFor { pageSource.contains('barFoo') }
    }

    @Issue('https://github.com/apache/grails-spring-security/issues/414')
    void 'test Servlet API methods unauthenticated'() {
        when:
        go('misc-test/test-servlet-api-methods')

        then:
        with(pageSource) {
            contains('request.getUserPrincipal(): null')
            contains('request.userPrincipal: null')
            contains('request.isUserInRole(\'ROLE_ADMIN\'): false')
            contains('request.isUserInRole(\'ROLE_FOO\'): false')
            contains('request.getRemoteUser(): null')
            contains('request.remoteUser: null')
        }
    }

    @Issue('https://github.com/apache/grails-spring-security/issues/414')
    void 'test Servlet API methods authenticated'() {
        when:
        login('admin')

        then:
        at(IndexPage)

        when:
        go('misc-test/test-servlet-api-methods')

        then:
        with(pageSource) {
            contains('request.getUserPrincipal(): UsernamePasswordAuthenticationToken')
            contains('request.userPrincipal: UsernamePasswordAuthenticationToken')
            contains('request.isUserInRole(\'ROLE_ADMIN\'): true')
            contains('request.isUserInRole(\'ROLE_FOO\'): false')
            contains('request.getRemoteUser(): admin')
            contains('request.remoteUser: admin')
        }
    }

    @Issue('https://github.com/apache/grails-spring-security/issues/403')
    void 'test controller with annotated index action, unauthenticated'() {
        when:
        go('index-annotated')

        then:
        waitFor { pageSource.contains('Please Login') }

        when:
        go('index-annotated/')

        then:
        waitFor { pageSource.contains('Please Login') }

        when:
        go('index-annotated/index')

        then:
        waitFor { pageSource.contains('Please Login') }

        when:
        go('index-annotated/show')

        then:
        waitFor { pageSource.contains('Please Login') }
    }

    @Issue('https://github.com/apache/grails-spring-security/issues/403')
    void 'test controller with annotated index action, authenticated'() {
        when:
        login('admin')

        then:
        at(IndexPage)

        when:
        go('index-annotated')

        then:
        waitFor { pageSource.contains('index action, principal: ') }

        when:
        go('index-annotated/')

        then:
        waitFor { pageSource.contains('index action, principal: ') }

        when:
        go('index-annotated/index')

        then:
        waitFor { pageSource.contains('index action, principal: ') }

        when:
        go('index-annotated/show')

        then:
        waitFor { pageSource.contains('Sorry, you\'re not authorized to view this page.') }
    }
}
