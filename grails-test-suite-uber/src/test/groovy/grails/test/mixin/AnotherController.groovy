package grails.test.mixin

import grails.artefact.Controller
import grails.converters.JSON
import grails.converters.XML
import grails.web.mime.MimeUtility
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.web.multipart.MultipartFile

/*
 * Copyright 2014 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author graemerocher
 */
@grails.web.Controller
class AnotherController  {

    def handleCommand( TestCommand test ) {

        if (test.hasErrors()) {
            render "Bad"
        }
        else {
            render "Good"
        }
    }
    def uploadFile = {
        assert request.method == 'POST'
        assert request.contentType == "multipart/form-data"
        MultipartFile file = request.getFile("myFile")
        file.transferTo(new File("/local/disk/myFile"))
    }

    def renderTemplateContents = {
        def contents = createLink(controller:"foo")
        render contents
    }
    def renderTemplateContentsViaNamespace = {
        def contents = g.render(template:"bar")

        render contents
    }
    def renderText = {
        render "good"
    }

    def redirectToController = {
        redirect(controller:"bar")
    }

    def renderView = {
        render(view:"foo")
    }

    def renderTemplate(String template) {
        render(template: template)
    }

    def renderXml = {
        render(contentType:"text/xml") {
            book(title:"Great")
        }
    }

    def renderJson = {
        render(contentType:"text/json") {
            book "Great"
        }
    }

    def renderAsJson = {
        render([foo:"bar"] as JSON)
    }

    def renderWithFormat = {
        def data = [foo:"bar"]
        withFormat {
            xml { render data as XML }
            html data
        }
    }

    def renderState = {
        render(contentType:"text/xml") {
            println params.foo
            println request.bar
            requestInfo {
                for (p in params) {
                    parameter(name:p.key, value:p.value)
                }
                request.each {
                    attribute(name:it.key, value:it.value)
                }
            }
        }
    }

    MessageSource messageSource
    @Autowired
    MimeUtility mimeUtility

    def renderMessage() {
        assert mimeUtility !=null
        assert grailsLinkGenerator != null
        render messageSource.getMessage("foo.bar", null, request.locale)
    }

    def renderWithForm() {
        withForm {
            render "Good"
        }.invalidToken {
            render "Bad"
        }
    }
}