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
package org.grails.web.servlet

import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification

import org.grails.web.servlet.view.CompositeViewResolver
import org.grails.web.util.GrailsApplicationAttributes

class DefaultGrailsApplicationAttributesSpec extends Specification {

    MockServletContext servletContext = new MockServletContext()

    WebApplicationContext applicationContext = Mock(WebApplicationContext)

    private DefaultGrailsApplicationAttributes createAttributes() {
        servletContext.setAttribute(GrailsApplicationAttributes.APPLICATION_CONTEXT, applicationContext)
        new DefaultGrailsApplicationAttributes(servletContext)
    }

    void 'the composite view resolver is resolved from the application context only once'() {
        given:
        CompositeViewResolver viewResolver = new CompositeViewResolver()
        DefaultGrailsApplicationAttributes attributes = createAttributes()

        when: 'the resolver is asked for repeatedly, as it is once per rendered template'
        List<CompositeViewResolver> resolved = [attributes.compositeViewResolver,
                                                attributes.compositeViewResolver,
                                                attributes.compositeViewResolver]

        then: 'the bean is only looked up on the first call'
        1 * applicationContext.getBean(CompositeViewResolver.BEAN_NAME, CompositeViewResolver) >> viewResolver

        and: 'and every call returns it'
        resolved.every { it.is(viewResolver) }
    }

    void 'a missing composite view resolver bean is reported rather than returned as null'() {
        given:
        DefaultGrailsApplicationAttributes attributes = createAttributes()
        applicationContext.getBean(CompositeViewResolver.BEAN_NAME, CompositeViewResolver) >> {
            throw new NoSuchBeanDefinitionException(CompositeViewResolver.BEAN_NAME)
        }

        when:
        attributes.getCompositeViewResolver()

        then:
        thrown(NoSuchBeanDefinitionException)
    }

    void 'a separate attributes instance for another servlet context resolves its own view resolver'() {
        given: 'two servlet contexts, each with its own application context and view resolver'
        CompositeViewResolver firstViewResolver = new CompositeViewResolver()
        CompositeViewResolver secondViewResolver = new CompositeViewResolver()
        WebApplicationContext secondApplicationContext = Mock(WebApplicationContext)
        secondApplicationContext.getBean(CompositeViewResolver.BEAN_NAME, CompositeViewResolver) >> secondViewResolver
        applicationContext.getBean(CompositeViewResolver.BEAN_NAME, CompositeViewResolver) >> firstViewResolver

        MockServletContext secondServletContext = new MockServletContext()
        secondServletContext.setAttribute(GrailsApplicationAttributes.APPLICATION_CONTEXT, secondApplicationContext)

        when:
        DefaultGrailsApplicationAttributes first = createAttributes()
        DefaultGrailsApplicationAttributes second = new DefaultGrailsApplicationAttributes(secondServletContext)

        then: 'neither is served the other context bean'
        first.compositeViewResolver.is(firstViewResolver)
        second.compositeViewResolver.is(secondViewResolver)
    }
}
