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

package org.grails.plugins.web.controllers

import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertyName
import org.springframework.boot.context.properties.source.ConfigurationPropertySource
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

import spock.lang.Specification

class GrailsSecurityHeadersPropertiesSpec extends Specification {

    void 'the defaults are the hardening headers on and the opt-in headers off'() {
        given:
        def properties = new GrailsSecurityHeadersProperties()

        expect:
        properties.enabled
        properties.defaults == GrailsSecurityHeadersProperties.Defaults.ALWAYS
        properties.contentTypeOptions.enabled
        properties.contentTypeOptions.value == 'nosniff'
        properties.frameOptions.value == 'SAMEORIGIN'
        properties.referrerPolicy.value == 'strict-origin-when-cross-origin'
        properties.xssProtection.value == '0'
        !properties.hsts.enabled
        !properties.contentSecurityPolicy.enabled
        properties.contentSecurityPolicy.value == null
        !properties.contentTypeOptions.explicit
    }

    void 'every header can be replaced as a whole'() {
        given:
        def properties = new GrailsSecurityHeadersProperties()
        def replacement = new GrailsSecurityHeadersProperties.Header(true, 'custom')

        when:
        properties.contentTypeOptions = replacement
        properties.frameOptions = replacement
        properties.referrerPolicy = replacement
        properties.xssProtection = replacement
        properties.hsts = replacement
        properties.contentSecurityPolicy = replacement

        then:
        [properties.contentTypeOptions, properties.frameOptions, properties.referrerPolicy,
         properties.xssProtection, properties.hsts, properties.contentSecurityPolicy].every { it.is(replacement) }
    }

    void 'a header created without arguments is disabled and not explicit until configured'() {
        given:
        def header = new GrailsSecurityHeadersProperties.Header()

        expect:
        !header.enabled
        header.value == null
        !header.explicit

        when:
        header.value = 'x'

        then:
        header.explicit
    }

    void 'headers bind from application configuration and are then explicit'() {
        given:
        def source = new MapConfigurationPropertySource([
                'grails.security.headers.enabled'                  : 'false',
                'grails.security.headers.defaults'                 : 'never',
                'grails.security.headers.frame-options.value'      : 'DENY',
                'grails.security.headers.hsts.enabled'             : 'true',
        ])

        when:
        def properties = new Binder([source] as ConfigurationPropertySource[]).bind(ConfigurationPropertyName.of('grails.security.headers'),
                Bindable.of(GrailsSecurityHeadersProperties)).get()

        then:
        !properties.enabled
        properties.defaults == GrailsSecurityHeadersProperties.Defaults.NEVER
        properties.frameOptions.value == 'DENY'
        properties.frameOptions.explicit
        properties.hsts.enabled
        properties.hsts.explicit
        !properties.referrerPolicy.explicit
    }
}
