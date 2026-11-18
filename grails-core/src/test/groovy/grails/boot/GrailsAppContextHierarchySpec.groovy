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

package grails.boot

import ch.qos.logback.classic.Level
import org.springframework.boot.WebApplicationType
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import grails.boot.config.GrailsAutoConfiguration
import grails.core.GrailsApplication
import grails.plugins.GrailsPluginManager
import grails.util.Environment
import grails.util.Holders
import org.apache.grails.core.testing.support.LogCapture

/**
 * Covers what {@link GrailsApp} does differently as a member of a context hierarchy: it runs the
 * plugin lifecycle only where the Grails application class is, says so when it leaves the lifecycle
 * out, and a context without a web server reports no address.
 */
@RestoreSystemProperties
class GrailsAppContextHierarchySpec extends Specification {

    ConfigurableApplicationContext context

    LogCapture logCapture

    void setup() {
        System.setProperty(Environment.KEY, Environment.TEST.getName())
    }

    void cleanup() {
        logCapture?.close()
        context?.close()
        Holders.clear()
        Environment.setInitializing(false)
    }

    private static GrailsApp application(Class<?>... sources) {
        GrailsApp app = new GrailsApp(sources)
        app.webApplicationType = WebApplicationType.NONE
        return app
    }

    /**
     * Captures what the application logs while it prepares its context. Spring Boot reinitialises
     * Logback the first time an application starts in a JVM, which detaches an appender attached
     * before the run, so the capture is attached once the environment, and with it the logging
     * system, has been prepared: after Boot's logging listener, and before the sources are weighed.
     */
    private GrailsApp capturingLog(GrailsApp app) {
        app.addListeners(new ApplicationListener<ApplicationEnvironmentPreparedEvent>() {
            @Override
            void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
                logCapture = logCapture ?: new LogCapture(GrailsApp, Level.INFO)
            }
        })
        return app
    }

    private boolean loggedSkippedLifecycle(Class<?> source) {
        logCapture.events.any {
            it.level == Level.INFO && it.formattedMessage.contains('no Grails application class') &&
                    it.formattedMessage.contains(source.name)
        }
    }

    void 'a standalone GrailsApp runs the plugin lifecycle whatever its sources are'() {
        given:
        GrailsApp app = capturingLog(application(HierarchyPlainConfig))

        expect: 'an application is standalone unless placed in a hierarchy'
        !app.contextHierarchyMember

        when:
        context = app.run()

        then:
        context.beanFactory.containsSingleton(GrailsApplication.APPLICATION_ID)
        context.beanFactory.containsSingleton(GrailsPluginManager.BEAN_NAME)

        and: 'nothing was left out, so nothing is said about it'
        !loggedSkippedLifecycle(HierarchyPlainConfig)
    }

    void 'a hierarchy member without the application class leaves the plugin lifecycle to the context that has it, and says so'() {
        given:
        GrailsApp app = capturingLog(application(HierarchyPlainConfig))
        app.contextHierarchyMember = true

        when:
        context = app.run()

        then: 'the context started as a plain Spring context'
        context.getBean(HierarchyPlainConfig.PlainMarker) != null
        !context.beanFactory.containsSingleton(GrailsApplication.APPLICATION_ID)
        !context.beanFactory.containsSingleton(GrailsPluginManager.BEAN_NAME)
        !Environment.isInitializing()

        and: 'the log names the sources that made it one, so the missing plugin beans can be traced back'
        loggedSkippedLifecycle(HierarchyPlainConfig)
    }

    void 'a hierarchy member with the application class among its sources runs the plugin lifecycle'() {
        given:
        GrailsApp app = capturingLog(application(HierarchyPlainConfig, HierarchyTestApplication))
        app.contextHierarchyMember = true

        when:
        context = app.run()

        then:
        context.beanFactory.containsSingleton(GrailsApplication.APPLICATION_ID)
        context.beanFactory.containsSingleton(GrailsPluginManager.BEAN_NAME)
        context.getBean(HierarchyPlainConfig.PlainMarker) != null
        !loggedSkippedLifecycle(HierarchyPlainConfig)
    }

    void 'no running address is reported for a context without a web server'() {
        given:
        PrintStream originalOut = System.out
        ByteArrayOutputStream captured = new ByteArrayOutputStream()
        System.setOut(new PrintStream(captured, true))

        when:
        context = application(HierarchyPlainConfig).run()

        then: 'nothing is listening, so there is no address to report'
        !captured.toString().contains('Grails application running at')

        cleanup:
        System.setOut(originalOut)
    }
}

@Configuration
class HierarchyPlainConfig {

    @Bean
    PlainMarker plainMarker() {
        new PlainMarker()
    }

    static class PlainMarker {
    }
}

class HierarchyTestApplication extends GrailsAutoConfiguration {
}
