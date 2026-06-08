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
package grails.plugin.springsecurity.rest.token.rendering

import grails.converters.JSON
import grails.core.DefaultGrailsApplication
import grails.plugin.springsecurity.ReflectionUtils
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.rest.RestOauthController
import grails.plugin.springsecurity.rest.oauth.OauthUser
import grails.plugin.springsecurity.rest.token.AccessToken
import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import org.grails.spring.GrailsApplicationContext
import org.pac4j.core.profile.CommonProfile
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class DefaultRestAuthenticationTokenJsonRendererSpec extends Specification implements ControllerUnitTest<RestOauthController> {

    @Shared
    DefaultAccessTokenJsonRenderer renderer

    def setupSpec() {
        def application = new DefaultGrailsApplication()
        application.mainContext = new GrailsApplicationContext()
        def config = new ConfigObject()
        application.getConfig() >> config
        ReflectionUtils.application = application
        SpringSecurityUtils.loadSecondaryConfig 'DefaultRestSecurityConfig'

        renderer = new DefaultAccessTokenJsonRenderer(authoritiesPropertyName: 'roles',
                                                                  tokenPropertyName: 'access_token',
                                                                  usernamePropertyName: 'username')
    }

    @Unroll
    void "it renders proper JSON for a given token when the user has #roles.size() roles"() {
        given:
        def username = 'john.doe'
        def password = 'donttellanybody'
        def tokenValue = '1a2b3c4d'
        def userDetails = new User(username, password, roles)

        AccessToken token = new AccessToken(userDetails, roles, tokenValue)

        when:
        def jsonResult = renderer.generateJson(token)

        then:
        jsonResult == generatedJson

        where:
        roles                                                                                   | generatedJson
        [new SimpleGrantedAuthority('USER'), new SimpleGrantedAuthority('ADMIN')]     | '{"username":"john.doe","roles":["USER","ADMIN"],"access_token":"1a2b3c4d"}'
        []                                                                                      | '{"username":"john.doe","roles":[],"access_token":"1a2b3c4d"}'
    }

    void "Render JSON with custom properties"() {
        given:
        def username = 'john.doe'
        def password = 'donttellanybody'
        def tokenValue = '1a2b3c4d'
        def userDetails = new User(username, password, roles)

        AccessToken token = new AccessToken(userDetails, roles, tokenValue)

        AccessTokenJsonRenderer customRenderer =
                new DefaultAccessTokenJsonRenderer(authoritiesPropertyName: 'authorities',
                                                               tokenPropertyName: 'token',
                                                               usernamePropertyName: 'login')

        when:
        def jsonResult = customRenderer.generateJson(token)

        then:
        jsonResult == generatedJson

        where:
        roles                                                                                   | generatedJson
        [new SimpleGrantedAuthority('USER'), new SimpleGrantedAuthority('ADMIN')]     | '{"login":"john.doe","authorities":["USER","ADMIN"],"token":"1a2b3c4d"}'
        []                                                                                      | '{"login":"john.doe","authorities":[],"token":"1a2b3c4d"}'


    }

    @Issue('https://github.com/grails/grails-spring-security-rest/issues/33')
    void "it renders OAuth information if the principal is an OAuthUser"() {
        given:
        def username = 'john.doe'
        def password = 'donttellanybody'
        def tokenValue = '1a2b3c4d'
        def roles = [new SimpleGrantedAuthority('USER'), new SimpleGrantedAuthority('ADMIN')]

        def profile = new CommonProfile()
        profile.addAttribute('display_name', "John Doe")
        profile.addAttribute('email', "john@doe.com")

        def userDetails = new OauthUser(username, password, roles, profile)

        AccessToken token = new AccessToken(userDetails, roles, tokenValue)

        when:
        def jsonResult = renderer.generateJson(token)

        then:
        jsonResult == '{"username":"john.doe","roles":["USER","ADMIN"],"access_token":"1a2b3c4d","email":"john@doe.com","displayName":"John Doe"}'
    }

    def "it renders valid Bearer tokens"() {
        given:
        def tokenValue = "abcdefghijklmnopqrstuvwxyz1234567890"
        def userDetails = new User('test', 'test', [])
        def token = new AccessToken(userDetails, userDetails.authorities, tokenValue)
        renderer.useBearerToken = true

        when:
        def result = renderer.generateJson( token )
        def json = JSON.parse( result )

        then:
        result.size()
        json.access_token == tokenValue
        json.token_type == 'Bearer'

        cleanup:
        renderer.useBearerToken = false
    }

}
