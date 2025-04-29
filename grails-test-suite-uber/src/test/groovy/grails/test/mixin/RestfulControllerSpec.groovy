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

import grails.gorm.transactions.Transactional
import grails.testing.gorm.DomainUnitTest
import grails.testing.web.controllers.ControllerUnitTest

import static org.springframework.http.HttpStatus.*
import grails.artefact.Artefact
import grails.persistence.Entity
import spock.lang.Specification

/**
 * @video Graeme Rocher
 */
class RestfulControllerSpec extends Specification implements ControllerUnitTest<VideoController>, DomainUnitTest<Video> {

    def populateValidParams(params) {
        assert params != null
    }

    void "Test the index action returns the correct model"() {

        when:"The index action is executed"
            controller.index()

        then:"The model is correct"
            assert !model.videoList
            assert model.videoCount == 0
    }

    void "Test the create action returns the correct model"() {
        when:"The create action is executed"
            controller.create()

        then:"The model is correctly created"
            model.video != null
    }

    void "Test the save action correctly persists an instance"() {

        when:"The save action is executed with an invalid instance"
            request.method = 'POST'
            controller.request.format = 'form'
            def video = new Video(title: '')
            video.validate()
            controller.save(video)

        then:"The create view is rendered again with the correct model"
            model.video != null
            view == 'create'

        when:"The save action is executed with a valid instance"
            response.reset()
            video = new Video(title: "Game of Thrones")
//            populateValidParams(params)
            controller.save(video)

        then:"A redirect is issued to the show action"
            response.redirectedUrl == '/video/show/1'
            controller.flash.message != null
            Video.count() == 1
    }

    void "Test that the show action returns the correct model"() {
        when:"The show action is executed with a null domain"
            controller.show(null)

        then:"A 404 error is returned"
            response.status == 404

        when:"A domain instance is passed to the show action"
    //        populateValidParams(params)
            def video = new Video(title: "Game of Thrones")
            controller.show(video)


        then:"A model is populated containing the domain instance"
            model.video == video
    }

    void "Test that the edit action returns the correct model"() {
        when:"The edit action is executed with a null domain"
            controller.edit(null)

        then:"A 404 error is returned"
            response.status == 404

        when:"A domain instance is passed to the edit action"
            //        populateValidParams(params)
            def video = new Video(title: "Game of Thrones")
            controller.edit(video)

        then:"A model is populated containing the domain instance"
            model.video == video
    }

    void "Test the update action performs an update on a valid domain instance"() {
        when:"Update is called for a domain instance that doesn't exist"
            request.method = 'PUT'
            controller.update(null)

        then:"A 404 error is returned"
            status == 404

        when:"An invalid domain instance is passed to the update action"
            response.reset()
            def video = new Video(title: '')
            video.validate()
            controller.update(video)

        then:"The edit view is rendered again with the invalid instance"
            view == 'edit'
            model.video == video

        when:"A valid domain instance is passed to the update action"
            response.reset()
            //populateValidParams(params)
            video = new Video(title: 'Game of Thrones').save(flush: true)
            controller.request.format = 'form'
            controller.update(video)

        then:"A redirect is issues to the show action"
            response.redirectedUrl == "/video/show/$video.id"
            flash.message != null
    }

    void "Test the patch action performs an update on a valid domain instance"() {
        when:"Patch is called for a domain instance that doesn't exist"
            request.method = 'PATCH'
            controller.patch(null)

        then:"A 404 error is returned"
            status == 404

        when:"An invalid domain instance is passed to the patch action"
            response.reset()
            def video = new Video(title: '')
            video.validate()
            controller.patch(video)

        then:"The edit view is rendered again with the invalid instance"
            view == 'edit'
            model.video == video

        when:"A valid domain instance is passed to the patch action"
            response.reset()
            //populateValidParams(params)
            video = new Video(title: 'Game of Thrones').save(flush: true)
            controller.request.format = 'form'
            controller.patch(video)

        then:"A redirect is issues to the show action"
            response.redirectedUrl == "/video/show/$video.id"
            flash.message != null
    }

    void "Test that the delete action deletes an instance if it exists"() {
        when:"The delete action is called for a null instance"
            request.method = 'DELETE'
            controller.delete(null)

        then:"A 404 is returned"
            status == 404

        when:"A domain instance is created"
            response.reset()
            //populateValidParams(params)
            def video = new Video(title: 'Game of Thrones').save(flush: true)

        then:"It exists"
            Video.count() == 1

        when:"The domain instance is passed to the delete action"
            controller.request.format = 'form'
            controller.delete(video)

        then:"The instance is deleted"
            Video.count() == 0
            response.redirectedUrl == '/video/index'
            flash.message != null
    }
}

@Entity
class Video {
    String title
    Integer numberOfMinutes
    static constraints = {
        title blank:false
        numberOfMinutes nullable: true
    }
}

@Transactional(readOnly = true)
@Artefact("Controller")
class VideoController {

    static allowedMethods = [save: "POST", update: "PUT", patch: "PATCH", delete: "DELETE"]

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond Video.list(params), model:[videoCount: Video.count()]
    }

    def show(Video video) {
        respond video
    }

    def create() {
        respond new Video(params)
    }

    @Transactional
    def save(Video video) {
        if (video == null) {
            notFound()
            return
        }

        if (video.hasErrors()) {
            respond video.errors, view:'create'
            return
        }

        video.save flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'video.label', default: 'Video'), video.id])
                redirect video
            }
            '*' { respond video, [status: CREATED] }
        }
    }

    def edit(Video video) {
        respond video
    }

    @Transactional
    def update(Video video) {
        if (video == null) {
            notFound()
            return
        }

        if (video.hasErrors()) {
            respond video.errors, view:'edit'
            return
        }

        video.save flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'Video.label', default: 'Video'), video.id])
                redirect video
            }
            '*'{ respond video, [status: OK] }
        }
    }

    @Transactional
    def patch(Video video) {
        if (video == null) {
            notFound()
            return
        }

        if (video.hasErrors()) {
            respond video.errors, view:'edit'
            return
        }

        video.save flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'Video.label', default: 'Video'), video.id])
                redirect video
            }
            '*'{ respond video, [status: OK] }
        }
    }

    @Transactional
    def delete(Video video) {
        if (video == null) {
            notFound()
            return
        }

        video.delete flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'Video.label', default: 'Video'), video.id])
                redirect action:"index", method:"GET"
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'video.label', default: 'Video'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
