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
package grails.artefact.controller.support

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.View
import spock.lang.Specification

import grails.util.GrailsWebMockUtil
import org.grails.web.servlet.mvc.ParameterCreationListener
import org.grails.web.servlet.view.CompositeViewResolver
import org.grails.web.util.GrailsApplicationAttributes

class ResponseRendererSpec extends Specification {

    MockServletContext servletContext = new MockServletContext()
    WebApplicationContext applicationContext = Mock(WebApplicationContext)

    void setup() {
        applicationContext.getBeansOfType(ParameterCreationListener) >> [:]
        servletContext.setAttribute(GrailsApplicationAttributes.APPLICATION_CONTEXT, applicationContext)
    }

    void cleanup() {
        RequestContextHolder.setRequestAttributes(null)
    }

    private void bindRequest() {
        GrailsWebMockUtil.bindMockWebRequest(applicationContext, new MockHttpServletRequest(servletContext),
                new MockHttpServletResponse())
    }

    void 'the composite view resolver is looked up once and reused for every template rendered'() {
        given:
        def view = Mock(View)
        def viewResolver = Mock(CompositeViewResolver)
        def controller = new TemplateRenderingController()

        when: 'the same controller renders two templates'
        bindRequest()
        controller.renderTemplate('first')
        bindRequest()
        controller.renderTemplate('second')

        then: 'the view resolver bean is only resolved from the context once'
        1 * applicationContext.getBean(CompositeViewResolver.BEAN_NAME, CompositeViewResolver) >> viewResolver

        and: 'but each template is still resolved and rendered'
        1 * viewResolver.resolveView('/templateRendering/_first', _) >> view
        1 * viewResolver.resolveView('/templateRendering/_second', _) >> view
        2 * view.render(_, _, _)
    }

    void 'different controllers in the same servlet context share one view resolver lookup'() {
        given:
        def view = Mock(View)
        def viewResolver = Mock(CompositeViewResolver)

        when: 'two different controller classes each render a template'
        bindRequest()
        new TemplateRenderingController().renderTemplate('first')
        bindRequest()
        new OtherTemplateRenderingController().renderTemplate('second')

        then: 'the bean is still resolved only once, because the lookup is not per controller'
        1 * applicationContext.getBean(CompositeViewResolver.BEAN_NAME, CompositeViewResolver) >> viewResolver

        and: 'each controller resolves its own template uri'
        1 * viewResolver.resolveView('/templateRendering/_first', _) >> view
        1 * viewResolver.resolveView('/otherTemplateRendering/_second', _) >> view
        2 * view.render(_, _, _)
    }
}

class TemplateRenderingController implements ResponseRenderer {

    void renderTemplate(String templateName) {
        render(template: templateName)
    }
}

class OtherTemplateRenderingController implements ResponseRenderer {

    void renderTemplate(String templateName) {
        render(template: templateName)
    }
}
