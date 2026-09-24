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
package org.grails.datastore.gorm

import groovy.transform.CompileStatic
import groovy.transform.Generated
import groovy.transform.NamedParam
import groovy.transform.NamedParams

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer

import grails.artefact.Artefact
import grails.persistence.Entity
import org.grails.datastore.gorm.query.GormQueryOperations
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter

/**
 * @author graemerocher
 */
class GormEntityTraitSpec extends Specification {

    void "Test dynamic parse"(){
        when:
        def cls = new GroovyClassLoader().parseClass('''
import grails.persistence.*

@Entity
class Book {

    String title

    Author author

    static namedQueries = {
        kingBooks {
            author {
                eq 'name', 'Stephen King'
            }
        }
    }
}

@Entity
class Author {

    String name
    // here to test properties with only a single letter
    static belongsTo = [p:Publisher]
}

@Entity
class Publisher {
    String name
}
''')
        def instance = cls.newInstance()

        then:
        cls.transients.contains('authorId')
        cls.getMethod("getKingBooks").returnType == GormQueryOperations
        Modifier.isStatic(cls.getMethod("getKingBooks").modifiers)
        GormEntity.isAssignableFrom(cls)
        GormValidateable.isAssignableFrom(cls)
        DirtyCheckable.isAssignableFrom(cls)
        cls.getAnnotation(grails.gorm.annotation.Entity)
        instance.hasProperty('authorId')

        when:
        Method  m = cls.methods.find { method ->
            def rt = method.getParameterTypes()
            rt && rt[0] == Closure && method.name == 'find'
        }

        then:
        m.returnType.name.contains("Book")
    }

    void "Test dynamic parse 2"(){
        def cl = new GroovyClassLoader()
        when:
        def cls = cl.parseClass('''
import grails.persistence.*

@Entity
class Group {
    Long id
    String name
    static hasMany = [members:Member]
    Collection members
}

@Entity
class Member   {
    Long id
    String name
    String externalId
}

@Entity
class SubMember extends Member {
    String extraName

   String getTransientProperty() {
        return transientProperty
    }

    void setTransientProperty(String transientProperty) {
        this.transientProperty = transientProperty
    }
    static transients = ["transientProperty"]
}

''')
        def instance = cls.newInstance()
        def SubMember = cl.loadClass('SubMember')
        then:
        instance.respondsTo('addToMembers')
        GormEntity.isAssignableFrom(cls)
        GormValidateable.isAssignableFrom(cls)
        DirtyCheckable.isAssignableFrom(cls)
        cls.getAnnotation(grails.gorm.annotation.Entity)
        ClassPropertyFetcher.forClass(SubMember).getStaticPropertyValuesFromInheritanceHierarchy(GormProperties.TRANSIENT, Collection) ==  [[], ["transientProperty"]]
    }
    void "test that a class marked with @Artefact('Domain') is enhanced with GormEntityTraitSpec"() {
        expect:
        GormEntity.isAssignableFrom QueryMethodArtefactDomain
    }

    void "test that a class marked with @Entity is enhanced with GormEntityTraitSpec"() {
        expect:
        GormEntity.isAssignableFrom QueryMethodEntityDomain
    }

    void 'test that generic return values are respected'() {
        when:
        def method = QueryMethodArtefactDomain.methods.find { method ->
            def rt = method.getParameterTypes()
            rt && rt[0] == Closure && method.name == 'find'
        }

        then:
        method.returnType == QueryMethodArtefactDomain
    }

    void "refresh(lock: true) and lock(id, refresh: true) resolve statically with the entity return type for #annotation"() {
        given:
        def classLoader = new GroovyClassLoader()

        when:
        def consumer = classLoader.parseClass("""
            import groovy.transform.CompileStatic
            import org.grails.datastore.gorm.GormEntityApi

            @CompileStatic
            class LockConsumer {
                LockBook refreshWithLock(LockBook book) {
                    book.refresh(lock: true)
                }

                LockBook refreshConnection(GormEntityApi<LockBook> connection) {
                    connection.refresh([lock: true])
                }

                LockBook lockWithRefresh(Long id) {
                    LockBook.lock(id, refresh: true)
                }

                LockBook lockById(Long id) {
                    LockBook.lock(id)
                }
            }

            @${annotation}
            class LockBook {
                String title
            }
        """)
        def bookClass = classLoader.loadClass('LockBook')

        then:
        consumer.getMethod('refreshWithLock', bookClass).returnType == bookClass
        consumer.getMethod('refreshConnection', GormEntityApi).returnType == bookClass
        consumer.getMethod('lockWithRefresh', Long).returnType == bookClass
        consumer.getMethod('lockById', Long).returnType == bookClass
        bookClass.getMethod('refresh', Map).returnType == bookClass
        bookClass.getMethod('refresh', Map).isAnnotationPresent(Generated)
        !Modifier.isStatic(bookClass.getMethod('refresh', Map).modifiers)
        bookClass.getMethod('lock', Map, Serializable).returnType == bookClass
        bookClass.getMethod('lock', Map, Serializable).isAnnotationPresent(Generated)
        Modifier.isStatic(bookClass.getMethod('lock', Map, Serializable).modifiers)
        bookClass.getMethod('lock', Serializable).returnType == bookClass

        cleanup:
        classLoader.close()

        where:
        annotation << ['grails.persistence.Entity', "grails.artefact.Artefact('Domain')"]
    }

    void "the supported named arguments are declared on the woven domain class methods"() {
        given:
        def classLoader = new GroovyClassLoader()

        when:
        classLoader.parseClass(NAMED_PARAM_BOOK)
        def bookClass = classLoader.loadClass('NamedParamBook')

        then: "refresh names lock, and lock names refresh and type"
        namedArgumentsOf(bookClass.getMethod('refresh', Map).parameters[0]) == ['lock']
        namedArgumentsOf(bookClass.getMethod('lock', Map, Serializable).parameters[0]) == ['refresh', 'type']

        and: "the value types stay open, because each argument accepts more than one form"
        bookClass.getMethod('refresh', Map).parameters[0]
                .getAnnotation(NamedParams).value().every { it.type() == Object }

        cleanup:
        classLoader.close()
    }

    @Unroll
    void "a statically compiled caller of #expression compiles"() {
        given:
        def classLoader = compilingStatically()

        when:
        classLoader.parseClass("class Caller { void call(NamedParamBook book, Long id) { ${expression} } }")

        then:
        noExceptionThrown()

        cleanup:
        classLoader.close()

        where:
        expression << [
                'book.refresh(lock: true)',
                'book.refresh(lock: false)',
                'NamedParamBook.lock(id, refresh: true)',
                "NamedParamBook.lock(id, type: 'pessimistic_read')",
                'NamedParamBook.lock(id, refresh: true, type: null)',
        ]
    }

    @Unroll
    void "a statically compiled caller of #expression is rejected, naming #unknown"() {
        given:
        def classLoader = compilingStatically()

        when:
        classLoader.parseClass("class Caller { void call(NamedParamBook book, Long id) { ${expression} } }")

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains("unexpected named arg: ${unknown}")

        cleanup:
        classLoader.close()

        where:
        expression                                            | unknown
        'book.refresh(lcok: true)'                            | 'lcok'
        'NamedParamBook.lock(id, refesh: true)'               | 'refesh'
        'NamedParamBook.lock(id, refresh: true, unknown: 1)'  | 'unknown'
    }

    void "a map argument that is not written as named arguments is still accepted, so a caller can pass one through"() {
        given:
        def classLoader = compilingStatically()

        when:
        classLoader.parseClass('''
            class PassThroughCaller {
                void call(NamedParamBook book, Long id, Map options) {
                    book.refresh(options)
                    NamedParamBook.lock(options, id)
                }
            }
        ''')

        then:
        noExceptionThrown()

        cleanup:
        classLoader.close()
    }

    @Unroll
    void "a dynamically compiled caller of #expression compiles, because the metadata only checks static calls"() {
        given:
        def classLoader = new GroovyClassLoader()
        classLoader.parseClass(NAMED_PARAM_BOOK)

        when:
        classLoader.parseClass("class DynamicCaller { void call(NamedParamBook book, Long id) { ${expression} } }")

        then:
        noExceptionThrown()

        cleanup:
        classLoader.close()

        where:
        expression << ['book.refresh(lcok: true)', 'NamedParamBook.lock(id, refesh: true)']
    }

    private static final String NAMED_PARAM_BOOK = '''
        import grails.persistence.Entity

        @Entity
        class NamedParamBook {
            String title
        }
    '''

    /**
     * A loader whose callers are statically compiled, with the domain class itself compiled normally by its
     * parent so that only the call sites are under static type checking.
     */
    private static GroovyClassLoader compilingStatically() {
        def entityLoader = new GroovyClassLoader(GormEntityTraitSpec.classLoader)
        entityLoader.parseClass(NAMED_PARAM_BOOK)
        def configuration = new CompilerConfiguration()
        configuration.addCompilationCustomizers(new ASTTransformationCustomizer(CompileStatic))
        new GroovyClassLoader(entityLoader, configuration)
    }

    private static List<String> namedArgumentsOf(Parameter parameter) {
        parameter.getAnnotation(NamedParams).value().collect { NamedParam named -> named.value() }
    }
}

@Artefact('Domain')
class QueryMethodArtefactDomain {
    String name
}

@Entity
class QueryMethodEntityDomain {
    String name
}
