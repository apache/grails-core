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
package org.grails.boot.internal

import spock.lang.Specification

/**
 * A class loader that answers with another class stands in for an agent that redefines the loaded class in place,
 * which the JVM running the tests does not allow. Each version of a class is compiled under the same name by a class
 * loader of its own, as the versions of a redefined class share their name, so only their members tell them apart.
 */
class RedefinedClassWatchSpec extends Specification {

    static final String CONTROLLER = 'reload.ReloadController'

    File controllerSource = new File('/app/grails-app/controllers/reload/ReloadController.groovy')
    RedefiningClassLoader classLoader = new RedefiningClassLoader(classes: [(CONTROLLER): beforeEdit()])
    RedefinedClassWatch watch = new RedefinedClassWatch(classLoader)

    void 'a class the plugins were told of is not reported while it keeps its shape'() {
        when:
        watch.tell(controllerSource) {}

        then:
        watch.redefined().empty
        watch.redefined().empty
    }

    void 'a class edited without gaining or losing a member is not reported'() {
        given:
        watch.tell(controllerSource) {}

        when:
        classLoader.classes[CONTROLLER] = bodyEdited()

        then:
        watch.redefined().empty
    }

    void 'a class that gains an action after the plugins were told of it is reported once'() {
        given:
        watch.tell(controllerSource) {}

        when:
        classLoader.classes[CONTROLLER] = actionAdded()

        then:
        watch.redefined() == [controllerSource]
        watch.redefined().empty
    }

    void 'a class an agent redefines while the plugins are told of it is reported'() {
        given:
        def redefinedVersion = actionAdded()

        when:
        watch.tell(controllerSource) {
            classLoader.classes[CONTROLLER] = redefinedVersion
        }

        then:
        watch.redefined() == [controllerSource]
        watch.redefined().empty
    }

    void 'a class that loses a field after the plugins were told of it is reported'() {
        given:
        classLoader.classes[CONTROLLER] = actionAdded()
        watch.tell(controllerSource) {}

        when:
        classLoader.classes[CONTROLLER] = fieldRemoved()

        then:
        watch.redefined() == [controllerSource]
    }

    void 'a class is reported again for each change of its shape'() {
        given:
        watch.tell(controllerSource) {}

        when:
        classLoader.classes[CONTROLLER] = actionAdded()
        def first = watch.redefined()
        classLoader.classes[CONTROLLER] = fieldRemoved()
        def second = watch.redefined()

        then:
        first == [controllerSource]
        second == [controllerSource]
    }

    void 'only the class that changed shape is reported'() {
        given:
        def serviceSource = new File('/app/grails-app/services/reload/ReloadService.groovy')
        classLoader.classes['reload.ReloadService'] = compile('''
            package reload

            class ReloadService {
            }
        ''')
        watch.tell(controllerSource) {}
        watch.tell(serviceSource) {}

        when:
        classLoader.classes[CONTROLLER] = actionAdded()

        then:
        watch.redefined() == [controllerSource]
    }

    void 'a file that is not the source of a class is not watched'() {
        given:
        def buildFile = new File('/app/build.gradle')

        boolean told = false

        when:
        watch.tell(buildFile) { told = true }
        classLoader.classes[CONTROLLER] = actionAdded()

        then:
        told
        watch.redefined().empty
    }

    void 'a class that cannot be loaded is not watched'() {
        given:
        def unloadable = new File('/app/grails-app/controllers/reload/MissingController.groovy')

        when:
        watch.tell(unloadable) {}

        then:
        watch.redefined().empty
    }

    private static Class<?> beforeEdit() {
        controller('''
            String message = 'before the edit'

            @grails.web.Action
            Object index() {
                message
            }
        ''')
    }

    private static Class<?> bodyEdited() {
        controller('''
            String message = 'after the edit'

            @grails.web.Action
            Object index() {
                message.toUpperCase()
            }
        ''')
    }

    private static Class<?> actionAdded() {
        controller('''
            String message = 'before the edit'

            @grails.web.Action
            Object index() {
                message
            }

            @grails.web.Action
            Object added() {
                'added while running'
            }
        ''')
    }

    private static Class<?> fieldRemoved() {
        controller('''
            @grails.web.Action
            Object index() {
                'before the edit'
            }

            @grails.web.Action
            Object added() {
                'added while running'
            }
        ''')
    }

    private static Class<?> controller(String members) {
        compile("""
            package reload

            class ReloadController {
                ${members}
            }
        """)
    }

    private static Class<?> compile(String source) {
        new GroovyClassLoader(RedefinedClassWatchSpec.classLoader).parseClass(source)
    }

    static class RedefiningClassLoader extends ClassLoader {
        Map<String, Class<?>> classes

        @Override
        Class<?> loadClass(String name) throws ClassNotFoundException {
            Class<?> loaded = classes[name]
            if (loaded == null) {
                throw new ClassNotFoundException(name)
            }
            loaded
        }
    }
}
