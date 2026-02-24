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

import grails.gorm.transactions.Transactional
import org.grails.datastore.gorm.services.Implemented
import org.grails.datastore.gorm.services.implementers.FindAllWhereImplementer
import org.grails.datastore.gorm.services.implementers.FindOneWhereImplementer
import org.grails.datastore.gorm.services.implementers.CountWhereImplementer
import org.grails.datastore.gorm.services.implementers.CountImplementer
import org.grails.datastore.gorm.services.implementers.FindAllByImplementer
import org.grails.datastore.gorm.services.implementers.FindAllImplementer
import org.grails.datastore.gorm.services.implementers.FindOneByImplementer
import org.grails.datastore.gorm.services.implementers.FindOneImplementer
import spock.lang.Specification

class WhereConnectionRoutingSpec extends Specification {

    void "test @Where method on interface with @Transactional(connection)"() {
        when:"The service transform is applied to an interface with @Transactional(connection)"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.services.Where
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Transactional

@Service(Foo)
@Transactional(connection = 'secondary')
interface FooService {

    @Where({ title ==~ pattern })
    Foo findByTitle(String pattern)
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

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$FooServiceImplementation")

        then:"The impl is valid"
        impl.getAnnotation(Transactional) != null
        impl.getAnnotation(Transactional).connection() == 'secondary'
        impl.getMethod("findByTitle", String).getAnnotation(Implemented).by() == FindOneWhereImplementer
    }

    void "test @Where method on abstract class with @Transactional(connection)"() {
        when:"The service transform is applied to an abstract class"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.services.Where
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Transactional

@Service(Foo)
@Transactional(connection = 'secondary')
abstract class FooService {

    @Where({ title ==~ pattern })
    abstract List<Foo> searchByTitle(String pattern)
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
        !service.isInterface()

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$FooServiceImplementation")

        then:"The impl is valid"
        impl.getAnnotation(Transactional) != null
        impl.getAnnotation(Transactional).connection() == 'secondary'
        impl.getMethod("searchByTitle", String).getAnnotation(Implemented).by() == FindAllWhereImplementer
    }

    void "test count method uses connection-aware implementer"() {
        when:"The service transform is applied to an interface with count()"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Transactional

@Service(Foo)
@Transactional(connection = 'secondary')
interface FooService {
    Number count()
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

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$FooServiceImplementation")

        then:"The impl is valid"
        impl.getAnnotation(Transactional) != null
        impl.getAnnotation(Transactional).connection() == 'secondary'
        impl.getMethod("count").getAnnotation(Implemented).by() == CountImplementer
    }

    void "test list, findAll, and findBy methods use connection-aware implementer"() {
        when:"The service transform is applied to an interface with list, findAll, and findBy methods"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Transactional

@Service(Foo)
@Transactional(connection = 'secondary')
interface FooService {
    List<Foo> list()
    List<Foo> findAllByTitle(String title)
    Foo findByName(String name)
    Foo find(Serializable id)
}
@Entity
class Foo {
    String title
    String name
    static mapping = {
        datasource 'secondary'
    }
}
''')

        then:
        service.isInterface()

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$FooServiceImplementation")

        then:"The impl is valid"
        impl.getAnnotation(Transactional) != null
        impl.getAnnotation(Transactional).connection() == 'secondary'
        impl.getMethod("list").getAnnotation(Implemented).by() == FindAllImplementer
        impl.getMethod("findAllByTitle", String).getAnnotation(Implemented).by() == FindAllByImplementer
        impl.getMethod("findByName", String).getAnnotation(Implemented).by() == FindOneByImplementer
        impl.getMethod("find", Serializable).getAnnotation(Implemented).by() == FindOneImplementer
    }

    void "test @Where method without @Transactional(connection)"() {
        when:"The service transform is applied to an interface with @Where"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.services.Where
import grails.gorm.annotation.Entity

@Service(Foo)
interface FooService {

    @Where({ title ==~ pattern })
    Number countByTitle(String pattern)
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

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$FooServiceImplementation")

        then:"The impl is valid"
        impl.getMethod("countByTitle", String).getAnnotation(Implemented).by() == CountWhereImplementer
    }
}
