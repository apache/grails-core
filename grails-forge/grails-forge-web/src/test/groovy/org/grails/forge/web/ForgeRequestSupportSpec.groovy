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
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.springframework.mock.web.MockHttpServletRequest
import spock.lang.Specification

class ForgeRequestSupportSpec extends Specification {

    void "featureFilter keeps the legacy hibernate gorm query value"() {
        when:
        def filter = ForgeRequestSupport.featureFilter([gorm: 'hibernate'])

        then:
        filter.gorm == GormImpl.HIBERNATE5
    }

    void "featureFilter ignores unknown enum query values"() {
        when:
        def filter = ForgeRequestSupport.featureFilter([gorm: 'invalid', javaVersion: 'invalid'])

        then:
        filter.gorm == null
        filter.javaVersion == null
    }

    void "parseJdk accepts JDK_21 and numeric 21"() {
        expect:
        ForgeRequestSupport.parseJdk('JDK_21') == JdkVersion.JDK_21
        ForgeRequestSupport.parseJdk('21') == JdkVersion.JDK_21
    }

    void "parseType accepts web and rejects unknown application types"() {
        expect:
        ForgeRequestSupport.parseType('web') == ApplicationType.WEB
        ForgeRequestSupport.parseType('not-a-type') == null
    }

    void "validName enforces the legacy create and zip patterns"() {
        expect:
        ForgeRequestSupport.validName('demo.app', ForgeRequestSupport.CREATE_NAME_PATTERN)
        !ForgeRequestSupport.validName('bad!', ForgeRequestSupport.CREATE_NAME_PATTERN)
        ForgeRequestSupport.validName('demo-app', ForgeRequestSupport.ZIP_NAME_PATTERN)
        !ForgeRequestSupport.validName('demo.app', ForgeRequestSupport.ZIP_NAME_PATTERN)
    }

    void "toJson serializes HAL links as _links"() {
        when:
        org.grails.forge.api.VersionDTO dto = new org.grails.forge.api.VersionDTO()
        dto.addLink(org.grails.forge.api.Relationship.SELF, new org.grails.forge.api.LinkDTO('http://localhost/versions', false))
        String json = ForgeRequestSupport.toJson(dto)

        then:
        json.contains('"_links"')
        !json.contains('"links"')
    }

    void "resolveUrl uses X-Forwarded-Proto and Host when no configured URL is present"() {
        given:
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/versions')
        request.scheme = 'http'
        request.serverName = 'localhost'
        request.serverPort = 8080
        request.addHeader('X-Forwarded-Proto', 'https')
        request.addHeader('Host', 'public.example')
        GrailsForgeConfiguration configuration = new GrailsForgeConfiguration()
        configuration.url = null

        expect:
        ForgeRequestSupport.resolveUrl(request, configuration) == 'https://public.example'
    }
}
