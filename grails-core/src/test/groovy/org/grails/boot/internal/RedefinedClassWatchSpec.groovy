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

import grails.web.Action

/**
 * A class loader that answers with another class stands in for an agent that redefines the loaded class in place,
 * which the JVM running the tests does not allow.
 */
class RedefinedClassWatchSpec extends Specification {

    File controllerSource = new File('/app/grails-app/controllers/reload/ReloadController.groovy')
    RedefiningClassLoader classLoader = new RedefiningClassLoader(classes: ['reload.ReloadController': BeforeEditController])
    RedefinedClassWatch watch = new RedefinedClassWatch(classLoader)

    void 'a class the plugins were told of is not reported while it keeps its shape'() {
        when:
        watch.told(controllerSource)

        then:
        watch.redefined().empty
        watch.redefined().empty
    }

    void 'a class that gains an action after the plugins were told of it is reported once'() {
        given:
        watch.told(controllerSource)

        when:
        classLoader.classes['reload.ReloadController'] = ActionAddedController

        then:
        watch.redefined() == [controllerSource]
        watch.redefined().empty
    }

    void 'a class that loses a field after the plugins were told of it is reported'() {
        given:
        classLoader.classes['reload.ReloadController'] = ActionAddedController
        watch.told(controllerSource)

        when:
        classLoader.classes['reload.ReloadController'] = FieldRemovedController

        then:
        watch.redefined() == [controllerSource]
    }

    void 'a class is reported again for each change of its shape'() {
        given:
        watch.told(controllerSource)

        when:
        classLoader.classes['reload.ReloadController'] = ActionAddedController
        def first = watch.redefined()
        classLoader.classes['reload.ReloadController'] = FieldRemovedController
        def second = watch.redefined()

        then:
        first == [controllerSource]
        second == [controllerSource]
    }

    void 'only the class that changed shape is reported'() {
        given:
        def serviceSource = new File('/app/grails-app/services/reload/ReloadService.groovy')
        classLoader.classes['reload.ReloadService'] = ReloadService
        watch.told(controllerSource)
        watch.told(serviceSource)

        when:
        classLoader.classes['reload.ReloadController'] = ActionAddedController

        then:
        watch.redefined() == [controllerSource]
    }

    void 'a file that is not the source of a class is not watched'() {
        given:
        def buildFile = new File('/app/build.gradle')

        when:
        watch.told(buildFile)
        classLoader.classes['reload.ReloadController'] = ActionAddedController

        then:
        watch.redefined().empty
    }

    void 'a class that cannot be loaded is not watched'() {
        given:
        def unloadable = new File('/app/grails-app/controllers/reload/MissingController.groovy')

        when:
        watch.told(unloadable)

        then:
        watch.redefined().empty
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

    static class BeforeEditController {
        String message = 'before the edit'

        @Action
        Object index() {
            message
        }
    }

    static class ActionAddedController {
        String message = 'before the edit'

        @Action
        Object index() {
            message
        }

        @Action
        Object added() {
            'added while running'
        }
    }

    static class FieldRemovedController {
        @Action
        Object index() {
            'before the edit'
        }

        @Action
        Object added() {
            'added while running'
        }
    }

    static class ReloadService {
    }
}
