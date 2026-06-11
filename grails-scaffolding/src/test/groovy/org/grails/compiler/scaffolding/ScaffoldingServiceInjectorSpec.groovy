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
import grails.plugin.scaffolding.GormService
import org.grails.compiler.injection.GrailsAwareClassLoader
import spock.lang.Specification
import spock.lang.TempDir

class ScaffoldingServiceInjectorSpec extends Specification {

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
        gcl.classInjectors = [new ScaffoldingServiceInjector()] as ClassInjector[]
        gcl.parseClass(DOMAIN_SRC)
    }

    /**
     * The ScaffoldingServiceInjector only runs for sources whose URL matches the
     * grails-app/services/**Service.groovy pattern, and the compiler only resolves that
     * URL when the file actually exists on disk - so the service must be written to a real file.
     */
    private Class<?> parseScaffoldService(String simpleName, String source) {
        def serviceDir = new File(tmpDir, 'grails-app/services/com/example')
        serviceDir.mkdirs()
        def serviceFile = new File(serviceDir, "${simpleName}.groovy")
        serviceFile.text = source
        gcl.parseClass(serviceFile)
    }

    void 'the @Scaffold(Domain) superclass is parameterized as GormService<Domain> rather than raw'() {
        when:
        def serviceClass = parseScaffoldService('WidgetService', '''
package com.example
import grails.plugin.scaffolding.annotation.Scaffold
@Scaffold(Widget)
class WidgetService {
}
''')

        then: 'the generic superclass carries the domain type argument, not an erased raw type'
        serviceClass.genericSuperclass instanceof ParameterizedType

        and:
        ParameterizedType parameterized = serviceClass.genericSuperclass as ParameterizedType
        parameterized.rawType == GormService
        parameterized.actualTypeArguments.length == 1
        parameterized.actualTypeArguments[0].name == 'com.example.Widget'
    }

    void 'the @Scaffold(GormService<Domain>) generic form is parameterized as GormService<Domain>'() {
        when:
        def serviceClass = parseScaffoldService('WidgetService', '''
package com.example
import grails.plugin.scaffolding.GormService
import grails.plugin.scaffolding.annotation.Scaffold
@Scaffold(GormService<Widget>)
class WidgetService {
}
''')

        then:
        ParameterizedType parameterized = serviceClass.genericSuperclass as ParameterizedType
        parameterized.rawType == GormService
        parameterized.actualTypeArguments[0].name == 'com.example.Widget'
    }

    void 'a custom scaffold base is preserved and parameterized as CustomBase<Domain>'() {
        given: 'a custom GormService subclass used as the scaffold base'
        gcl.parseClass('''
package com.example
import grails.plugin.scaffolding.GormService
import org.grails.datastore.gorm.GormEntity
class WidgetRepository<T extends GormEntity<T>> extends GormService<T> {
    WidgetRepository(Class<T> resource, boolean readOnly) {
        super(resource, readOnly)
    }
}
''')

        when:
        def serviceClass = parseScaffoldService('WidgetService', '''
package com.example
import grails.plugin.scaffolding.annotation.Scaffold
@Scaffold(WidgetRepository<Widget>)
class WidgetService {
}
''')

        then: 'the declared custom base is kept (not collapsed to GormService) and carries the domain type argument'
        ParameterizedType parameterized = serviceClass.genericSuperclass as ParameterizedType
        parameterized.rawType.name == 'com.example.WidgetRepository'
        parameterized.actualTypeArguments[0].name == 'com.example.Widget'
    }

    void 'the parameterized superclass narrows inherited get()/list()/save() to the domain type under static compilation'() {
        given:
        parseScaffoldService('WidgetService', '''
package com.example
import grails.plugin.scaffolding.annotation.Scaffold
@Scaffold(Widget)
class WidgetService {
}
''')

        when: 'a statically-compiled caller assigns the scaffold-service results to the concrete domain type'
        def caller = gcl.parseClass('''
package com.example
import groovy.transform.CompileStatic
@CompileStatic
class WidgetCaller {
    static List<Widget> useService(WidgetService service) {
        Widget single = service.get(1L)
        List<Widget> many = service.list([:])
        Widget saved = service.save(new Widget())
        many
    }
}
''')

        then: '''static compilation succeeds without casts: the type checker resolves get():T, list():List<T>
                  and save(T):T through the GormService<Widget> superclass. With the prior raw superclass these
                  erased to GormEntity and the Widget assignments would have failed to compile.'''
        caller != null
        caller.getDeclaredMethod('useService', gcl.loadClass('com.example.WidgetService')) != null
    }
}
