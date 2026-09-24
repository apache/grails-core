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

package org.grails.forge.feature.grails

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.Options
import spock.lang.Unroll

class GrailsUrlMappingsSpec extends ApplicationContextSpec implements CommandOutputFixture {

    private static final String FAVICON_MAPPING = '"/favicon.ico"(redirect: [uri: \'/assets/favicon.ico\', permanent: true])'

    void 'a web app maps the home page, the favicon the browser asks for on its own, and the error pages'() {
        when:
        def urlMappings = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))['grails-app/controllers/example/grails/UrlMappings.groovy']

        then:
        urlMappings.contains('package example.grails')
        urlMappings.contains('"/$namespace/$controller/$action?/$id?(.$format)?" {}')
        urlMappings.contains('"/"(view:"/index")')

        and: 'the favicon is served from the asset pipeline instead of answering the not-found page'
        urlMappings.contains(FAVICON_MAPPING)

        and: 'the error mappings follow'
        urlMappings.contains('"500"(view:\'/error\')')
        urlMappings.contains('"404"(view:\'/notFound\')')
    }

    @Unroll
    void 'a #applicationType carries no favicon mapping'(ApplicationType applicationType) {
        when:
        def urlMappings = generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS))['grails-app/controllers/example/grails/UrlMappings.groovy']

        then:
        urlMappings != null
        !urlMappings.contains(FAVICON_MAPPING)
        !urlMappings.contains('favicon')

        where:
        applicationType << [ApplicationType.WEB_PLUGIN, ApplicationType.REST_API]
    }
}
