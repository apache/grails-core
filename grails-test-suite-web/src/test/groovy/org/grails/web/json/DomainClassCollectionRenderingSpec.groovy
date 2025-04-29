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
package org.grails.web.json

import com.fasterxml.jackson.databind.ObjectMapper
import grails.persistence.Entity
import grails.rest.render.json.JsonRenderer
import grails.testing.gorm.DataTest
import grails.testing.web.GrailsWebUnitTest
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

class DomainClassCollectionRenderingSpec extends Specification implements GrailsWebUnitTest, DataTest {

    Class[] getDomainClassesToMock() {
        [Album, Company]
    }

    @Shared
    ObjectMapper objectMapper

    void setupSpec() {
        objectMapper = new ObjectMapper()
    }

    void setup() {
        final initializer = new ConvertersConfigurationInitializer(grailsApplication: grailsApplication)
        initializer.initialize()
    }

    @Issue('GRAILS-11197')
    void 'Test rendering nested collection of objects as JSON'() {
        given: 'a JSON renderer'
            def renderer = new JsonRenderer(Album)
            renderer.grailsApplication = grailsApplication
            renderer.registerCustomConverter()

        when: 'a domain object with a reference to a collection of other domain objects is rendered'
            def webRequest = GrailsWebRequest.lookup()
            def undertow = new Album(title: 'Undertow')
            def lateralus = new Album(title: 'Lateralus')
            def company = new Company(name: 'Tool Inc.')
            company.addToAlbums(undertow).addToAlbums(lateralus).save(flush: true, failOnError: true)
            renderer.render(undertow, new ServletRenderContext(webRequest, [includes: ['title', 'companies']]))

        then: 'all of the nested elements have fully qualified class names'
            def expectedResponse = '''
                {
                    "title": "Undertow",
                    "companies": [
                        {
                            "id": 1,
                            "albums": [
                                {
                                    "id": 1
                                },
                                {
                                    "id": 2
                                }
                            ],
                            "name": "Tool Inc."
                        }
                    ]
                }
            '''
            objectMapper.readTree((webRequest.response as MockHttpServletResponse).contentAsString) == objectMapper.readTree(expectedResponse)
    }
}

@Entity
class Album {

    String title
    List companies

    @SuppressWarnings('unused')
    static belongsTo = Company

    @SuppressWarnings('unused')
    static hasMany = [companies: Company]
}

@Entity
class Company {

    String name

    @SuppressWarnings('unused')
    static hasMany = [albums: Album]
}
