package org.grails.compiler.gorm
import grails.gorm.annotation.Entity
import org.codehaus.groovy.ast.ClassNode
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import spock.lang.Specification
/*
 * Copyright 2014 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
        bookClass.getMethod('startsWithLetter', String)

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
        thrown MissingPropertyException
        when:
        Book.bar = 'blah'
        then:
        Book.getDeclaredMethod('$static_propertyMissing', String, Object)
        thrown MissingPropertyException
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
