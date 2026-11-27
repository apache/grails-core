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
package org.grails.plugins.xml

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import grails.artefact.Artefact
import grails.rest.render.RenderContext
import grails.rest.render.Renderer
import grails.rest.render.RendererRegistry
import grails.web.mime.MimeType
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

class UnmanagedControllerXmlSpec extends Specification implements ControllerUnitTest<UnmanagedXmlController> {

    void 'a controller Spring did not create responds in XML with the renderers of the application'() {
        given: 'a controller created with new, which Spring does not autowire'
        def controller = new UnmanagedXmlController()
        request.addHeader('Accept', 'application/xml')

        when:
        controller.respond(new UnmanagedXmlBody(title: 'Grails'))

        then:
        response.status == 200
        response.contentType.startsWith('application/xml')
        response.xml.title.text() == 'Grails'
    }
}

class UnmanagedControllerSecondRegistrySpec extends Specification implements ControllerUnitTest<UnmanagedXmlController> {

    @Configuration
    static class RegistryConfiguration {

        @Bean
        RendererRegistry otherRendererRegistry() {
            new RenderingNothingRegistry()
        }

        // an HTML renderer of the body, which the registry respond writes with has, and the other has not
        @Bean
        Renderer<UnmanagedXmlBody> bodyHtmlRenderer() {
            new BodyHtmlRenderer()
        }
    }

    void 'a controller Spring did not create responds with the registry of the application where it has another too'() {
        given: 'a controller created with new, and a second registry, which renders nothing, neither of them primary'
        def controller = new UnmanagedXmlController()
        request.addHeader('Accept', 'application/xml')
        assert applicationContext.getBeansOfType(RendererRegistry).keySet() == ['rendererRegistry', 'otherRendererRegistry'] as Set

        when:
        controller.respond(new UnmanagedXmlBody(title: 'Grails'))

        then: 'the one an autowired controller is given, by its name'
        response.status == 200
        response.contentType.startsWith('application/xml')
        response.xml.title.text() == 'Grails'
    }

    void "the XML view of an action is given to the HTML renderer of the registry respond writes with, where the application has a second registry"() {
        given: 'a view of the action in XML'
        views['/unmanagedXml/show.xml.gsp'] = '<book><title>${unmanagedXmlBody.title}</title></book>'
        webRequest.actionName = 'show'
        request.addHeader('Accept', 'application/xml')

        when:
        controller.respond(new UnmanagedXmlBody(title: 'Grails'))

        then: 'the HTML renderer of that registry, which the other has not, writes the response in place of the view'
        response.status == 200
        response.contentAsString == 'the view of Grails'
    }
}

@Artefact('Controller')
class UnmanagedXmlController { }

class RenderingNothingRegistry implements RendererRegistry {

    @Override
    <T> void addRenderer(Renderer<T> renderer) {
    }

    @Override
    void addDefaultRenderer(Renderer<Object> renderer) {
    }

    @Override
    void addContainerRenderer(Class objectType, Renderer renderer) {
    }

    @Override
    <T> Renderer<T> findRenderer(MimeType contentType, T object) {
        null
    }

    @Override
    <C, T> Renderer<C> findContainerRenderer(MimeType contentType, Class<C> containerType, T object) {
        null
    }

    @Override
    boolean isContainerType(Class<?> aClass) {
        false
    }
}

class BodyHtmlRenderer implements Renderer<UnmanagedXmlBody> {

    @Override
    Class<UnmanagedXmlBody> getTargetType() {
        UnmanagedXmlBody
    }

    @Override
    MimeType[] getMimeTypes() {
        [MimeType.HTML] as MimeType[]
    }

    @Override
    void render(Object object, RenderContext context) {
        context.writer.write("the view of ${((UnmanagedXmlBody) object).title}")
    }
}

class UnmanagedXmlBody {
    String title
}
