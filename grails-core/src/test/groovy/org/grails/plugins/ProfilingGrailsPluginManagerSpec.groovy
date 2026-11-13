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

import ch.qos.logback.classic.Level
import grails.core.DefaultGrailsApplication
import grails.plugins.DefaultGrailsPluginManager
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.PluginDiscovery
import org.apache.grails.core.testing.support.LogCapture
import org.grails.spring.DefaultRuntimeSpringConfiguration
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
        def capturedOut = new ByteArrayOutputStream()
        System.setOut(new PrintStream(capturedOut, true))
        def logCapture = new LogCapture(DefaultGrailsPluginManager, Level.INFO)

        when:
        manager.loadPlugins()

        then:
        !capturedOut.toString().contains('Loading plugins')
        logCapture.events.any {
            it.loggerName == DefaultGrailsPluginManager.name &&
                    it.level == Level.INFO &&
                    it.formattedMessage == 'Loading plugins started'
        }
        logCapture.events.any {
            it.loggerName == DefaultGrailsPluginManager.name &&
                    it.level == Level.INFO &&
                    it.formattedMessage ==~ /Loading plugins took \d+/
        }

        cleanup:
        System.setOut(originalOut)
        logCapture.close()
    }

    void "configuration phases emit INFO profiling messages for each plugin"() {
        given:
        def gcl = new GroovyClassLoader()
        def probeClass = gcl.parseClass('''
class ProfilingProbeGrailsPlugin {
    def version = "1.0.0"
}
''')
        def application = new DefaultGrailsApplication()
        def mainContext = new GenericApplicationContext()
        application.mainContext = mainContext
        def discovery = new DefaultPluginDiscovery(new Class<?>[]{probeClass})
        discovery.loadPluginsFromClasspath = false
        discovery.init(new StandardEnvironment())
        def manager = new ProfilingGrailsPluginManager(application, discovery)
        def originalOut = System.out
        def capturedOut = new ByteArrayOutputStream()
        System.setOut(new PrintStream(capturedOut, true))
        def logCapture = new LogCapture(DefaultGrailsPluginManager, Level.INFO)

        when:
        manager.loadPlugins()
        manager.applicationContext = mainContext
        manager.doArtefactConfiguration()
        manager.doRuntimeConfiguration(new DefaultRuntimeSpringConfiguration())
        manager.doDynamicMethods()
        manager.doPostProcessing(mainContext)

        then:
        !capturedOut.toString().contains('doWith')
        !capturedOut.toString().contains('doArtefactConfiguration')
        [
            'doArtefactConfiguration started',
            'doWithSpring started',
            'doWithSpring for plugin [profilingProbe] started',
            'doWithDynamicMethods started',
            'doWithDynamicMethods for plugin [profilingProbe] started',
            'doWithApplicationContext started',
            'doWithApplicationContext for plugin [profilingProbe] started'
        ].every { message ->
            logCapture.events.any {
                it.loggerName == DefaultGrailsPluginManager.name &&
                        it.level == Level.INFO &&
                        it.formattedMessage == message
            }
        }
        ['doArtefactConfiguration', 'doWithSpring', 'doWithDynamicMethods', 'doWithApplicationContext'].every { phase ->
            logCapture.events.any {
                it.loggerName == DefaultGrailsPluginManager.name &&
                        it.level == Level.INFO &&
                        it.formattedMessage ==~ /${phase} took \d+/
            } &&
                    (phase == 'doArtefactConfiguration' ||
                            logCapture.events.any {
                                it.loggerName == DefaultGrailsPluginManager.name &&
                                        it.level == Level.INFO &&
                                        it.formattedMessage ==~ /${phase} for plugin \[profilingProbe\] took \d+/
                            })
        }

        cleanup:
        System.setOut(originalOut)
        logCapture.close()
    }

    void "deprecated constructors log their warning in the historical category"() {
        given:
        def application = new DefaultGrailsApplication()
        def applicationContext = new GenericApplicationContext()
        def discovery = Mock(PluginDiscovery)
        applicationContext.beanFactory.registerSingleton(PluginDiscovery.BEAN_NAME, discovery)
        applicationContext.refresh()
        application.mainContext = applicationContext
        def logCapture = new LogCapture(DefaultGrailsPluginManager, Level.WARN)

        when:
        def manager = new ProfilingGrailsPluginManager(new Class<?>[0], application)

        then:
        manager
        1 * discovery.reset()
        1 * discovery.setPluginClasses(_)
        1 * discovery.init(_)
        logCapture.events.any {
            it.loggerName == DefaultGrailsPluginManager.name &&
                    it.level == Level.WARN &&
                    it.formattedMessage.startsWith('Using deprecated DefaultGrailsPluginManager constructor.')
        }

        cleanup:
        logCapture.close()
        applicationContext.close()
    }
}
