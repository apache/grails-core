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

import grails.artefact.Controller
import grails.artefact.controller.RestResponder
import grails.artefact.controller.support.ResponseRenderer
import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import grails.web.api.WebAttributes
import grails.web.databinding.DataBinder

import org.codehaus.groovy.control.CompilerConfiguration
import spock.lang.Shared
import spock.lang.Specification
/**
 * @author Graeme Rocher
 */
//TODO: Replace ControllerUnitTest<Object> with ControllerUnitTest once update to Groovy 3.0.7
class ResourceAnnotationRestfulControllerSpec extends Specification implements DataTest, ControllerUnitTest<Object> {

    @Shared Class domainClass
    @Shared Class controllerClass
    def controller

    void setupSpec() {
        def gcl  = new GroovyClassLoader(Thread.currentThread().getContextClassLoader(), createCompilerConfiguration())
        gcl.parseClass('''
import grails.persistence.*
import grails.rest.*

@Entity
@Resource(formats=['html', 'xml'], uri="/videos")
class Video {
    String title
    static constraints = {
        title blank:false
    }
}
''')
        domainClass = gcl.loadClass('Video')
        controllerClass = gcl.loadClass('VideoController')
        mockDomain(domainClass)
    }

    protected CompilerConfiguration createCompilerConfiguration() {
        CompilerConfiguration compilerConfig = new CompilerConfiguration()
        File targetDir = new File(System.getProperty("java.io.tmpdir"), "classes_" + this.getClass().getSimpleName())
        if(targetDir.exists()) {
            targetDir.deleteDir()
        }
        targetDir.mkdirs()
        // keep compiled bytecode in targetDirectory for debugging purposes 
        compilerConfig.targetDirectory = targetDir
        return compilerConfig
    }

    def setup() {
        mockController(controllerClass)
        controller = applicationContext.getBean(controllerClass)
    }
    
    void 'Test that the generated controller implements the expected traits'() {
        expect:
        Controller.isAssignableFrom controllerClass
        WebAttributes.isAssignableFrom controllerClass
        ResponseRenderer.isAssignableFrom controllerClass
        DataBinder.isAssignableFrom controllerClass
        RestResponder.isAssignableFrom controllerClass
    }
    
    void "Test the index action returns the correct model"() {

        when:"The index action is executed"
            controller.index()

        then:"The model is correct"
            !model.videoList
            model.videoCount == 0
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
            params.title = ''
            controller.save()

        then:"The create view is rendered again with the correct model"
            model.video != null
            view == 'create'

        when:"The save action is executed with a valid instance"
            response.reset()
            params.title = "Game of Thrones"
            controller.save()

        then:"A redirect is issued to the show action"
            response.status == 201
            domainClass.count() == 1
    }

    void "Test that the show action returns the correct model"() {
        when:"The show action is executed with a null domain"
            controller.show()

        then:"A 404 error is returned"
            response.status == 404

        when:"A domain instance is passed to the show action"
            def video = domainClass.newInstance(title: "Game of Thrones")
            video.save(flush:true)
            params.id = video.id
            controller.show()


        then:"A model is populated containing the domain instance"
            model.video == video
    }

    void "Test that the edit action returns the correct model"() {
        when:"The edit action is executed with a null domain"
            controller.edit()

        then:"A 404 error is returned"
            response.status == 404

        when:"A domain instance is passed to the edit action"
            def video = domainClass.newInstance(title: "Game of Thrones")
            video.save(flush:true)
            params.id = video.id
            controller.edit()

        then:"A model is populated containing the domain instance"
            model.video == video
    }


    void "Test the update action performs an update on a valid domain instance"() {
        when:"Update is called for a domain instance that doesn't exist"
            request.method = 'PUT'
            controller.update()

        then:"A 404 error is returned"
            status == 404

        when:"A valid domain instance is passed to the update action"
            def video = domainClass.newInstance(title: 'Title').save(flush: true)
            response.reset()
            request.contentType = FORM_CONTENT_TYPE
            params.id = video.id
            params.title = 'Game of Thrones'
            controller.update()

        then:"A redirect is issues to the show action"
            response.status == 200
            domainClass.get(video.id).title == 'Game of Thrones'

    }

    void "Test the patch action performs an update on a valid domain instance"() {
        when:"Patch is called for a domain instance that doesn't exist"
            request.method = 'PATCH'
            controller.patch()

        then:"A 404 error is returned"
            status == 404

        when:"A valid domain instance is passed to the update action"
            def video = domainClass.newInstance(title: 'Title').save(flush: true)
            response.reset()
            request.contentType = FORM_CONTENT_TYPE
            params.id = video.id
            params.title = 'Game of Thrones'
            controller.patch()

        then:"A redirect is issues to the show action"
            response.status == 200
            domainClass.get(video.id).title == 'Game of Thrones'

    }

    void "Test that the delete action deletes an instance if it exists"() {
        when:"The delete action is called for a null instance"
            request.method = 'DELETE'
            controller.delete()

        then:"A 404 is returned"
            status == 404

        when:"A domain instance is created"
            response.reset()
            def video = domainClass.newInstance(title: 'Game of Thrones').save(flush: true)

        then:"It exists"
            domainClass.count() == 1

        when:"The domain instance is passed to the delete action"
            params.id = video.id
            controller.delete()

        then:"The instance is deleted"
            response.status == 204
            domainClass.count() == 0

    }
}
