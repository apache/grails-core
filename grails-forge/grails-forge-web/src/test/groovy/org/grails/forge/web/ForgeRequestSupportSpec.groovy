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
package org.grails.forge.web

import org.grails.forge.api.GrailsForgeConfiguration
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
import org.springframework.mock.web.MockHttpServletRequest
import spock.lang.Specification

class ForgeRequestSupportSpec extends Specification {

    void "featureFilter keeps the legacy hibernate gorm query value"() {
        when:
        def filter = ForgeRequestSupport.featureFilter(null, 'hibernate', null, null)

        then:
        filter.gorm == GormImpl.HIBERNATE5
        filter.reloading == null
        filter.servlet == null
        filter.javaVersion == null
    }

    void "featureFilter ignores unknown enum query values"() {
        when:
        def filter = ForgeRequestSupport.featureFilter(null, 'invalid', null, 'invalid')

        then:
        filter.gorm == null
        filter.javaVersion == null
    }

    void "options default every option a request leaves out or gets wrong"() {
        when:
        Options options = ForgeRequestSupport.options(null, 'invalid', null, '21', OperatingSystem.LINUX)

        then:
        options.developmentReloading == DevelopmentReloading.DEFAULT_OPTION
        options.gormImpl == GormImpl.DEFAULT_OPTION
        options.servletImpl == ServletImpl.DEFAULT_OPTION
        options.javaVersion == JdkVersion.JDK_21
        options.operatingSystem == OperatingSystem.LINUX
    }

    void "parseJdk accepts JDK_21 and numeric 21"() {
        expect:
        ForgeRequestSupport.parseJdk('JDK_21') == JdkVersion.JDK_21
        ForgeRequestSupport.parseJdk('21') == JdkVersion.JDK_21
    }

    void "parseType accepts an application type by its id or name in any case, hyphenated or not"() {
        expect:
        ForgeRequestSupport.parseType(raw) == type

        where:
        raw          | type
        'web'        | ApplicationType.WEB
        'WEB'        | ApplicationType.WEB
        'rest-api'   | ApplicationType.REST_API
        'rest_api'   | ApplicationType.REST_API
        'REST_API'   | ApplicationType.REST_API
        'web-plugin' | ApplicationType.WEB_PLUGIN
        'not-a-type' | null
        ''           | null
        null         | null
    }

    void "validName enforces the legacy create and zip patterns"() {
        expect:
        ForgeRequestSupport.validName('demo.app', ForgeRequestSupport.CREATE_NAME_PATTERN)
        !ForgeRequestSupport.validName('bad!', ForgeRequestSupport.CREATE_NAME_PATTERN)
        !ForgeRequestSupport.validName(null, ForgeRequestSupport.CREATE_NAME_PATTERN)
        ForgeRequestSupport.validName('demo-app', ForgeRequestSupport.ZIP_NAME_PATTERN)
        !ForgeRequestSupport.validName('demo.app', ForgeRequestSupport.ZIP_NAME_PATTERN)
    }

    void "featureList reads repeated and comma separated features"() {
        expect:
        ForgeRequestSupport.featureList(['gorm-mongodb', 'grails-quartz, grails-cache,', ' ']) ==
                ['gorm-mongodb', 'grails-quartz', 'grails-cache']
        ForgeRequestSupport.featureList([]) == []
        ForgeRequestSupport.featureList(null) == []
    }

    void "resolveUrl uses the scheme, host and port of the request when no URL is configured"() {
        given:
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/versions')
        request.scheme = scheme
        request.serverName = 'public.example'
        request.serverPort = port

        expect:
        ForgeRequestSupport.resolveUrl(request, new GrailsForgeConfiguration()) == url

        where:
        scheme  | port || url
        'https' | 443  || 'https://public.example'
        'http'  | 80   || 'http://public.example'
        'http'  | 8080 || 'http://public.example:8080'
    }

    void "resolveUrl uses the configured URL and path over the request"() {
        given:
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/versions')
        request.scheme = 'http'
        request.serverName = 'localhost'
        request.serverPort = 8080
        GrailsForgeConfiguration configuration = new GrailsForgeConfiguration()
        configuration.url = new URL('https://start.grails.org')
        configuration.path = '/forge'

        expect:
        ForgeRequestSupport.resolveUrl(request, configuration) == 'https://start.grails.org/forge'
    }
}
