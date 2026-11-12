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
package org.grails.web.mapping.mvc

import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.support.GenericApplicationContext
import org.springframework.mock.web.MockHttpServletRequest

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.core.GrailsControllerClass
import grails.plugins.DefaultGrailsPluginManager
import grails.plugins.GrailsPluginManager
import grails.util.GrailsWebMockUtil
import grails.web.mapping.AbstractUrlMappingsSpec
import grails.web.mapping.UrlMappingsHolder
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.grails.core.DefaultGrailsControllerClass
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.web.mapping.UrlMappingsHolderFactoryBean

/**
 * A controller registered again while the application runs, as the development watch does when a class is reloaded.
 * Each version of a controller is compiled by a class loader of its own, so the versions share a class name as the
 * versions of a reloaded class do.
 */
class ReloadedControllerMappingSpec extends AbstractUrlMappingsSpec {

    void 'an action added to a controller registered again is matched'() {
        given:
        def before = controller('reload.ReloadController', ['index'])
        def mappings = mappingsFor(before)
        def after = controller('reload.ReloadController', ['index', 'added'])

        when:
        mappings.registerController(after)

        then:
        controllerFor(mappings, '/reload/added').is(after.clazz)
        controllerFor(mappings, '/reload/index').is(after.clazz)
    }

    void 'an action removed from a controller registered again is no longer matched'() {
        given:
        def before = controller('reload.ReloadController', ['index', 'removed'])
        def mappings = mappingsFor(before)
        def after = controller('reload.ReloadController', ['index'])

        expect:
        controllerFor(mappings, '/reload/removed').is(before.clazz)

        when:
        mappings.registerController(after)

        then:
        controllerFor(mappings, '/reload/removed') == null
        controllerFor(mappings, '/reload/index').is(after.clazz)
    }

    void 'an action removed from a controller registered again goes to a namespaced controller that has it'() {
        given:
        def plain = controller('reload.ReloadController', ['index', 'show'])
        def namespaced = controller('admin.ReloadController', ['index', 'show'], 'admin')
        def mappings = mappingsFor(plain, namespaced)

        expect:
        controllerFor(mappings, '/reload/show').is(plain.clazz)

        when:
        mappings.registerController(controller('reload.ReloadController', ['index']))

        then:
        controllerFor(mappings, '/reload/show').is(namespaced.clazz)
    }

    void 'an action removed from a namespaced controller registered again goes to another namespaced controller that has it'() {
        given:
        def admin = controller('admin.ReloadController', ['index', 'show'], 'admin')
        def staff = controller('staff.ReloadController', ['index', 'show'], 'staff')
        def mappings = mappingsFor(admin, staff)

        expect:
        controllerFor(mappings, '/reload/show').is(staff.clazz)

        when:
        mappings.registerController(controller('staff.ReloadController', ['index'], 'staff'))

        then:
        controllerFor(mappings, '/reload/show').is(admin.clazz)
    }

    void 'a controller registered again leaves the mappings a start with its new version builds'() {
        given:
        def plain = controller('reload.ReloadController', ['index', 'show'])
        def admin = controller('admin.ReloadController', ['index', 'show', 'edit'], 'admin')
        def staff = controller('staff.ReloadController', ['index', 'show', 'list'], 'staff')
        def mappings = mappingsFor(plain, admin, staff)
        def reloaded = controller('staff.ReloadController', ['index', 'edit', 'create'], 'staff')

        when:
        mappings.registerController(reloaded)

        then:
        def started = mappingsFor(plain, admin, reloaded)
        classesByKey(mappings.mappingsToGrailsControllerMap) == classesByKey(started.mappingsToGrailsControllerMap)
        classesByKey(mappings.deferredMappings) == classesByKey(started.deferredMappings)
    }

    void 'an action added to a namespaced controller registered again is matched without its namespace'() {
        given:
        def before = controller('reload.ReloadController', ['index'], 'admin')
        def mappings = mappingsFor(before)
        def after = controller('reload.ReloadController', ['index', 'added'], 'admin')

        when:
        mappings.registerController(after)

        then:
        controllerFor(mappings, '/reload/added').is(after.clazz)
        controllerFor(mappings, '/reload/index').is(after.clazz)
    }

    void 'a namespaced controller added while the application runs is matched without its namespace'() {
        given:
        def mappings = mappingsFor(controller('other.OtherController', ['index']))
        def added = controller('admin.ReloadController', ['index', 'show'], 'admin')

        when:
        mappings.registerController(added)

        then:
        controllerFor(mappings, '/reload/show').is(added.clazz)
        controllerFor(mappings, '/other/index') != null
    }

    void 'a namespaced controller registered again leaves a controller without a namespace the requests without one'() {
        given:
        def plain = controller('reload.ReloadController', ['index'])
        def namespaced = controller('admin.ReloadController', ['index'], 'admin')
        def mappings = mappingsFor(plain, namespaced)

        expect:
        controllerFor(mappings, '/reload/index').is(plain.clazz)

        when:
        mappings.registerController(controller('admin.ReloadController', ['index', 'added'], 'admin'))

        then:
        controllerFor(mappings, '/reload/index').is(plain.clazz)
    }

    void 'a controller class added again while the application runs is matched through the url mappings bean'() {
        given:
        def before = controllerClass('reload.ReloadController', ['index'])
        def grailsApplication = new DefaultGrailsApplication(before, ReloadUrlMappings)
        def context = runningContext(grailsApplication)
        def holder = context.getBean('grailsUrlMappingsHolder', UrlMappingsHolder)
        def after = controllerClass('reload.ReloadController', ['index', 'added'])

        expect:
        controllerFor(holder, '/reload/added') == null

        when:
        grailsApplication.addArtefact(ControllerArtefactHandler.TYPE, after)

        then:
        controllerFor(holder, '/reload/added').is(after)
        controllerFor(holder, '/reload/index').is(after)

        cleanup:
        context?.close()
    }

    void 'an application controller that lost an action to a plugin controller at startup still loses it once registered again'() {
        given:
        def application = controller('reload.BookController', ['index', 'show'])
        def plugin = controller('bookstore.BookController', ['index', 'show'], null, 'bookstore')
        def mappings = mappingsFor(application, plugin)

        expect:
        controllerFor(mappings, '/book/show').is(plugin.clazz)

        when:
        mappings.registerController(controller('reload.BookController', ['index', 'show']))

        then:
        controllerFor(mappings, '/book/show').is(plugin.clazz)
    }

    private GrailsControllerUrlMappings mappingsFor(GrailsControllerClass... controllers) {
        def grailsApplication = new DefaultGrailsApplication(controllers*.clazz as Class[]).tap {
            initialise()
        }
        new GrailsControllerUrlMappings(grailsApplication, getUrlMappingsHolder {
            "/$controller/$action?"()
        })
    }

    private static GrailsControllerClass controller(String className, List<String> actions, String namespace = null, String pluginName = null) {
        new DefaultGrailsControllerClass(controllerClass(className, actions, namespace, pluginName))
    }

    private static Class<?> controllerClass(String className, List<String> actions, String namespace = null, String pluginName = null) {
        int lastDot = className.lastIndexOf('.')
        String source = """
            package ${className.substring(0, lastDot)}

            ${pluginName ? "@grails.plugins.metadata.GrailsPlugin(name = '${pluginName}', version = '1.0')" : ''}
            class ${className.substring(lastDot + 1)} {
                ${namespace ? "static namespace = '${namespace}'" : ''}
                ${actions.collect { "@grails.web.Action def ${it}() { '${it}' }" }.join('\n')}
            }
        """
        new GroovyClassLoader(ReloadedControllerMappingSpec.classLoader).parseClass(source)
    }

    /**
     * A started application context whose url mappings bean listens for controllers added to the application.
     */
    private static GenericApplicationContext runningContext(DefaultGrailsApplication grailsApplication) {
        def context = new GenericApplicationContext()
        def discovery = new DefaultPluginDiscovery()
        discovery.init(context.environment)
        context.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, grailsApplication)
        context.beanFactory.registerSingleton(GrailsPluginManager.BEAN_NAME, new DefaultGrailsPluginManager(grailsApplication, discovery))
        context.registerBeanDefinition('grailsUrlMappingsHolder', new RootBeanDefinition(UrlMappingsHolderFactoryBean))
        context.addApplicationListener(grailsApplication)
        grailsApplication.mainContext = context
        grailsApplication.initialise()
        context.refresh()
        context
    }

    private static Map<AbstractGrailsControllerUrlMappings.ControllerKey, Class<?>> classesByKey(Map<AbstractGrailsControllerUrlMappings.ControllerKey, GrailsControllerClass> map) {
        map.collectEntries { key, controller -> [(key): controller.clazz] } as Map<AbstractGrailsControllerUrlMappings.ControllerKey, Class<?>>
    }

    private static Class<?> controllerFor(UrlMappingsHolder mappings, String uri) {
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def request = webRequest.request as MockHttpServletRequest
        request.requestURI = uri
        request.method = 'GET'
        def info = new UrlMappingsHandlerMapping(mappings).getHandler(request)?.handler
        info instanceof GrailsControllerUrlMappingInfo ? ((GrailsControllerUrlMappingInfo) info).controllerClass.clazz : null
    }

    static class ReloadUrlMappings {

        static mappings = {
            "/$controller/$action?"()
        }
    }
}
