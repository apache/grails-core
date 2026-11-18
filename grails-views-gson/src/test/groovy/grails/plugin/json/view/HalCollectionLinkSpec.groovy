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
package grails.plugin.json.view

import grails.plugin.json.view.hallinks.Film
import grails.plugin.json.view.hallinks.MoviesController
import grails.views.json.test.JsonViewUnitTest
import org.grails.core.artefact.ControllerArtefactHandler
import spock.lang.Specification

/**
 * The self link of a HAL collection targets the controller serving the domain class of its elements, as
 * the self link of each element does. The controller registered here stays registered for the whole
 * specification, so it has one of its own.
 */
class HalCollectionLinkSpec extends Specification implements JsonViewUnitTest {

    def setupSpec() {
        mappingContext.addPersistentEntities(Film)
        grailsApplication.addArtefact(ControllerArtefactHandler.TYPE, MoviesController)
    }

    void 'the self link of a HAL collection targets the controller serving its elements'() {
        given: 'a Film, which MoviesController serves; no FilmController exists'
        def film = new Film(title: 'Metropolis')
        film.id = 1

        when:
        def result = render('''
            import groovy.transform.*
            import grails.plugin.json.view.hallinks.Film

            @Field Collection<Film> films

            json hal.render(films)
        ''', [films: [film]])

        then: 'the collection links to the same controller as its element'
        result.json._embedded[0]._links.self.href == 'http://localhost:8080/movies/show/1'
        result.json._links.self.href == 'http://localhost:8080/movies/index'
    }
}
