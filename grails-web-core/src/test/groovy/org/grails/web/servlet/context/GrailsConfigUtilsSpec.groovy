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
package org.grails.web.servlet.context

import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.support.GenericWebApplicationContext
import spock.lang.Specification

import grails.core.ApplicationAttributes
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.plugins.GrailsPluginManager
import grails.web.servlet.bootstrap.GrailsBootstrapClass
import org.grails.web.servlet.boostrap.BootstrapArtefactHandler

class GrailsConfigUtilsSpec extends Specification {

    void 'configureServletContextAttributes stores the application, plugin manager and both context attributes'() {
        given:
        MockServletContext servletContext = new MockServletContext()
        GrailsApplication application = Stub(GrailsApplication)
        GrailsPluginManager pluginManager = Stub(GrailsPluginManager)
        GenericWebApplicationContext webContext = new GenericWebApplicationContext()
        GenericWebApplicationContext parent = new GenericWebApplicationContext()
        webContext.parent = parent

        when:
        GrailsConfigUtils.configureServletContextAttributes(servletContext, application, pluginManager, webContext)

        then:
        servletContext.getAttribute(ApplicationAttributes.PLUGIN_MANAGER).is(pluginManager)
        servletContext.getAttribute(ApplicationAttributes.PARENT_APPLICATION_CONTEXT).is(parent)
        servletContext.getAttribute(GrailsApplication.APPLICATION_ID).is(application)
        servletContext.getAttribute(ApplicationAttributes.APPLICATION_CONTEXT).is(webContext)
        servletContext.getAttribute(org.springframework.web.context.WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE).is(webContext)
    }

    void 'executeGrailsBootstraps autowires, initializes and flushes the persistence interceptor around every bootstrap class'() {
        given:
        MockServletContext servletContext = new MockServletContext()
        GenericWebApplicationContext webContext = new GenericWebApplicationContext(servletContext)
        List events = []
        grails.persistence.support.PersistenceContextInterceptor interceptor = Stub(grails.persistence.support.PersistenceContextInterceptor) {
            init() >> { events << 'init' }
            flush() >> { events << 'flush' }
            destroy() >> { events << 'destroy' }
        }
        webContext.beanFactory.registerSingleton('interceptor', interceptor)
        webContext.refresh()
        GrailsBootstrapClass bootstrap = Stub(GrailsBootstrapClass) {
            getReferenceInstance() >> new Object()
            callInit() >> { events << 'callInit' }
        }
        GrailsApplication application = Stub(GrailsApplication) {
            getArtefacts(BootstrapArtefactHandler.TYPE) >> ([bootstrap] as grails.core.GrailsClass[])
        }
        GrailsPluginManager pluginManager = Stub(GrailsPluginManager)

        when:
        GrailsConfigUtils.executeGrailsBootstraps(application, webContext, servletContext, pluginManager)

        then:
        events == ['init', 'callInit', 'flush', 'destroy']
        servletContext.getAttribute(GrailsApplication.APPLICATION_ID).is(application)
    }

    void 'executeGrailsBootstraps still destroys the interceptor when a bootstrap fails'() {
        given:
        MockServletContext servletContext = new MockServletContext()
        GenericWebApplicationContext webContext = new GenericWebApplicationContext(servletContext)
        List events = []
        grails.persistence.support.PersistenceContextInterceptor interceptor = Stub(grails.persistence.support.PersistenceContextInterceptor) {
            destroy() >> { events << 'destroy' }
        }
        webContext.beanFactory.registerSingleton('interceptor', interceptor)
        webContext.refresh()
        GrailsBootstrapClass bootstrap = Stub(GrailsBootstrapClass) {
            getReferenceInstance() >> new Object()
            callInit() >> { throw new RuntimeException('boom') }
        }
        GrailsApplication application = Stub(GrailsApplication) {
            getArtefacts(BootstrapArtefactHandler.TYPE) >> ([bootstrap] as grails.core.GrailsClass[])
        }

        when:
        GrailsConfigUtils.executeGrailsBootstraps(application, webContext, servletContext, Stub(GrailsPluginManager))

        then:
        thrown(RuntimeException)
        events == ['destroy']
    }

    void 'isConfigTrue reads a boolean config property and always reports false for a non GrailsApplication'() {
        given:
        DefaultGrailsApplication application = new DefaultGrailsApplication()
        application.config = new org.grails.config.PropertySourcesConfig([myFlag: true])

        expect:
        GrailsConfigUtils.isConfigTrue(application, 'myFlag')
        !GrailsConfigUtils.isConfigTrue(application, 'otherFlag')
        !GrailsConfigUtils.isConfigTrue(new Object(), 'myFlag')
        !GrailsConfigUtils.isConfigTrue('not an application', 'myFlag')
    }

}
