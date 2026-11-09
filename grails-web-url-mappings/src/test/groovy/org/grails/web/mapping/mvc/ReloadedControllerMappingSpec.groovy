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

import org.springframework.mock.web.MockHttpServletRequest

import grails.core.DefaultGrailsApplication
import grails.core.GrailsControllerClass
import grails.util.GrailsWebMockUtil
import grails.web.mapping.AbstractUrlMappingsSpec
import org.grails.core.DefaultGrailsControllerClass

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

    private GrailsControllerUrlMappings mappingsFor(GrailsControllerClass... controllers) {
        def grailsApplication = new DefaultGrailsApplication(controllers*.clazz as Class[]).tap {
            initialise()
        }
        new GrailsControllerUrlMappings(grailsApplication, getUrlMappingsHolder {
            "/$controller/$action?"()
        })
    }

    private static GrailsControllerClass controller(String className, List<String> actions, String namespace = null) {
        int lastDot = className.lastIndexOf('.')
        String source = """
            package ${className.substring(0, lastDot)}

            class ${className.substring(lastDot + 1)} {
                ${namespace ? "static namespace = '${namespace}'" : ''}
                ${actions.collect { "@grails.web.Action def ${it}() { '${it}' }" }.join('\n')}
            }
        """
        new DefaultGrailsControllerClass(new GroovyClassLoader(ReloadedControllerMappingSpec.classLoader).parseClass(source))
    }

    private static Class<?> controllerFor(GrailsControllerUrlMappings mappings, String uri) {
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def request = webRequest.request as MockHttpServletRequest
        request.requestURI = uri
        request.method = 'GET'
        def info = new UrlMappingsHandlerMapping(mappings).getHandler(request)?.handler
        info instanceof GrailsControllerUrlMappingInfo ? ((GrailsControllerUrlMappingInfo) info).controllerClass.clazz : null
    }
}
