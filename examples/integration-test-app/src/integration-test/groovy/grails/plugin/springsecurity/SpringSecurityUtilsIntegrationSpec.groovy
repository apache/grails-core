/* Copyright 2006-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugin.springsecurity

import org.sitemesh.webapp.SiteMeshFilter
import org.springframework.web.filter.FormContentFilter

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE
import static org.springframework.beans.factory.support.AbstractBeanDefinition.AUTOWIRE_BY_NAME
import grails.plugin.springsecurity.userdetails.GrailsUser
import grails.plugin.springsecurity.web.SecurityRequestHolderFilter
import grails.plugin.springsecurity.web.authentication.GrailsUsernamePasswordAuthenticationFilter
import grails.plugin.springsecurity.web.authentication.logout.MutableLogoutFilter
import grails.plugin.springsecurity.web.filter.GrailsAnonymousAuthenticationFilter
import grails.plugin.springsecurity.web.filter.GrailsRememberMeAuthenticationFilter
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.access.ExceptionTranslationFilter
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor
import org.springframework.security.web.context.SecurityContextPersistenceFilter
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter
import org.springframework.web.filter.GenericFilterBean
import test.TestRole
import test.TestUser
import test.TestUserRole

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse

/**
 * Integration tests for <code>SpringSecurityUtils</code>.
 *
 * @author Burt Beckwith
 */
class SpringSecurityUtilsIntegrationSpec extends AbstractIntegrationSpec {

    def securityFilterChains
    SpringSecurityService springSecurityService

    private static final String username = 'username'
    private static TestUser testUser

    void setup() {
        if (testUser) {
            return
        }

        TestUser.withNewTransaction {
            testUser = save(new TestUser(loginName: username, passwrrd: 'password'))
            def role = save(new TestRole(auth: 'ROLE_ADMIN', description: 'admin'))
            TestUserRole.create testUser, role

            def user = save(new TestUser(loginName: 'other', passwrrd: 'password'))
            TestUserRole.create user, role
        }
    }

    void cleanupSpec() {
        TestUser.withNewTransaction {
            TestUserRole.deleteAll TestUserRole.list()
            TestRole.deleteAll TestRole.list()
            TestUser.deleteAll TestUser.list()
        }
    }

    void cleanup() {
        SecurityContextHolder.clearContext() // logout
    }

    void 'clientRegisterFilter'() {
        given:
        def map = SpringSecurityUtils.configuredOrderedFilters

        expect:
        11 == map.size()
        map[Integer.MIN_VALUE + 10] instanceof SecurityRequestHolderFilter
        map[SecurityFilterPosition.SECURITY_CONTEXT_FILTER.order] instanceof SecurityContextPersistenceFilter
        map[SecurityFilterPosition.LOGOUT_FILTER.order] instanceof MutableLogoutFilter
        map[SecurityFilterPosition.FORM_LOGIN_FILTER.order] instanceof GrailsUsernamePasswordAuthenticationFilter
        map[SecurityFilterPosition.SERVLET_API_SUPPORT_FILTER.order] instanceof SecurityContextHolderAwareRequestFilter
        map[SecurityFilterPosition.REMEMBER_ME_FILTER.order] instanceof GrailsRememberMeAuthenticationFilter
        map[SecurityFilterPosition.ANONYMOUS_FILTER.order] instanceof GrailsAnonymousAuthenticationFilter
        map[SecurityFilterPosition.EXCEPTION_TRANSLATION_FILTER.order-10] instanceof FormContentFilter
        map[SecurityFilterPosition.EXCEPTION_TRANSLATION_FILTER.order-5] instanceof SiteMeshFilter
        map[SecurityFilterPosition.EXCEPTION_TRANSLATION_FILTER.order] instanceof ExceptionTranslationFilter
        map[SecurityFilterPosition.FILTER_SECURITY_INTERCEPTOR.order] instanceof FilterSecurityInterceptor

        when:
        SpringSecurityUtils.clientRegisterFilter 'foo', SecurityFilterPosition.LOGOUT_FILTER

        then:
        thrown AssertionError

        when:
        SpringSecurityUtils.clientRegisterFilter 'foo', SecurityFilterPosition.LOGOUT_FILTER.order + 10

        then:
        thrown NoSuchBeanDefinitionException

        when:
        SpringSecurityUtils.clientRegisterFilter 'passwordEncoder', SecurityFilterPosition.LOGOUT_FILTER.order + 10

        then:
        thrown ClassCastException

        when:
        grailsApplication.mainContext.registerBeanDefinition 'dummyFilter',
                new GenericBeanDefinition(beanClass: DummyFilter, scope: SCOPE_PROTOTYPE, autowireMode: AUTOWIRE_BY_NAME)

        SpringSecurityUtils.clientRegisterFilter 'dummyFilter', SecurityFilterPosition.LOGOUT_FILTER.order + 10

        then:
        12 == map.size()
        map[SecurityFilterPosition.LOGOUT_FILTER.order + 10] instanceof DummyFilter

        when:
        def filters = securityFilterChains[0].filters

        then:
        filters[0] instanceof SecurityRequestHolderFilter
        filters[1] instanceof SecurityContextPersistenceFilter
        filters[2] instanceof MutableLogoutFilter
        filters[3] instanceof DummyFilter
        filters[4] instanceof GrailsUsernamePasswordAuthenticationFilter
        filters[5] instanceof SecurityContextHolderAwareRequestFilter
        filters[6] instanceof GrailsRememberMeAuthenticationFilter
        filters[7] instanceof GrailsAnonymousAuthenticationFilter
        filters[8] instanceof FormContentFilter
        filters[9] instanceof SiteMeshFilter
        filters[10] instanceof ExceptionTranslationFilter
        filters[11] instanceof FilterSecurityInterceptor
    }

    void 'reauthenticate'() {
        expect:
        !springSecurityService.loggedIn

        when:
        SpringSecurityUtils.reauthenticate username, null

        then:
        springSecurityService.loggedIn

        when:
        def principal = springSecurityService.principal

        then:
        principal instanceof GrailsUser
        ['ROLE_ADMIN'] == principal.authorities.authority
        username == principal.username
    }

    void 'doWithAuth with a current auth'() {
        expect:
        !springSecurityService.loggedIn

        when:
        SpringSecurityUtils.reauthenticate username, null

        then:
        springSecurityService.loggedIn

        when:
        doInThread {
            assert !springSecurityService.loggedIn, "shouldn't appear authenticated in a new thread"

            SpringSecurityUtils.doWithAuth username, {
                assert springSecurityService.loggedIn
                assert username == springSecurityService.principal.username
            }

            assert !springSecurityService.loggedIn, 'should have reset auth'
        }

        then:
        assert springSecurityService.loggedIn, 'should still be authenticated in main thread'
    }

    void 'doWithAuth with a new auth'() {
        expect:
        !springSecurityService.loggedIn

        when:
        doInThread {
            assert !springSecurityService.loggedIn, "shouldn't appear authenticated in a new thread"

            SpringSecurityUtils.doWithAuth username, {
                assert springSecurityService.loggedIn
                assert username == springSecurityService.principal.username
            }

            assert !springSecurityService.loggedIn, 'should have reset auth'
        }

        then:
        assert !springSecurityService.loggedIn, 'should still be unauthenticated in main thread'
    }

    void 'doWithAuth with a new auth, existing'() {
        expect:
        !springSecurityService.loggedIn

        when:
        SpringSecurityUtils.reauthenticate username, null

        then:
        springSecurityService.loggedIn

        when:
        doInThread {
            assert !springSecurityService.loggedIn, "shouldn't appear authenticated in a new thread"

            SpringSecurityUtils.doWithAuth 'other', {
                assert springSecurityService.loggedIn
                assert 'other' == springSecurityService.principal.username
            }

            assert !springSecurityService.loggedIn, 'should have reset auth'
        }

        then:
        assert springSecurityService.loggedIn, 'should still be authenticated'
        assert username == springSecurityService.principal.username, 'should still be unauthenticated in main thread'
    }

    void 'getCurrentUser not logged in'() {
        expect:
        !springSecurityService.loggedIn
        !springSecurityService.currentUser
    }

    void 'getCurrentUser logged in'() {
        when:
        SpringSecurityUtils.reauthenticate username, null

        then:
        springSecurityService.loggedIn

        when:
        def currentUser = springSecurityService.currentUser

        then:
        currentUser
        currentUser.id == testUser.id
    }

    private void doInThread(Closure c) {
        Throwable exception

        Thread.start {
            try {
                c()
            }
            catch (Throwable e) {
                exception = e
            }
        }.join()

        if (exception) {
            throw exception
        }
    }
}

class DummyFilter extends GenericFilterBean {
    void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {}
}
