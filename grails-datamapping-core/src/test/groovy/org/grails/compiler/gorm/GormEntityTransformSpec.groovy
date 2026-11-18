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
package org.grails.compiler.gorm

import java.lang.reflect.Method
import java.lang.reflect.Modifier

import groovy.transform.Generated
import org.codehaus.groovy.ast.ClassNode

import spock.lang.Specification

import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable

/**
 * @author graemerocher
 */
class GormEntityTransformSpec extends Specification{

    void 'generated fields do not leak into a shared non-domain superclass'() {
        given:
        def loader = new GroovyClassLoader()
        loader.parseClass("""
            import grails.gorm.annotation.Entity

            $declarations

            abstract class SharedDetails {
                String name
            }

            class PlainCommand extends SharedDetails { }
        """)

        when:
        def root = loader.loadClass('RootEntity')
        def child = loader.loadClass('ChildEntity')
        def command = loader.loadClass('PlainCommand').getDeclaredConstructor().newInstance()

        then:
        root.getDeclaredField('id').type == Long
        root.getDeclaredField('version').type == Long
        child.getMethod('getId').declaringClass == root
        child.getMethod('getVersion').declaringClass == root
        !child.declaredFields*.name.contains('id')
        !child.declaredFields*.name.contains('version')
        !child.superclass.declaredFields*.name.contains('id') || child.superclass == root
        !child.superclass.declaredFields*.name.contains('version') || child.superclass == root
        command.hasProperty('id') == null
        command.hasProperty('version') == null

        cleanup:
        loader.close()

        where:
        declarations << [
            '@Entity abstract class RootEntity extends SharedDetails { }; @Entity class ChildEntity extends RootEntity { }',
            '@Entity class ChildEntity extends RootEntity { }; @Entity abstract class RootEntity extends SharedDetails { }',
            '@Entity abstract class RootEntity extends SharedDetails { }; abstract class PlainIntermediate extends RootEntity { }; @Entity class ChildEntity extends PlainIntermediate { }',
            '@Entity class ChildEntity extends PlainIntermediate { }; abstract class PlainIntermediate extends RootEntity { }; @Entity abstract class RootEntity extends SharedDetails { }'
        ]
    }

    void 'identity placement and dirty checking are independent of same-named parents in separate compilations'() {
        given:
        def firstLoader = new GroovyClassLoader()
        def secondLoader = new GroovyClassLoader()
        def packageName = "recompiled.entities.order${firstSource}"
        def sources = [
            '''
                import grails.gorm.annotation.Entity
                @Entity class RecompiledChild extends RecompiledBase { String title }
                abstract class RecompiledBase { String name }
            ''',
            '''
                import grails.gorm.annotation.Entity
                @Entity class RecompiledChild extends RecompiledBase { String title }
                @Entity abstract class RecompiledBase { String name }
            '''
        ]

        when:
        firstLoader.parseClass("package $packageName\n${sources[firstSource]}")
        secondLoader.parseClass("package $packageName\n${sources[1 - firstSource]}")
        def plainLoader = firstSource == 0 ? firstLoader : secondLoader
        def domainLoader = firstSource == 1 ? firstLoader : secondLoader
        def plainBase = plainLoader.loadClass("${packageName}.RecompiledBase")
        def plainChild = plainLoader.loadClass("${packageName}.RecompiledChild")
        def domainBase = domainLoader.loadClass("${packageName}.RecompiledBase")
        def domainChild = domainLoader.loadClass("${packageName}.RecompiledChild")

        then:
        !plainBase.declaredFields*.name.contains('id')
        !plainBase.declaredFields*.name.contains('version')
        plainChild.getDeclaredField('id').type == Long
        plainChild.getDeclaredField('version').type == Long
        domainBase.getDeclaredField('id').type == Long
        domainBase.getDeclaredField('version').type == Long
        !domainChild.declaredFields*.name.contains('id')
        !domainChild.declaredFields*.name.contains('version')
        domainChild.getMethod('getId').declaringClass == domainBase
        domainChild.getMethod('getVersion').declaringClass == domainBase

        when:
        def plainInstance = plainChild.getDeclaredConstructor().newInstance()
        def domainInstance = domainChild.getDeclaredConstructor().newInstance()
        plainInstance.trackChanges()
        domainInstance.trackChanges()
        plainInstance.title = 'plain parent'
        domainInstance.title = 'domain parent'
        domainInstance.name = 'updated'

        then:
        plainInstance.listDirtyPropertyNames() == ['title']
        domainInstance.listDirtyPropertyNames() == ['title', 'name']

        cleanup:
        firstLoader.close()
        secondLoader.close()

        where:
        firstSource << [0, 1]
    }

    void 'dirty checking a plain base does not make it the owner of generated persistence fields'() {
        given:
        def loader = new GroovyClassLoader()
        loader.parseClass('''
            import grails.gorm.annotation.Entity
            import grails.gorm.dirty.checking.DirtyCheck

            @DirtyCheck
            abstract class DirtyDetails {
                String name
            }

            @Entity
            class DirtyDetailsEntity extends DirtyDetails { }

            class DirtyDetailsCommand extends DirtyDetails { }
        ''')
        def entity = loader.loadClass('DirtyDetailsEntity')
        def base = loader.loadClass('DirtyDetails')
        def command = loader.loadClass('DirtyDetailsCommand').getDeclaredConstructor().newInstance()

        when:
        command.trackChanges()
        command.name = 'updated'

        then:
        command.listDirtyPropertyNames() == ['name']
        !base.declaredFields*.name.contains('id')
        !base.declaredFields*.name.contains('version')
        command.hasProperty('id') == null
        command.hasProperty('version') == null
        entity.getDeclaredField('id').type == Long
        entity.getDeclaredField('version').type == Long

        cleanup:
        loader.close()
    }

    void 'separately compiled plain superclasses are not modified'() {
        given:
        def loader = new GroovyClassLoader()
        def base = loader.parseClass('abstract class CompiledDetails { String name }')
        def entity = loader.parseClass('''
            import grails.gorm.annotation.Entity

            @Entity
            class CompiledEntity extends CompiledDetails { }
        ''')

        expect:
        entity.getDeclaredField('id').type == Long
        entity.getDeclaredField('version').type == Long
        !base.declaredFields*.name.contains('id')
        !base.declaredFields*.name.contains('version')

        cleanup:
        loader.close()
    }

    void 'test parse named queries'() {
        when:
        def bookClass = new GroovyClassLoader().parseClass('''
            import grails.gorm.annotation.Entity

            @Entity
            class Book {
            
                String title
            
                static namedQueries = {
                    startsWithA { ->
                        like('title', 'A%')
                    }
                    
                    startsWithB {
                        like('title', 'B%')
                    }
                    
                    startsWithLetter { String letter ->
                        like('title', letter)
                    }
                }
            }
        ''')

        then:
        new ClassNode(bookClass).methods
        bookClass.getMethod('getStartsWithA')
        bookClass.getMethod('startsWithA')
        bookClass.getMethod('getStartsWithB')
        bookClass.getMethod('startsWithB')

        and: 'they are all marked as Generated'
        bookClass.getMethod('getStartsWithA').isAnnotationPresent(Generated)
        bookClass.getMethod('startsWithA').isAnnotationPresent(Generated)
        bookClass.getMethod('getStartsWithB').isAnnotationPresent(Generated)
        bookClass.getMethod('startsWithB').isAnnotationPresent(Generated)
        bookClass.getMethod('startsWithLetter', String).isAnnotationPresent(Generated)
    }

    void 'test parse withTransaction usage in spock'() {
        when:
        def classLoader = new GroovyClassLoader()
        def bookClass = classLoader.parseClass('''
            import grails.gorm.annotation.Entity
            
            @Entity
            class Book {

                String title
            
                static constraints = {
                    title(validator: { val ->
                        val.asBoolean()
                    })
                }
            }
        ''')
        def spockClass = classLoader.parseClass('''
            class HibernateSpecSpec extends spock.lang.Specification {
            
                void setupSpec() {
                    Book.withTransaction {
                        new Book(title: 'The Stand').save(flush: true)
                    }
                }
            
                void 'test hibernate spec'() {
                    expect:
                    Book.count() == 1
                    !new Book().validate()
                    !new Book(title: '').validate()
                }
            }
        ''')

        then: 'the classes are valid'
        new ClassNode(bookClass).methods
        new ClassNode(spockClass).methods
    }

    void 'test parse abstract GORM entity with getters and setters'() {
        when:
        def cls = new GroovyClassLoader().parseClass('''
            import grails.gorm.annotation.Entity
            
            @Entity
            abstract class AbstractDomain {
            
                abstract String getStringValue()
                abstract void setStringValue(String value)
            
            }
        ''')

        then: 'it is a valid class'
        new ClassNode(cls).methods
    }

    void 'test parse GORM entity with single char properties'() {

        when: 'a gorm entity is parsed'
        def cls = new GroovyClassLoader().parseClass('''
            import grails.gorm.annotation.Entity
            
            @Entity
            class Person {
                String firstName
                String lastName
            }
            
            @Entity
            class PersonLink {
                Person a
                Person b

                String toString() {
                    "$a -> $b"
                }
            }
        ''')

        then: 'it is a valid class'
        new ClassNode(cls).methods
    }

    void 'test parse GORM entity'() {

        when: 'a gorm entity is parsed'
        def cls = new GroovyClassLoader().parseClass('''
            import grails.gorm.annotation.Entity
            
            @Entity
            class Foo {
                String name
            }
        ''')

        then: 'it is a valid class'
        new ClassNode(cls).methods
    }

    void 'test GORM entity transformation implements'() {
        expect:
        GormEntity.isAssignableFrom(Book)
        GormValidateable.isAssignableFrom(Book)
        DirtyCheckable.isAssignableFrom(Book)
        Book.getAnnotation(Entity)
        new Author().respondsTo('addToBooks', Book)
        new Book().hasProperty('authorId')
        Author.getDeclaredMethod('addToBooks', Object).isAnnotationPresent(Generated)
        Author.getDeclaredMethod('removeFromBooks', Object).isAnnotationPresent(Generated)
        Author.getDeclaredMethod('setBooks', Set).isAnnotationPresent(Generated)
        Author.getDeclaredMethod('getBooks').isAnnotationPresent(Generated)
        Book.getDeclaredMethod('getAuthorId').isAnnotationPresent(Generated)
        Book.getDeclaredMethod('refresh', Map).isAnnotationPresent(Generated)
        Book.getDeclaredMethod('refresh', Map).returnType == Book
        !Modifier.isStatic(Book.getDeclaredMethod('refresh', Map).modifiers)
        Book.getDeclaredMethod('lock', Map, Serializable).isAnnotationPresent(Generated)
        Book.getDeclaredMethod('lock', Map, Serializable).returnType == Book
        Modifier.isStatic(Book.getDeclaredMethod('lock', Map, Serializable).modifiers)
        Book.getDeclaredMethod('lock', Serializable).returnType == Book
    }

    void 'test property/method missing'() {
        when:
        Book.foo()

        then:
        thrown(IllegalStateException)

        when:
        Book.bar

        then:
        Book.getDeclaredMethod('$static_propertyMissing', String)
        Book.getDeclaredMethod('$static_propertyMissing', String).isAnnotationPresent(Generated)
        thrown(MissingPropertyException)

        when:
        Book.bar = 'blah'

        then:
        Book.getDeclaredMethod('$static_propertyMissing', String, Object)
        Book.getDeclaredMethod('$static_propertyMissing', String, Object).isAnnotationPresent(Generated)
        thrown(MissingPropertyException)
    }

    void 'test that all GormEntity/GormValidateable trait methods are marked as Generated'() {

        expect: 'all GormEntity methods are marked as Generated on implementation class'
        GormEntity.methods.each { Method traitMethod ->
            assert findGeneratedMethod(Book, traitMethod).isAnnotationPresent(Generated)
        }

        and: 'all GormValidateable methods are marked as Generated on implementation class'
        GormValidateable.methods.each { Method traitMethod ->
            assert findGeneratedMethod(Book, traitMethod).isAnnotationPresent(Generated)
        }
    }

    // Groovy 6 specializes a generic trait method's type-variable parameters to the implementing
    // class, so Book.merge(Object) from GormEntity<D> becomes Book.merge(Book). Look the specialized
    // signature up when the erased one is missing.
    private static Method findGeneratedMethod(Class targetClass, Method traitMethod) {
        try {
            return targetClass.getMethod(traitMethod.name, traitMethod.parameterTypes)
        } catch (NoSuchMethodException e) {
            Class[] specializedParameterTypes = traitMethod.genericParameterTypes.withIndex().collect { type, index ->
                type instanceof java.lang.reflect.TypeVariable ? targetClass : traitMethod.parameterTypes[index]
            } as Class[]
            if (specializedParameterTypes.toList() != traitMethod.parameterTypes.toList()) {
                return targetClass.getMethod(traitMethod.name, specializedParameterTypes)
            }
            throw e
        }
    }

    void 'test that all DirtyCheckingTransformer added methods are marked as Generated'() {

        expect: 'added getId and getVersion methods are marked'
        Book.getMethod('getId').isAnnotationPresent(Generated)
        Book.getMethod('getVersion').isAnnotationPresent(Generated)

        and: 'getter and setter methods are marked'
        Book.getMethod('getTitle').isAnnotationPresent(Generated)
        Book.getMethod('setTitle', String).isAnnotationPresent(Generated)
        Book.getMethod('getAuthor').isAnnotationPresent(Generated)
        Book.getMethod('setAuthor', Author).isAnnotationPresent(Generated)
    }
}

@Entity
class Book {
    String title
    static belongsTo = [author:Author]
}

@Entity
class Author {
    String name
    static hasMany = [books:Book]
}
