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

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.grails.datastore.gorm.services.Implemented
import org.grails.datastore.gorm.services.implementers.DeleteWhereImplementer
import org.grails.datastore.gorm.services.implementers.FindAllPropertyProjectionImplementer
import org.grails.datastore.gorm.services.implementers.FindOneByImplementer
import org.grails.datastore.gorm.services.implementers.FindOneInterfaceProjectionWhereImplementer
import org.grails.datastore.gorm.services.implementers.FindOnePropertyProjectionImplementer
import org.grails.datastore.gorm.services.implementers.UpdateOneImplementer
import spock.lang.Specification

/**
 * Covers DSL shapes for {@link org.grails.datastore.gorm.services.implementers.ServiceImplementer}
 * subclasses that are not otherwise exercised by {@link ServiceTransformSpec} or
 * {@link WhereConnectionRoutingSpec}: update methods, {@code @Where}-annotated deletes,
 * property projections and the {@code findById(id)} shortcut.
 */
class ServiceImplementerEdgeCaseSpec extends Specification {

    void 'an update method with a matching id parameter is implemented via UpdateOneImplementer'() {
        when:
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity

@Service(Foo)
interface FooService {
    Foo updateFoo(Serializable id, String title)
}
@Entity
class Foo {
    String title
}
''')

        then:
        service.isInterface()

        when:
        Class impl = service.classLoader.loadClass("\$FooServiceImplementation")

        then:
        impl.getMethod('updateFoo', Serializable, String).getAnnotation(Implemented).by() == UpdateOneImplementer
    }

    void 'an update method with a Map args parameter is implemented via UpdateOneImplementer'() {
        when:
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity

@Service(Foo)
interface FooService {
    Foo updateFoo(Serializable id, Map args)
}
@Entity
class Foo {
    String title
}
''')

        then:
        service.isInterface()

        when:
        Class impl = service.classLoader.loadClass("\$FooServiceImplementation")

        then:
        impl.getMethod('updateFoo', Serializable, Map).getAnnotation(Implemented).by() == UpdateOneImplementer
    }

    void 'an update method on a service routed to a non-default connection saves through the instance API for that connection'() {
        when:
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Transactional

@Service(Foo)
@Transactional(connection = 'secondary')
interface FooService {
    Foo updateFoo(Serializable id, String title)
}
@Entity
class Foo {
    String title
    static mapping = {
        datasource 'secondary'
    }
}
''')

        then:
        service.isInterface()

        when:
        Class impl = service.classLoader.loadClass("\$FooServiceImplementation")

        then:
        impl.getMethod('updateFoo', Serializable, String).getAnnotation(Implemented).by() == UpdateOneImplementer
    }

    void 'an update method with a parameter that matches no domain property fails to compile'() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity

@Service(Foo)
interface FooService {
    Foo updateFoo(Serializable id, String notAProperty)
}
@Entity
class Foo {
    String title
}
''')

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('Cannot implement method for argument [notAProperty]')
    }

    void 'a void @Where delete method is implemented via DeleteWhereImplementer'() {
        when:
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.services.Where
import grails.gorm.annotation.Entity

@Service(Foo)
interface FooService {
    @Where({ title ==~ pattern })
    void deleteByTitle(String pattern)
}
@Entity
class Foo {
    String title
}
''')

        then:
        service.isInterface()

        when:
        Class impl = service.classLoader.loadClass("\$FooServiceImplementation")

        then:
        impl.getMethod('deleteByTitle', String).getAnnotation(Implemented).by() == DeleteWhereImplementer
    }

    void 'a Number-returning @Where delete method is implemented via DeleteWhereImplementer'() {
        when:
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.services.Where
import grails.gorm.annotation.Entity

@Service(Foo)
interface FooService {
    @Where({ title ==~ pattern })
    Number deleteByTitle(String pattern)
}
@Entity
class Foo {
    String title
}
''')

        then:
        service.isInterface()

        when:
        Class impl = service.classLoader.loadClass("\$FooServiceImplementation")

        then:
        impl.getMethod('deleteByTitle', String).getAnnotation(Implemented).by() == DeleteWhereImplementer
    }

    void 'a @Where method returning an interface projection is implemented via FindOneInterfaceProjectionWhereImplementer'() {
        when:
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.services.Where
import grails.gorm.annotation.Entity

interface ITitle {
    String getTitle()
}

@Service(Foo)
interface FooService {
    @Where({ title ==~ pattern })
    ITitle findProjectionByTitle(String pattern)
}
@Entity
class Foo {
    String title
}
''')

        then:
        service.isInterface()

        when:
        Class impl = service.classLoader.loadClass("\$FooServiceImplementation")

        then:
        impl.getMethod('findProjectionByTitle', String).getAnnotation(Implemented).by() == FindOneInterfaceProjectionWhereImplementer
    }

    void 'a findAll<Domain><Property> method is implemented via FindAllPropertyProjectionImplementer'() {
        when:
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity

@Service(Foo)
interface FooService {
    List<String> findFooTitle()
}
@Entity
class Foo {
    String title
}
''')

        then:
        service.isInterface()

        when:
        Class impl = service.classLoader.loadClass("\$FooServiceImplementation")

        then:
        impl.getMethod('findFooTitle').getAnnotation(Implemented).by() == FindAllPropertyProjectionImplementer
    }

    void 'a findAll<Domain><Property> method returning an array is implemented via FindAllPropertyProjectionImplementer'() {
        when:
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity

@Service(Foo)
interface FooService {
    String[] findFooTitle()
}
@Entity
class Foo {
    String title
}
''')

        then:
        service.isInterface()

        when:
        Class impl = service.classLoader.loadClass("\$FooServiceImplementation")

        then:
        impl.getMethod('findFooTitle').getAnnotation(Implemented).by() == FindAllPropertyProjectionImplementer
    }

    void 'a find<Domain><Property> method returning a single value is implemented via FindOnePropertyProjectionImplementer'() {
        when:
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity

@Service(Foo)
interface FooService {
    String findFooTitle()
}
@Entity
class Foo {
    String title
}
''')

        then:
        service.isInterface()

        when:
        Class impl = service.classLoader.loadClass("\$FooServiceImplementation")

        then:
        impl.getMethod('findFooTitle').getAnnotation(Implemented).by() == FindOnePropertyProjectionImplementer
    }

    void 'a findById(Serializable) method is implemented via a direct get(id) call in FindOneByImplementer'() {
        when:
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity

@Service(Foo)
interface FooService {
    Foo findById(Serializable id)
}
@Entity
class Foo {
    String title
}
''')

        then:
        service.isInterface()

        when:
        Class impl = service.classLoader.loadClass("\$FooServiceImplementation")

        then:
        impl.getMethod('findById', Serializable).getAnnotation(Implemented).by() == FindOneByImplementer
    }

    void 'a pre-existing concrete write method on an abstract class service is enhanced with a default transaction'() {
        when:
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity

@Service(Foo)
abstract class FooService {
    Foo saveFoo(String title) {
        Foo f = new Foo(title: title)
        f.save(failOnError: true)
        return f
    }
}
@Entity
class Foo {
    String title
}
''')

        then:
        !service.isInterface()

        when:
        Class impl = service.classLoader.loadClass("\$FooServiceImplementation")

        then:
        impl.getMethod('saveFoo', String).getAnnotation(grails.gorm.transactions.Transactional) != null
    }

    void 'a @Where delete method with an incompatible return type fails to compile'() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.services.Where
import grails.gorm.annotation.Entity

@Service(Foo)
interface FooService {
    @Where({ title ==~ pattern })
    String deleteByTitle(String pattern)
}
@Entity
class Foo {
    String title
}
''')

        then:
        MultipleCompilationErrorsException e = thrown(MultipleCompilationErrorsException)
        e.message.contains('No implementations possible')
    }

    void 'a save method with an id-named parameter binds it directly onto the entity'() {
        when:
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity

@Service(Foo)
interface FooService {
    Foo save(Serializable id, String title)
}
@Entity
class Foo {
    String title
}
''')

        then:
        service.isInterface()

        when:
        Class impl = service.classLoader.loadClass("\$FooServiceImplementation")

        then:
        impl.getMethod('save', Serializable, String).getAnnotation(Implemented).by() == org.grails.datastore.gorm.services.implementers.SaveImplementer
    }
}
