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
package org.grails.plugins.web

import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.DefaultListableBeanFactory

import grails.core.DefaultGrailsApplication
import grails.spring.BeanBuilder
import spock.lang.Specification

/**
 * Covers when the manifest of pages compiled at build time is attached to the page locator.
 *
 * <p>Attached, the locator reads its pages from what was compiled. That is right for a deployed
 * application and for one whose code is being written out -- an image cannot compile a page at all
 * -- and wrong for a project on disk, where editing a page has to take effect without a restart.
 * A manifest left on the classpath by an earlier build, or shipped inside a plugin, must not turn
 * that off.</p>
 */
class GroovyPagesGrailsPluginPrecompiledSpec extends Specification {

    private static final String LOCATOR = 'groovyPageLocator'

    private AbstractBeanDefinition locatorFrom(boolean developmentMode) {
        def application = new DefaultGrailsApplication()
        application.initialise()
        def plugin = new Plugin(developmentMode: developmentMode, grailsApplication: application)
        def beanFactory = new DefaultListableBeanFactory()
        def builder = new BeanBuilder(null, null, getClass().classLoader)
        builder.beans(plugin.doWithSpring())
        builder.registerBeans(beanFactory)
        (AbstractBeanDefinition) beanFactory.getBeanDefinition(LOCATOR)
    }

    private static boolean readsPrecompiledPages(AbstractBeanDefinition locator) {
        locator.propertyValues.contains('precompiledGspMap')
    }

    void 'a deployed application reads the pages compiled at build time'() {
        expect:
            readsPrecompiledPages(locatorFrom(false))
    }

    void 'a project on disk does not, whatever manifest is on its classpath'() {
        expect: 'a manifest left by an earlier build, or shipped inside a plugin, would otherwise ' +
                'stop a page being reloaded as it is edited'
            !readsPrecompiledPages(locatorFrom(true))
    }

    /** A plugin told what its surroundings are, which only a run can otherwise answer. */
    static class Plugin extends GroovyPagesGrailsPlugin {

        boolean developmentMode

        @Override
        protected boolean isDevelopmentMode() {
            developmentMode
        }
    }
}
