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
package grails.plugin.scaffolding

import grails.codegen.model.ModelBuilder
import grails.plugin.scaffolding.redirect.Film
import grails.plugin.scaffolding.redirect.FilmService
import grails.testing.gorm.DataTest
import grails.testing.web.GrailsWebUnitTest
import groovy.text.GStringTemplateEngine
import spock.lang.Shared
import spock.lang.Specification

/**
 * A controller generated from the scaffolding template redirects a form save or update to its own show
 * action, so it keeps working when renamed by hand rather than sending the user to a controller named
 * after the domain class.
 */
class GeneratedControllerRedirectSpec extends Specification implements GrailsWebUnitTest, DataTest, ModelBuilder {

    @Shared
    Map<String, Class> controllers = [:]

    /**
     * The scaffolding template the controllers are generated from.
     */
    String getTemplateName() {
        'Controller'
    }

    def setupSpec() {
        mockDomain(Film)
        mockDataService(FilmService)

        String generated = new GStringTemplateEngine()
                .createTemplate(new File("src/main/templates/scaffolding/${templateName}.groovy"))
                .make(model(Film).asMap())
                .toString()
        GroovyClassLoader classLoader = new GroovyClassLoader(getClass().classLoader)
        for (String name in ['Film', 'Movies']) {
            controllers[name] = classLoader.parseClass(generated.replace('class FilmController {',
                    "@grails.artefact.Artefact('Controller')\nclass ${name}Controller {"))
        }
    }

    void 'a form save through the #name controller redirects to its own show action'() {
        given:
        def controller = mockController(controllers[name])

        when:
        request.method = 'POST'
        request.format = 'form'
        params.title = 'Metropolis'
        controller.save()

        then:
        response.redirectedUrl == "/${path}/show/${Film.findByTitle('Metropolis').id}"

        where: 'the controller as generated, and renamed so that no controller is named after the domain class'
        name     | path
        'Film'   | 'film'
        'Movies' | 'movies'
    }

    void 'a form update through the #name controller redirects to its own show action'() {
        given:
        def film = new Film(title: 'Metropolis').save(flush: true, failOnError: true)
        def controller = mockController(controllers[name])

        when:
        request.method = 'PUT'
        request.format = 'form'
        params.id = film.id
        params.title = 'Nosferatu'
        controller.update()

        then:
        response.redirectedUrl == "/${path}/show/${film.id}"

        where:
        name     | path
        'Film'   | 'film'
        'Movies' | 'movies'
    }
}
