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

import grails.gorm.annotation.Entity
import groovy.transform.Generated
import org.codehaus.groovy.ast.ClassNode
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import spock.lang.Specification

import java.lang.reflect.Method

/**
 * @author graemerocher
 */
class GormEntityTransformSpec extends Specification{


    void "test parse named queries"() {
        def classLoader = new GroovyClassLoader()
        when:
        Class bookClass = classLoader.parseClass('''
import grails.gorm.annotation.Entity
@Entity
class Book {
    String title

    static namedQueries = {
        startsWithA {->
            like 'title', 'A%'
        }
        
        startsWithB {
            like 'title', 'B%'
        }
        
        startsWithLetter { String letter ->
            like 'title', letter
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

        and: "they are all marked as Generated"
        bookClass.getMethod('getStartsWithA').isAnnotationPresent(Generated)
        bookClass.getMethod('startsWithA').isAnnotationPresent(Generated)
        bookClass.getMethod('getStartsWithB').isAnnotationPresent(Generated)
        bookClass.getMethod('startsWithB').isAnnotationPresent(Generated)
        bookClass.getMethod('startsWithLetter', String).isAnnotationPresent(Generated)
    }

    void "test parse withTransaction usage in spock"() {
            def classLoader = new GroovyClassLoader()
            when:
            Class bookClass = classLoader.parseClass('''
import grails.gorm.annotation.Entity
@Entity
class Book {
    String title

    static constraints = {
        title validator: { val ->
            val.asBoolean()
        }
    }
}


''')
            Class spockClass = classLoader.parseClass('''
class HibernateSpecSpec extends spock.lang.Specification {

    void setupSpec() {
        Book.withTransaction {
            new Book(title: "The Stand").save(flush:true)
        }
    }
    void "test hibernate spec"() {
        expect:
        Book.count() == 1
        !new Book().validate()
        !new Book(title: "").validate()
    }
}

''')

        then:"The classes are valid"
        new ClassNode(bookClass).methods
        new ClassNode(spockClass).methods

    }

    void "Test parse abstract GORM entity with getters and setters"() {
        when:
        def cls = new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
abstract class AbstractDomain {

    abstract String getStringValue()
    abstract void setStringValue(String value)

}


''')
        then:"It is a valid class"
        new ClassNode(cls).methods
    }

    void "Test parse GORM entity with single char properties"() {
        when:"A gorm entity is parsed"
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
        then:"It is a valid class"
        new ClassNode(cls).methods
    }

    void "Test parse GORM entity"() {
        when:"A gorm entity is parsed"
        def cls = new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Foo {
    String name
}
''')
        then:"It is a valid class"
        new ClassNode(cls).methods
    }

    void "Test GORM entity transformation implements"() {
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
    }

    void "Test property/method missing"() {

        when:
        Book.foo()
        then:
        thrown IllegalStateException
        when:
        def var = Book.bar
        then:
        Book.getDeclaredMethod('$static_propertyMissing', String)
        Book.getDeclaredMethod('$static_propertyMissing', String).isAnnotationPresent(Generated)
        thrown MissingPropertyException
        when:
        Book.bar = 'blah'
        then:
        Book.getDeclaredMethod('$static_propertyMissing', String, Object)
        Book.getDeclaredMethod('$static_propertyMissing', String, Object).isAnnotationPresent(Generated)
        thrown MissingPropertyException
    }

    void "test that all GormEntity/GormValidateable trait methods are marked as Generated"() {
        expect: "all GormEntity methods are marked as Generated on implementation class"
        GormEntity.getMethods().each { Method traitMethod ->
            assert Book.class.getMethod(traitMethod.name, traitMethod.parameterTypes).isAnnotationPresent(Generated)
        }

        and: "all GormValidateable methods are marked as Generated on implementation class"
        GormValidateable.getMethods().each { Method traitMethod ->
            assert Book.class.getMethod(traitMethod.name, traitMethod.parameterTypes).isAnnotationPresent(Generated)
        }
    }

    void "test that all DirtyCheckingTransformer added methods are marked as Generated"() {
        expect: "added getId and getVersion methods are marked"
        Book.getMethod('getId').isAnnotationPresent(Generated)
        Book.getMethod('getVersion').isAnnotationPresent(Generated)

        and: "getter and setter methods are marked"
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
