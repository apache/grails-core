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
package grails.test.mixin

import grails.artefact.Artefact
import grails.persistence.Entity
import grails.rest.RestfulController
import grails.testing.gorm.DomainUnitTest
import grails.testing.web.controllers.ControllerUnitTest
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.web.mime.HttpServletResponseExtension

import spock.lang.Specification

/**
 * A {@link RestfulController} takes its resource class as a constructor argument, so the controller
 * does not have to be named after the domain class it serves. Its HTML responses redirect with
 * {@code redirect instance}, which derives a URL from the domain instance, so they have to reach the
 * same controller that its API responses name in their {@code Location} header.
 */
class RestfulControllerRedirectTargetSpec extends Specification implements ControllerUnitTest<MoviesController>, DomainUnitTest<Film> {

    def setup() {
        HttpServletResponseExtension.@mimeTypes = null
    }

    def cleanup() {
        HttpServletResponseExtension.@mimeTypes = null
    }

    void "an HTML form save redirects to the controller that saved the instance"() {
        when: 'a Film is saved through MoviesController from an HTML form'
        request.method = 'POST'
        request.format = 'form'
        params.title = 'Metropolis'
        controller.save()

        then: 'the redirect targets MoviesController, not a FilmController that does not exist'
        response.redirectedUrl == '/movies/show/1'
    }

    void "an HTML form update redirects to the controller that updated the instance"() {
        given: 'an existing Film'
        def film = new Film(title: 'Metropolis').save(flush: true)

        when: 'it is updated through MoviesController from an HTML form'
        request.method = 'PUT'
        request.format = 'form'
        params.id = film.id
        params.title = 'Nosferatu'
        controller.update()

        then: 'the redirect targets MoviesController'
        response.redirectedUrl == "/movies/show/${film.id}"
    }

    void "an HTML form save stays in the controller that saved it when another is named after the domain class"() {
        given: 'a FilmController, named after the domain class, is registered as well'
        grailsApplication.addArtefact(ControllerArtefactHandler.TYPE, FilmController)

        when: 'a Film is saved through MoviesController from an HTML form'
        request.method = 'POST'
        request.format = 'form'
        params.title = 'Metropolis'
        controller.save()

        then: 'the redirect stays in MoviesController instead of moving to FilmController'
        response.redirectedUrl == '/movies/show/1'
    }

    void "an API save names the controller that saved the instance in its Location header"() {
        when: 'the same save is made as an API request'
        request.method = 'POST'
        request.format = 'json'
        request.json = '{"title": "Metropolis"}'
        controller.save()

        then: 'the Location header targets MoviesController, as the HTML redirect does'
        response.status == 201
        response.getHeader('Location').endsWith('/movies/show/1')
    }
}

@Entity
class Film {
    String title
}

@Artefact('Controller')
class MoviesController extends RestfulController<Film> {
    MoviesController() {
        super(Film)
    }
}

/**
 * Named after {@link Film}, so the naming convention alone would send a redirect for a Film here even
 * when {@link MoviesController} saved it.
 */
@Artefact('Controller')
class FilmController {
    def show() {}
}
