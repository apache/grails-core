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
package grails.rest.web

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

class ControllerXmlRenderingSpec extends Specification implements ControllerUnitTest<XmlResponseController> {

    void 'XML renderers can be recreated with a GSP locator already defined'() {
        given:
        def beanFactory = applicationContext.beanFactory
        assert beanFactory.containsBeanDefinition('groovyPageLocator')
        ['xmlRenderer', 'rendererRegistry', 'groovyPageLocator', 'mimeTypeResolver', 'mimeTypesConfiguration'].each {
            beanFactory.destroySingleton(it)
        }

        when:
        beanFactory.getBean(firstBean)
        beanFactory.preInstantiateSingletons()
        response.format = 'xml'
        controller.respond('ok')

        then:
        response.status == 200
        response.xml.name() == 'string'
        response.xml.text() == 'ok'

        where:
        firstBean << ['xmlRenderer', 'mimeTypeResolver']
    }

    void 'XML responses find GSP views registered after the renderer was created'() {
        given:
        assert applicationContext.beanFactory.containsSingleton('xmlRenderer')
        views['/xmlResponse/show.xml.gsp'] = '<message>${xmlResponseBody.message}</message>'
        request.addHeader('Accept', 'application/xml')
        webRequest.actionName = 'show'

        when:
        controller.show()

        then:
        model.xmlResponseBody.message == 'hello'
        view == 'show'
        response.contentAsString == ''
    }
}

@Artefact('Controller')
class XmlResponseController {
    static responseFormats = ['xml']

    def show() {
        respond new XmlResponseBody(message: 'hello')
    }
}

class XmlResponseBody {
    String message
}
