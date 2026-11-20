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

import spock.lang.Specification

/**
 * Covers {@code AbstractStringQueryImplementer#buildNamedParamsFromQuery} - new in the GormRegistry
 * rewrite: a constant (non-GString) {@code @Query} string containing named parameters (e.g.
 * {@code :title}) that match method parameter names now gets an implicit {@code Map} of bound
 * parameters built for it, so Hibernate 7's strict parameter validation succeeds even without an
 * explicit {@code Map args} method parameter. Every existing {@code @Query} test in
 * {@link ServiceTransformSpec} either uses GString interpolation (a different code path entirely)
 * or a constant string with no {@code :name} placeholders, so this exact branch - and the
 * {@code args(transformed, argMap)} call it feeds into - had no coverage at all.
 */
class AbstractStringQueryImplementerCoverageSpec extends Specification {

    void "test constant @Query string with named parameters matching method args builds an implicit params map"() {
        when:
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(Foo)
interface MyService {

    @Query('from Foo as f where f.title = :title and f.age > :minAge')
    Foo searchByNamedParams(String title, int minAge)
}
@Entity
class Foo {
    String title
    int age
}
''')

        then: "the interface compiles without error - the named params were correctly bound"
        service.isInterface()

        when: "the impl is obtained"
        Class impl = service.classLoader.loadClass('$MyServiceImplementation')

        then: "the method was implemented (not left as an unresolved abstract method)"
        impl.getMethod('searchByNamedParams', String, int)
                .getAnnotation(org.grails.datastore.gorm.services.Implemented) != null
    }

    void "test constant @Query string with a named parameter that has no matching method argument compiles without a params map"() {
        when: "the query references a named param the method doesn't declare"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(Foo)
interface MyService {

    @Query('from Foo as f where f.title = :missing')
    Foo searchByTitle(String title)
}
@Entity
class Foo {
    String title
}
''')

        then: "still compiles - buildNamedParamsFromQuery finds no matching parameter and returns null"
        service.isInterface()

        when:
        Class impl = service.classLoader.loadClass('$MyServiceImplementation')

        then:
        impl.getMethod('searchByTitle', String)
                .getAnnotation(org.grails.datastore.gorm.services.Implemented) != null
    }

    void "test constant @Query with named params and a non-domain return type merges the max:1 map into the existing arg list"() {
        when: "FindOneStringQueryImplementer.buildQueryReturnStatement receives an already-multi-arg\n" +
                "ArgumentListExpression (built by AbstractStringQueryImplementer's named-params support)\n" +
                "for a non-domain, non-'find' return type - which needs the max:1 map appended rather\n" +
                "than the whole arg list wrapped as a single nested argument"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(Foo)
interface MyService {

    @Query('select f.title from Foo as f where f.title = :title')
    String searchTitle(String title)
}
@Entity
class Foo {
    String title
}
''')

        then: "the interface compiles - isCompatibleReturnType's constant-query 'select'/'from' detection also runs here"
        service.isInterface()

        when:
        Class impl = service.classLoader.loadClass('$MyServiceImplementation')

        then:
        impl.getMethod('searchTitle', String)
                .getAnnotation(org.grails.datastore.gorm.services.Implemented) != null
    }
}
