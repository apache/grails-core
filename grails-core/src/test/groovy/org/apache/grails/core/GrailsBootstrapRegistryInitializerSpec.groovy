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
package org.apache.grails.core

import grails.config.Settings
import grails.util.GrailsUtil
import org.apache.grails.core.testing.support.LogCapture
import org.grails.core.cfg.GroovyConfigPropertySourceLoader
import org.grails.exceptions.reporting.DefaultStackTraceFilterer
import org.grails.exceptions.reporting.StackTraceFilterer
import org.springframework.boot.bootstrap.DefaultBootstrapContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.io.FileSystemResource
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Verifies that {@link GrailsBootstrapRegistryInitializer} resolves a {@link StackTraceFilterer} from
 * the {@code ApplicationContext} environment -- honouring
 * {@link Settings#SETTING_LOGGING_STACKTRACE_FILTER_CLASS} and
 * {@link Settings#SETTING_LOG_FULL_STACKTRACE_ON_FILTER} -- promotes it as a singleton bean under
 * {@link GrailsBootstrapRegistryInitializer#STACK_TRACE_FILTERER_BEAN_NAME}, and installs it in
 * {@link GrailsUtil} before the context refreshes. Exercised through the public
 * {@link org.springframework.boot.bootstrap.BootstrapRegistryInitializer#initialize} entry point, the
 * same way Spring Boot invokes it during a real bootstrap.
 *
 * <p>Installation into {@code GrailsUtil} is asserted behaviourally -- by sanitizing through the public
 * {@link GrailsUtil#deepSanitize} and observing which filterer handled it -- rather than by inspecting
 * internal state.
 */
class GrailsBootstrapRegistryInitializerSpec extends Specification {

    @TempDir
    File configDir

    def cleanup() {
        GrailsUtil.initializeStackFilterer(new DefaultStackTraceFilterer())
    }

    def 'promotes a default StackTraceFilterer bean and installs it in GrailsUtil when no config is set'() {
        given:
        def context = contextWithProperties([:])

        when:
        closeBootstrapContext(context)

        then:
        promotedFilterer(context) instanceof DefaultStackTraceFilterer
    }

    def 'promotes the class configured via grails.logging.stackTraceFiltererClass as a class name'() {
        given: 'the string form, as written in application.yml or application.properties'
        def context = contextWithProperties([
                (Settings.SETTING_LOGGING_STACKTRACE_FILTER_CLASS): RecordingStackTraceFilterer.name
        ])

        when:
        closeBootstrapContext(context)
        GrailsUtil.deepSanitize(new RuntimeException('boom'))

        then: 'the configured class is both promoted and the one GrailsUtil sanitizes through'
        def bean = promotedFilterer(context)
        bean instanceof RecordingStackTraceFilterer
        bean.recursiveCalls == 1
    }

    def 'promotes the class configured via grails.logging.stackTraceFiltererClass as a Class literal'() {
        given: 'the Class form -- application.groovy is loaded into a property source that preserves value types'
        def context = contextWithProperties([
                (Settings.SETTING_LOGGING_STACKTRACE_FILTER_CLASS): RecordingStackTraceFilterer
        ])

        when:
        closeBootstrapContext(context)
        GrailsUtil.deepSanitize(new RuntimeException('boom'))

        then: 'the configured class is both promoted and the one GrailsUtil sanitizes through'
        def bean = promotedFilterer(context)
        bean instanceof RecordingStackTraceFilterer
        bean.recursiveCalls == 1
    }

    def 'promotes the class configured via a real application.groovy Class literal'() {
        given: 'an application.groovy parsed by the real ConfigSlurper -> NavigableMap -> NavigableMapPropertySource pipeline, not a synthetic MapPropertySource'
        def configFile = new File(configDir, 'application.groovy')
        configFile.text = """
            grails.logging.stackTraceFiltererClass = ${RecordingStackTraceFilterer.name}
        """
        def propertySource = new GroovyConfigPropertySourceLoader()
                .load('application.groovy', new FileSystemResource(configFile))
                .first()
        def context = new GenericApplicationContext()
        context.environment.propertySources.addFirst(propertySource)

        when:
        closeBootstrapContext(context)
        GrailsUtil.deepSanitize(new RuntimeException('boom'))

        then: 'the Class literal survives the real config-loading pipeline and is both promoted and used'
        def bean = promotedFilterer(context)
        bean instanceof RecordingStackTraceFilterer
        bean.recursiveCalls == 1
    }

    def 'resolves the configured class name against the ApplicationContext ClassLoader'() {
        given: 'a filterer only the context ClassLoader can see, standing in for the devtools RestartClassLoader split'
        def applicationLoader = new GroovyClassLoader(GrailsBootstrapRegistryInitializerSpec.classLoader)
        applicationLoader.parseClass('''
            package dynamic
            import org.grails.exceptions.reporting.StackTraceFilterer
            class ApplicationStackTraceFilterer implements StackTraceFilterer {
                Throwable filter(Throwable source) { source }
                Throwable filter(Throwable source, boolean recursive) { source }
                void addInternalPackage(String name) {}
                void setCutOffPackage(String cutOffPackage) {}
                void setShouldFilter(boolean shouldFilter) {}
            }
        ''')

        when: "grails-core's own ClassLoader tries to resolve it"
        GrailsBootstrapRegistryInitializer.classLoader.loadClass('dynamic.ApplicationStackTraceFilterer')

        then: 'it genuinely cannot -- the pre-fix lookup path'
        thrown(ClassNotFoundException)

        when:
        def context = contextWithProperties([
                (Settings.SETTING_LOGGING_STACKTRACE_FILTER_CLASS): 'dynamic.ApplicationStackTraceFilterer'
        ])
        context.classLoader = applicationLoader
        closeBootstrapContext(context)

        then: "the context's own ClassLoader resolves it anyway"
        promotedFilterer(context).class.name == 'dynamic.ApplicationStackTraceFilterer'
    }

    def 'falls back to the default filterer when the configured class cannot be loaded'() {
        given:
        def context = contextWithProperties([
                (Settings.SETTING_LOGGING_STACKTRACE_FILTER_CLASS): 'not.a.real.ClassName'
        ])

        when:
        closeBootstrapContext(context)

        then:
        noExceptionThrown()
        promotedFilterer(context) instanceof DefaultStackTraceFilterer
    }

    def 'falls back to the default filterer when the configured class does not implement StackTraceFilterer'() {
        given:
        def context = contextWithProperties([
                (Settings.SETTING_LOGGING_STACKTRACE_FILTER_CLASS): String.name
        ])

        when:
        closeBootstrapContext(context)

        then:
        noExceptionThrown()
        promotedFilterer(context) instanceof DefaultStackTraceFilterer
    }

    def 'falls back to the default filterer when the configured Class literal does not implement StackTraceFilterer'() {
        given:
        def context = contextWithProperties([
                (Settings.SETTING_LOGGING_STACKTRACE_FILTER_CLASS): String
        ])

        when:
        closeBootstrapContext(context)

        then:
        noExceptionThrown()
        promotedFilterer(context) instanceof DefaultStackTraceFilterer
    }

    def 'falls back to the default filterer when the configured value is neither a Class nor a String'() {
        given: 'a shape neither branch of resolveFiltererClass handles -- e.g. a YAML list or a boolean typo'
        def context = contextWithProperties([
                (Settings.SETTING_LOGGING_STACKTRACE_FILTER_CLASS): ['not', 'a', 'class', 'or', 'a', 'string']
        ])

        when:
        closeBootstrapContext(context)

        then: 'the mismatched type degrades to the default rather than being silently ignored or failing the bootstrap'
        noExceptionThrown()
        promotedFilterer(context) instanceof DefaultStackTraceFilterer
    }

    def 'a logFullStackTraceOnFilter value that is not a boolean does not fail the bootstrap'() {
        given:
        def context = contextWithProperties([
                (Settings.SETTING_LOG_FULL_STACKTRACE_ON_FILTER): 'yes-please'
        ])

        when:
        closeBootstrapContext(context)

        then: 'a misconfigured filterer degrades to the default rather than taking the application down'
        noExceptionThrown()
        promotedFilterer(context) instanceof DefaultStackTraceFilterer
    }

    def 'propagates logFullStackTraceOnFilter=false to the promoted DefaultStackTraceFilterer'() {
        given:
        def context = contextWithProperties([
                (Settings.SETTING_LOG_FULL_STACKTRACE_ON_FILTER): 'false'
        ])
        def logCapture = new LogCapture(DefaultStackTraceFilterer.STACK_LOG_NAME)

        when:
        closeBootstrapContext(context)
        GrailsUtil.deepSanitize(exceptionWithApplicationFrame())

        then: "no 'Full Stack Trace:' entry is emitted"
        logCapture.events.every { !it.formattedMessage.contains(StackTraceFilterer.FULL_STACK_TRACE_MESSAGE) }

        cleanup:
        logCapture.close()
    }

    def 'defaults logFullStackTraceOnFilter to true on the promoted DefaultStackTraceFilterer'() {
        given:
        def context = contextWithProperties([:])
        def logCapture = new LogCapture(DefaultStackTraceFilterer.STACK_LOG_NAME)

        when:
        closeBootstrapContext(context)
        GrailsUtil.deepSanitize(exceptionWithApplicationFrame())

        then: 'the positive control proving the negative case above is meaningful'
        logCapture.events.any { it.formattedMessage.contains(StackTraceFilterer.FULL_STACK_TRACE_MESSAGE) }

        cleanup:
        logCapture.close()
    }

    private static StackTraceFilterer promotedFilterer(GenericApplicationContext context) {
        context.getBeanFactory().getBean(
                GrailsBootstrapRegistryInitializer.STACK_TRACE_FILTERER_BEAN_NAME, StackTraceFilterer)
    }

    private static void closeBootstrapContext(GenericApplicationContext context) {
        def registry = new DefaultBootstrapContext()
        new GrailsBootstrapRegistryInitializer().initialize(registry)
        registry.close(context)
    }

    private static GenericApplicationContext contextWithProperties(Map<String, Object> properties) {
        def context = new GenericApplicationContext()
        if (properties) {
            context.environment.propertySources.addFirst(new MapPropertySource('test', properties))
        }
        context
    }

    private static RuntimeException exceptionWithApplicationFrame() {
        def exception = new RuntimeException('boom')
        exception.stackTrace = [
                new StackTraceElement('test.FooController', 'show', 'FooController.groovy', 6),
                new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 580)
        ] as StackTraceElement[]
        exception
    }

    static class RecordingStackTraceFilterer implements StackTraceFilterer {
        int recursiveCalls = 0

        Throwable filter(Throwable source) { source }
        Throwable filter(Throwable source, boolean recursive) { recursiveCalls++; source }
        void addInternalPackage(String name) {}
        void setCutOffPackage(String cutOffPackage) {}
        void setShouldFilter(boolean shouldFilter) {}
    }
}
