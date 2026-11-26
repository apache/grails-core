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
package grails.plugins

import grails.core.DefaultGrailsApplication
import grails.plugins.exceptions.PluginException
import grails.spring.BeanBuilder
import org.grails.plugins.DefaultGrailsPlugin
import org.grails.spring.DefaultRuntimeSpringConfiguration
import org.grails.spring.RuntimeSpringConfiguration
import org.springframework.beans.propertyeditors.ClassEditor
import spock.lang.Specification

/**
 * Tests the method-based {@link Plugin#doWithSpring(BeanBuilder)} hook that complements the closure-returning
 * {@link Plugin#doWithSpring()} hook.
 */
class PluginDoWithSpringMethodSpec extends Specification {

    private static final String IMPORTS = '''
        import grails.plugins.Plugin
        import grails.plugins.metadata.GrailsPluginDescriptor
        import grails.spring.BeanBuilder
        import org.springframework.beans.propertyeditors.ClassEditor
    '''

    private static final String METHOD_HOOK = IMPORTS + '''
        @GrailsPluginDescriptor
        class MethodHookGrailsPlugin extends Plugin {
            @Override
            void doWithSpring(BeanBuilder beans) {
                beans.methodEditor(ClassEditor, grailsApplication.classLoader)
            }
        }
    '''

    private static final String BOTH_HOOKS = IMPORTS + '''
        @GrailsPluginDescriptor
        class BothHooksGrailsPlugin extends Plugin {
            @Override
            Closure doWithSpring() {
                { ->
                    classEditor(ClassEditor, application.classLoader)
                }
            }

            @Override
            void doWithSpring(BeanBuilder beans) {
                beans.customEditor(ClassEditor, grailsApplication.classLoader)
            }
        }
    '''

    private static final String CLOSURE_HOOK = IMPORTS + '''
        @GrailsPluginDescriptor
        class ClosureHookGrailsPlugin extends Plugin {
            @Override
            Closure doWithSpring() {
                { ->
                    classEditor(ClassEditor, application.classLoader)
                }
            }
        }
    '''

    /** Plugins compiled at run time, so that the test class path does not list them as plugins of its own. */
    private static Class<?> plugin(String source) {
        new GroovyClassLoader(PluginDoWithSpringMethodSpec.classLoader).parseClass(source)
    }

    void "a Plugin subclass can register beans by overriding doWithSpring(BeanBuilder)"() {
        given: "a plugin that registers a bean through the method-based hook"
            def app = new DefaultGrailsApplication()
            def plugin = new MethodHookPlugin(grailsApplication: app)
            def bb = new BeanBuilder()

        when: "the method-based hook is invoked with the builder"
            plugin.doWithSpring(bb)
            def ctx = bb.createApplicationContext()

        then: "the bean registered against the builder is present"
            ctx.containsBean('methodEditor')
    }

    void "the base Plugin doWithSpring(BeanBuilder) is a no-op"() {
        given: "a plugin that overrides neither hook"
            def app = new DefaultGrailsApplication()
            def plugin = new NoOpPlugin(grailsApplication: app)
            def bb = new BeanBuilder()

        when: "the method-based hook is invoked"
            plugin.doWithSpring(bb)
            def ctx = bb.createApplicationContext()

        then: "no beans are registered by the plugin and the closure hook still returns null"
            plugin.doWithSpring() == null
            ctx.beanDefinitionNames.length == 0
    }

    void "DefaultGrailsPlugin invokes the method-based doWithSpring(BeanBuilder) hook"() {
        given: "a plugin descriptor that uses only the method-based hook"
            def app = new DefaultGrailsApplication()
            def plugin = new DefaultGrailsPlugin(plugin(METHOD_HOOK), app)
            RuntimeSpringConfiguration springConfig = new DefaultRuntimeSpringConfiguration()

        when: "runtime configuration is performed"
            plugin.doWithRuntimeConfiguration(springConfig)
            def ctx = springConfig.applicationContext

        then: "the bean registered via the method hook is present"
            ctx.containsBean('methodEditor')
    }

    void "DefaultGrailsPlugin throws when a plugin defines both doWithSpring hooks"() {
        given: "a plugin descriptor that overrides both hooks"
            def app = new DefaultGrailsApplication()
            def plugin = new DefaultGrailsPlugin(plugin(BOTH_HOOKS), app)
            RuntimeSpringConfiguration springConfig = new DefaultRuntimeSpringConfiguration()

        when: "runtime configuration is performed"
            plugin.doWithRuntimeConfiguration(springConfig)

        then: "a PluginException is thrown identifying the conflicting hooks"
            PluginException e = thrown()
            e.message.contains('doWithSpring()')
            e.message.contains('doWithSpring(BeanBuilder)')
    }

    void "DefaultGrailsPlugin supports the closure-returning doWithSpring hook"() {
        given: "a plugin descriptor that overrides only the closure-returning hook"
            def app = new DefaultGrailsApplication()
            def plugin = new DefaultGrailsPlugin(plugin(CLOSURE_HOOK), app)
            RuntimeSpringConfiguration springConfig = new DefaultRuntimeSpringConfiguration()

        when: "runtime configuration is performed"
            plugin.doWithRuntimeConfiguration(springConfig)
            def ctx = springConfig.applicationContext

        then: "the bean registered via the closure hook is present"
            ctx.containsBean('classEditor')
    }
}

class MethodHookPlugin extends Plugin {
    @Override
    void doWithSpring(BeanBuilder beans) {
        beans.methodEditor(ClassEditor, grailsApplication.classLoader)
    }
}

class NoOpPlugin extends Plugin {
}
