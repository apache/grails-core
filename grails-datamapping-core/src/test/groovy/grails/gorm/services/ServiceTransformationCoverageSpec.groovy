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
package grails.gorm.services

import java.lang.reflect.Modifier

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Specification

/**
 * Covers two code paths introduced by the GormRegistry rewrite of
 * {@link org.grails.datastore.gorm.services.transform.ServiceTransformation} that the existing
 * {@link ServiceTransformSpec}/{@link ConnectionRoutingServiceTransformSpec} suites never exercise:
 *
 * <ul>
 *     <li>the constructor guard for abstract data services (restructured to sit behind the new
 *     {@code isAbstractClass || !isInterface} property-scanning guard)</li>
 *     <li>{@code addDatastoreMethods}/{@code generateServiceDescriptor} being invoked directly on a
 *     concrete (non-interface, non-abstract) {@code @Service} class rather than on a generated
 *     {@code $...Implementation} class - previously only exercised via classes declared statically in
 *     spec source (compiled by {@code compileTestGroovy}, outside JaCoCo's instrumentation of the
 *     {@code test} task JVM), never via a runtime {@code GroovyClassLoader#parseClass}</li>
 * </ul>
 */
class ServiceTransformationCoverageSpec extends Specification {

    void "test abstract data service with an explicit constructor fails to compile"() {
        when: "an abstract service declares its own constructor"
        new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity

@Service(Foo)
abstract class BadAbstractService {

    BadAbstractService() {
    }

    abstract Foo find(Serializable id)
}
@Entity
class Foo {
    String title
}
''')

        then: "a compilation error is raised rather than silently accepting the constructor"
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('Abstract data Services should not define constructors')
    }

    void "test concrete non-abstract service class is enhanced directly without a generated implementation class"() {
        when: "a plain concrete class (neither an interface nor abstract) is annotated with @Service"
        Class scriptClass = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity

@Service(Foo)
class PlainFooService {
    void doStuff() {
    }
}
@Entity
class Foo {
    String title
}
return PlainFooService
''')
        Class service = scriptClass.classLoader.loadClass('PlainFooService')

        then: "the class compiles as itself - no interface, no generated Implementation subclass"
        !service.isInterface()
        !Modifier.isAbstract(service.modifiers)

        and: "no separate implementation class was generated - the transform enhanced this class directly"
        service.classLoader.loadedClasses.every { !it.name.contains('$PlainFooServiceImplementation') }

        and: "the datastore accessor methods were woven directly onto the concrete class"
        service.getMethod('getDatastore') != null
        service.getMethod('setDatastore', org.grails.datastore.mapping.core.Datastore) != null

        and: "the class is a real GORM Service at runtime"
        org.grails.datastore.mapping.services.Service.isAssignableFrom(service)
    }
}
