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
import grails.rest.RestfulController
import grails.testing.gorm.DomainUnitTest
import grails.testing.web.controllers.ControllerUnitTest
import org.springframework.http.HttpStatus
import spock.lang.Specification

/**
 */
class RestfulControllerSuperClassSpec extends Specification implements ControllerUnitTest<SecondVideoController>, DomainUnitTest<Video> {

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


    void "Test the save action returns the correct model, status and location"() {
        when:"The save action is executed"
            request.method = 'POST'
            controller.params['title'] = 'TestVideo'
            controller.save()

        then:"The model is created successfully"
            model.video != null
            response.status == HttpStatus.CREATED.value()
            response.getHeader('Location') != null
    }

    void "Test the update action returns the correct model, status and location"() {
        given: "An existing domain object and Restful controller"
            def video = new Video(title:'Existing').save()
        when:"The update action is executed on controller"
            request.method = 'PUT'
            controller.params['id']=video.id
            controller.params['title'] = 'Updated'
            controller.update()

        then:"The model is created successfully"
            model.video != null
            response.status == HttpStatus.OK.value()
            response.getHeader('Location') != null

    }

    void "Test the patch action returns the correct model, status and location"() {
        given: "An existing domain object and Restful controller"
            def video = new Video(title:'Existing').save()
        when:"The patch action is executed on controller"
            request.method = 'PATCH'
            controller.params['id']=video.id
            controller.params['title'] = 'Updated'
            controller.params.numberOfMinutes = '42'
            controller.patch()

        then:"The model is created successfully"
            model.video != null
            model.video.numberOfMinutes == 42
            response.status == HttpStatus.OK.value()
            response.getHeader('Location') != null

    }

    void "Test negative max param still only returns min size list"() {
        given: "save objects"
        101.times { new Video(title:"Existing + ${it}").save(failOnError: true) }

        when:"The index action is executed with param"
        controller.index(-1)

        then:"return model is 100"
        assert model.videoList.size() == 10
        assert model.videoCount == 101

        cleanup:
        Video.list().each { it.delete() }
    }


}

@Artefact("Controller")
class SecondVideoController extends RestfulController<Video> {
    SecondVideoController() {
        super(Video)
    }
}
