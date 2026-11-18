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
package grails.gsp.boot

import org.sitemesh.autoconfigure.SiteMeshViewResolverAutoConfiguration

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.core.io.FileSystemResourceLoader
import org.springframework.mock.web.MockServletContext
import org.springframework.web.servlet.View
import org.springframework.web.servlet.ViewResolver
import org.springframework.web.servlet.view.AbstractUrlBasedView

import spock.lang.Specification
import spock.lang.TempDir

/**
 * Which views the GSP view resolver of a Spring Boot application answers for when no template has the name.
 *
 * <p>Spring Boot renders its error page through the view named {@code error}. Answering for that name with a
 * JSP that does not exist forwards the error back to the URL it is being handled at, and the container refuses
 * the loop; the gsp-spring-boot example's {@code ErrorPageTest} covers that end to end.
 */
class StandaloneGroovyPageViewResolverSpec extends Specification {

    @TempDir
    File documentRoot

    void 'a view with no template resolves to the existing JSP #viewName'() {
        given: 'a JSP in the servlet context, as src/main/webapp puts one there'
        new File(documentRoot, 'form.jsp').text = '<p>rendered by JSP</p>'

        expect:
        runner().run { context ->
            View view = context.getBean('gspViewResolver', ViewResolver).resolveViewName(viewName, Locale.ROOT)
            assert view instanceof AbstractUrlBasedView
            assert ((AbstractUrlBasedView) view).url == viewName
        }

        where:
        viewName << ['form.jsp', '/form.jsp']
    }

    void 'a missing view #viewName is left to the view resolvers after this one'() {
        expect: 'nothing is answered for the name Boot renders its error page through, so that page is reached'
        runner().run { context ->
            assert context.getBean('gspViewResolver', ViewResolver).resolveViewName(viewName, Locale.ROOT) == null
        }

        where:
        viewName << ['error', '/error', 'missing.jsp', '/missing.jsp']
    }

    void 'JSP fallback can be disabled even when the JSP exists'() {
        given:
        new File(documentRoot, 'form.jsp').text = '<p>rendered by JSP</p>'

        expect:
        runner().withPropertyValues('spring.gsp.jspEnabled=false').run { context ->
            assert context.getBean('gspViewResolver', ViewResolver).resolveViewName('form.jsp', Locale.ROOT) == null
        }
    }

    private WebApplicationContextRunner runner() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(GspAutoConfiguration, SiteMeshViewResolverAutoConfiguration))
                .withPropertyValues('spring.gsp.templateRoots=classpath:/templates', 'spring.gsp.view.cacheTimeout=0',
                        'sitemesh.viewResolver.wrapMode=delegate')
                .withInitializer { context ->
                    context.servletContext = new MockServletContext(
                            "file:${documentRoot.absolutePath}", new FileSystemResourceLoader())
                }
    }

}
