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
package org.grails.compiler.scaffolding

import java.lang.reflect.ParameterizedType

import grails.compiler.ast.ClassInjector
import grails.rest.RestfulController
import org.grails.compiler.injection.GrailsAwareClassLoader
import spock.lang.Specification
import spock.lang.TempDir

class ScaffoldingControllerInjectorSpec extends Specification {

    private static final String DOMAIN_SRC = '''
package com.example
import org.grails.datastore.gorm.GormEntity
class Widget implements GormEntity<Widget> {
    String name
}
'''

    @TempDir
    File tmpDir

    GrailsAwareClassLoader gcl

    def setup() {
        gcl = new GrailsAwareClassLoader()
        gcl.classInjectors = [new ScaffoldingControllerInjector()] as ClassInjector[]
        gcl.parseClass(DOMAIN_SRC)
    }

    /**
     * The ScaffoldingControllerInjector only runs for sources whose URL matches the
     * grails-app/controllers/**Controller.groovy pattern, and the compiler only resolves that
     * URL when the file actually exists on disk - so the controller must be written to a real file.
     */
    private Class<?> parseScaffoldController(String simpleName, String source) {
        def controllerDir = new File(tmpDir, 'grails-app/controllers/com/example')
        controllerDir.mkdirs()
        def controllerFile = new File(controllerDir, "${simpleName}.groovy")
        controllerFile.text = source
        gcl.parseClass(controllerFile)
    }

    void 'the @Scaffold(Domain) superclass is parameterized as RestfulController<Domain> rather than raw'() {
        when:
        def controllerClass = parseScaffoldController('WidgetController', '''
package com.example
import grails.plugin.scaffolding.annotation.Scaffold
@Scaffold(Widget)
class WidgetController {
}
''')

        then: 'the generic superclass carries the domain type argument, not an erased raw type'
        controllerClass.genericSuperclass instanceof ParameterizedType

        and:
        ParameterizedType parameterized = controllerClass.genericSuperclass as ParameterizedType
        parameterized.rawType == RestfulController
        parameterized.actualTypeArguments.length == 1
        parameterized.actualTypeArguments[0].name == 'com.example.Widget'
    }

    void 'the @Scaffold(RestfulController<Domain>) generic form is parameterized as RestfulController<Domain>'() {
        when:
        def controllerClass = parseScaffoldController('WidgetController', '''
package com.example
import grails.plugin.scaffolding.annotation.Scaffold
import grails.rest.RestfulController
@Scaffold(RestfulController<Widget>)
class WidgetController {
}
''')

        then:
        ParameterizedType parameterized = controllerClass.genericSuperclass as ParameterizedType
        parameterized.rawType == RestfulController
        parameterized.actualTypeArguments[0].name == 'com.example.Widget'
    }

    void 'a custom scaffold base is preserved and parameterized as CustomBase<Domain>'() {
        given: 'a custom RestfulController subclass used as the scaffold base'
        gcl.parseClass('''
package com.example
import grails.rest.RestfulController
import org.grails.datastore.gorm.GormEntity
class ApiController<T extends GormEntity<T>> extends RestfulController<T> {
    ApiController(Class<T> resource, boolean readOnly) {
        super(resource, readOnly)
    }
}
''')

        when:
        def controllerClass = parseScaffoldController('WidgetController', '''
package com.example
import grails.plugin.scaffolding.annotation.Scaffold
@Scaffold(ApiController<Widget>)
class WidgetController {
}
''')

        then: 'the declared custom base is kept (not collapsed to RestfulController) and carries the domain type argument'
        ParameterizedType parameterized = controllerClass.genericSuperclass as ParameterizedType
        parameterized.rawType.name == 'com.example.ApiController'
        parameterized.actualTypeArguments[0].name == 'com.example.Widget'
    }

    void 'the parameterized superclass narrows inherited resource hooks to the domain type under static compilation'() {
        when: 'a statically-compiled scaffolded controller overrides the hooks and narrows the super results'
        def controllerClass = parseScaffoldController('WidgetController', '''
package com.example
import grails.plugin.scaffolding.annotation.Scaffold
import groovy.transform.CompileStatic
@Scaffold(Widget)
@CompileStatic
class WidgetController {
    @Override
    protected Widget queryForResource(Serializable id) {
        Widget found = super.queryForResource(id)
        found
    }
    @Override
    protected Widget createResource() {
        Widget created = super.createResource()
        created.name = 'fresh'
        created
    }
    @Override
    protected List<Widget> listAllResources(Map params) {
        List<Widget> all = super.listAllResources(params)
        all
    }
}
''')

        then: '''static compilation succeeds without casts: the type checker resolves queryForResource():T,
                  createResource():T and listAllResources():List<T> through the RestfulController<Widget>
                  superclass. With the prior raw superclass these erased to GormEntity and the Widget
                  assignments and the created.name property write would have failed to compile.'''
        controllerClass != null
    }
}
