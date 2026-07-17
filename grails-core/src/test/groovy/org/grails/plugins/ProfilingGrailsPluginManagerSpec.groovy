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
package org.grails.plugins

import grails.core.DefaultGrailsApplication
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.PluginDiscovery
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.StandardEnvironment
import spock.lang.Specification

class ProfilingGrailsPluginManagerSpec extends Specification {

    void "plugin loading emits INFO profiling messages instead of standard output"() {
        given:
        def application = new DefaultGrailsApplication()
        application.mainContext = new GenericApplicationContext()
        def discovery = new DefaultPluginDiscovery(new Class<?>[0])
        discovery.loadPluginsFromClasspath = false
        discovery.init(new StandardEnvironment())
        def manager = new ProfilingGrailsPluginManager(application, discovery)
        def originalOut = System.out
        def originalErr = System.err
        def capturedOut = new ByteArrayOutputStream()
        def capturedErr = new ByteArrayOutputStream()
        System.setOut(new PrintStream(capturedOut, true))
        System.setErr(new PrintStream(capturedErr, true))

        when:
        manager.loadPlugins()

        then:
        !capturedOut.toString().contains('Loading plugins')
        capturedErr.toString().contains('INFO grails.plugins.DefaultGrailsPluginManager - Loading plugins started')
        capturedErr.toString() ==~ /(?s).*INFO grails\.plugins\.DefaultGrailsPluginManager - Loading plugins took \d+.*/

        cleanup:
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    void "deprecated constructors log their warning in the historical category"() {
        given:
        def application = new DefaultGrailsApplication()
        def applicationContext = new GenericApplicationContext()
        def discovery = Mock(PluginDiscovery)
        applicationContext.beanFactory.registerSingleton(PluginDiscovery.BEAN_NAME, discovery)
        applicationContext.refresh()
        application.mainContext = applicationContext
        def originalErr = System.err
        def capturedErr = new ByteArrayOutputStream()
        System.setErr(new PrintStream(capturedErr, true))

        when:
        def manager = new ProfilingGrailsPluginManager(new Class<?>[0], application)

        then:
        manager
        1 * discovery.reset()
        1 * discovery.setPluginClasses(_)
        1 * discovery.init(_)
        capturedErr.toString().contains('WARN grails.plugins.DefaultGrailsPluginManager - Using deprecated DefaultGrailsPluginManager constructor.')

        cleanup:
        System.setErr(originalErr)
        applicationContext.close()
    }
}
