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
package org.grails.plugins.web.mime

import grails.core.DefaultGrailsApplication
import grails.spring.BeanBuilder
import grails.web.mime.MimeType
import org.grails.web.mime.HttpServletResponseExtension
import spock.lang.Specification

class MimeTypesConfigurationSpec extends Specification {

    void setup() {
        // Clear the static mimeTypes cache to prevent test environment pollution
        HttpServletResponseExtension.@mimeTypes = null
    }

    void cleanup() {
        // Clear the static mimeTypes cache after each test for test isolation
        HttpServletResponseExtension.@mimeTypes = null
    }

    void "test when no mimeTypes configured then the defaults should be used"() {
        when:
        MimeType[] mimeTypes = resolveMimeTypes(null)

        then:
        MimeType.createDefaults() == mimeTypes
    }

    void "test the defaults provide the full set of supported mime types"() {
        when:
        MimeType[] mimeTypes = resolveMimeTypes(null)

        then: "every extension historically declared in application.yml is available out of the box"
        mimeTypes.find { it.extension == 'all' && it.name == '*/*' }
        mimeTypes.find { it.extension == 'form' && it.name == 'application/x-www-form-urlencoded' }
        mimeTypes.find { it.extension == 'multipartForm' && it.name == 'multipart/form-data' }
        mimeTypes.find { it.extension == 'css' && it.name == 'text/css' }
        mimeTypes.find { it.extension == 'csv' && it.name == 'text/csv' }
        mimeTypes.find { it.extension == 'js' && it.name == 'text/javascript' }
        mimeTypes.find { it.extension == 'pdf' && it.name == 'application/pdf' }
        mimeTypes.find { it.extension == 'rss' && it.name == 'application/rss+xml' }
        mimeTypes.find { it.extension == 'atom' && it.name == 'application/atom+xml' }
        mimeTypes.find { it.extension == 'text' && it.name == 'text/plain' }
        mimeTypes.find { it.extension == 'hal' && it.name == 'application/hal+json' }
        mimeTypes.find { it.extension == 'html' && it.name == 'text/html' }
        mimeTypes.find { it.extension == 'json' && it.name == 'application/json' }
        mimeTypes.find { it.extension == 'xml' && it.name == 'application/xml' }
    }

    void "test a declared configuration replaces the defaults by default"() {
        when: "only a single extension is declared"
        MimeType[] mimeTypes = resolveMimeTypes([json: 'application/json'])

        then: "the declared configuration is authoritative and the defaults are not merged in"
        mimeTypes*.name == ['application/json']
        !mimeTypes.find { it.extension == 'html' }
        !mimeTypes.find { it.extension == 'xml' }
    }

    void "test a declared configuration keeps its declared order so the first entry is the default format"() {
        when:
        MimeType[] mimeTypes = resolveMimeTypes([html: ['text/html', 'application/xhtml+xml'], json: 'application/json'])

        then:
        mimeTypes[0].extension == 'html'
        mimeTypes*.name == ['text/html', 'application/xhtml+xml', 'application/json']
    }

    void "test mergeDefaults appends the defaults the user did not declare"() {
        when: "a single custom extension is declared with merging enabled"
        MimeType[] mimeTypes = resolveMimeTypes([custom: 'application/x-custom'], true)

        then: "the declared type is first and the inherited defaults are appended"
        mimeTypes[0].extension == 'custom'
        mimeTypes.find { it.extension == 'custom' && it.name == 'application/x-custom' }
        mimeTypes.find { it.extension == 'html' }
        mimeTypes.find { it.extension == 'json' }
        mimeTypes.find { it.extension == 'all' }
    }

    void "test mergeDefaults overrides a default for the same extension"() {
        when: "the json extension is overridden with merging enabled"
        MimeType[] mimeTypes = resolveMimeTypes([json: 'application/json'], true)

        then: "only the configured json mime type is present"
        mimeTypes.findAll { it.extension == 'json' }*.name == ['application/json']

        and: "other defaults are untouched"
        mimeTypes.find { it.extension == 'html' && it.name == 'text/html' }
        mimeTypes.findAll { it.extension == 'xml' }*.name == ['text/xml', 'application/xml']
    }

    private MimeType[] resolveMimeTypes(Map config, boolean mergeDefaults = false) {
        def application = new DefaultGrailsApplication()
        if (config != null) {
            application.config['grails.mime.types'] = config
        }
        if (mergeDefaults) {
            application.config['grails.mime.mergeDefaults'] = true
        }
        def bb = new BeanBuilder()
        bb.beans {
            grailsApplication = application
            mimeConfiguration(MimeTypesConfiguration, application, [])
        }
        application.setApplicationContext(bb.createApplicationContext())
        MimeTypesConfiguration mimeTypesConfiguration = application.mainContext.getBean(MimeTypesConfiguration)
        return mimeTypesConfiguration.mimeTypes()
    }
}
