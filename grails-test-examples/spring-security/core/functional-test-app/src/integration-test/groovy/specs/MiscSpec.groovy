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
        auth.contains('Username=admin')
        auth.contains('Authenticated=true')
        auth.contains('ROLE_ADMIN')
        auth.contains('ROLE_USER') // new, added since inferred from role hierarchy
        !auth.contains('ROLE_PREVIOUS_ADMINISTRATOR')

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
        auth.contains('Username=testuser')
        auth.contains('Authenticated=true')
        auth.contains('ROLE_USER')
        auth.contains('ROLE_PREVIOUS_ADMINISTRATOR')

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
        auth.contains('Username=admin')
        auth.contains('Authenticated=true')
        auth.contains('ROLE_ADMIN')
        auth.contains('ROLE_USER')
        !auth.contains('ROLE_PREVIOUS_ADMINISTRATOR')
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
        auth.contains('Authenticated=true')
        auth.contains('ROLE_USER')

        // now get an action that's ROLE_USER only
        when:
        go('secure-annotated/user-action')

        then:
        waitFor { pageSource.contains('you have ROLE_USER') }
    }

    void 'taglibs unauthenticated'() {
        when:
        go('misc-test/test')
        def html = pageSource

        then:
        !html.contains('user and admin')
        !html.contains('user and admin and foo')
        html.contains('not user and not admin')
        !html.contains('user or admin')
        html.contains('accountNonExpired: "not logged in"')
        html.contains('id: "not logged in"')
        html.contains('Username is ""')
        !html.contains('logged in true')
        html.contains('logged in false')
        !html.contains('switched true')
        html.contains('switched false')
        html.contains('switched original username ""')
        !html.contains('access with role user: true')
        !html.contains('access with role admin: true')
        html.contains('access with role user: false')
        html.contains('access with role admin: false')
        html.contains('Can access /login/auth')
        !html.contains('Can access /secure-annotated')
        !html.contains('Cannot access /login/auth')
        html.contains('Cannot access /secure-annotated')
        html.contains('anonymous access: true')
        html.contains('Can access /misc-test/test')
        !html.contains('anonymous access: false')
        !html.contains('Cannot access /misc-test/test')
    }

    void 'taglibs user'() {
        when:
        login('testuser')

        then:
        at(IndexPage)

        when:
        go('misc-test/test')
        def html = pageSource

        then:
        !html.contains('user and admin')
        !html.contains('user and admin and foo')
        !html.contains('not user and not admin')
        html.contains('user or admin')
        html.contains('accountNonExpired: "true"')
        !html.contains('id: "not logged in"') // can't test on exact id, don't know what it is)
        html.contains('Username is "testuser"')
        html.contains('logged in true')
        !html.contains('logged in false')
        !html.contains('switched true')
        html.contains('switched false')
        html.contains('switched original username ""')
        html.contains('access with role user: true')
        !html.contains('access with role admin: true')
        !html.contains('access with role user: false')
        html.contains('access with role admin: false')
        html.contains('Can access /login/auth')
        !html.contains('Can access /secure-annotated')
        !html.contains('Cannot access /login/auth')
        html.contains('Cannot access /secure-annotated')
        html.contains('anonymous access: false')
        html.contains('Can access /misc-test/test')
        !html.contains('anonymous access: true')
    }

    void 'taglibs admin'() {
        when:
        login('admin')

        then:
        at(IndexPage)

        when:
        go('misc-test/test')
        def html = pageSource

        then:
        html.contains('user and admin')
        !html.contains('user and admin and foo')
        !html.contains('not user and not admin')
        html.contains('user or admin')
        html.contains('accountNonExpired: "true"')
        !html.contains('id: "not logged in"') // can't test on exact id, don't know what it is)
        html.contains('Username is "admin"')
        html.contains('logged in true')
        !html.contains('logged in false')
        !html.contains('switched true')
        html.contains('switched false')
        html.contains('switched original username ""')
        html.contains('access with role user: true')
        html.contains('access with role admin: true')
        !html.contains('access with role user: false')
        !html.contains('access with role admin: false')
        html.contains('Can access /login/auth')
        html.contains('Can access /secure-annotated')
        !html.contains('Cannot access /login/auth')
        !html.contains('Cannot access /secure-annotated')
        html.contains('anonymous access: false')
        html.contains('Can access /misc-test/test')
        !html.contains('anonymous access: true')
        !html.contains('Cannot access /misc-test/test')
    }

    void 'controller methods unauthenticated'() {
        when:
        go('misc-test/test-controller-methods')
        def html = pageSource

        then:
        html.contains('getPrincipal: org.springframework.security.core.userdetails.User')
        html.contains('Username=__grails.anonymous.user__')
        html.contains('Granted Authorities=[ROLE_ANONYMOUS]')
        html.contains('isLoggedIn: false')
        html.contains('loggedIn: false')
        html.contains('getAuthenticatedUser: null')
        html.contains('authenticatedUser: null')
    }

    void 'controller methods authenticated'() {
        when:
        login('admin')

        then:
        at(IndexPage)

        when:
        go('misc-test/test-controller-methods')
        def html = pageSource

        then:
        html.contains('getPrincipal: grails.plugin.springsecurity.userdetails.GrailsUser')
        html.contains('principal: grails.plugin.springsecurity.userdetails.GrailsUser')
        html.contains('Username=admin')
        html.contains('isLoggedIn: true')
        html.contains('loggedIn: true')
        html.contains('getAuthenticatedUser: TestUser(username:admin)')
        html.contains('authenticatedUser: TestUser(username:admin)')
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
        def html = pageSource

        then:
        html.contains('request.getUserPrincipal(): null')
        html.contains('request.userPrincipal: null')
        html.contains('request.isUserInRole(\'ROLE_ADMIN\'): false')
        html.contains('request.isUserInRole(\'ROLE_FOO\'): false')
        html.contains('request.getRemoteUser(): null')
        html.contains('request.remoteUser: null')
    }

    @Issue('https://github.com/apache/grails-spring-security/issues/414')
    void 'test Servlet API methods authenticated'() {
        when:
        login('admin')

        then:
        at(IndexPage)

        when:
        go('misc-test/test-servlet-api-methods')
        def html = pageSource

        then:
        html.contains('request.getUserPrincipal(): UsernamePasswordAuthenticationToken')
        html.contains('request.userPrincipal: UsernamePasswordAuthenticationToken')
        html.contains('request.isUserInRole(\'ROLE_ADMIN\'): true')
        html.contains('request.isUserInRole(\'ROLE_FOO\'): false')
        html.contains('request.getRemoteUser(): admin')
        html.contains('request.remoteUser: admin')
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
