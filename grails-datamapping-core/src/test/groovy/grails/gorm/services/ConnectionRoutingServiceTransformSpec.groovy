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
import org.grails.datastore.gorm.services.implementers.DeleteImplementer
import org.grails.datastore.gorm.services.implementers.FindAndDeleteImplementer
import org.grails.datastore.gorm.services.implementers.FindOneImplementer
import org.grails.datastore.gorm.services.implementers.SaveImplementer
import spock.lang.Specification

/**
 * Tests that auto-implemented Data Service methods correctly route through
 * connection-aware GormEnhancer APIs when @Transactional(connection=...) is specified.
 *
 * Covers the fix for: save (single-entity), delete (by-id), find-and-delete (by-id),
 * and get/find (by-id) which previously bypassed connection routing and always hit
 * the default datasource.
 *
 * @see org.grails.datastore.gorm.services.implementers.SaveImplementer
 * @see org.grails.datastore.gorm.services.implementers.DeleteImplementer
 * @see org.grails.datastore.gorm.services.implementers.FindAndDeleteImplementer
 * @see org.grails.datastore.gorm.services.implementers.AbstractDetachedCriteriaServiceImplementor
 */
class ConnectionRoutingServiceTransformSpec extends Specification {

    void "test save with @Transactional(connection) routes through connection-aware API"() {
        when: "an abstract data service with @Transactional(connection='secondary') declares save(Foo)"
        def service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Transactional

@Service(Foo)
@Transactional(connection = 'secondary')
abstract class FooService {

    abstract Foo save(Foo foo)

    abstract Foo saveFoo(String title)
}

@Entity
class Foo {
    String title

    static mapping = {
        datasource 'secondary'
    }
}
''')

        then: 'the class compiles without errors'
        !service.isInterface()

        when: 'the implementation is loaded'
        def impl = service.classLoader.loadClass('$FooServiceImplementation')

        then: 'the implementation exists and inherits the connection-aware @Transactional'
        impl != null
        impl.getAnnotation(Transactional) != null
        impl.getAnnotation(Transactional).connection() == 'secondary'

        and: 'save(Foo) is implemented by SaveImplementer'
        impl.getMethod('save', impl.classLoader.loadClass('Foo'))
                .getAnnotation(Implemented)
                .by() == SaveImplementer

        and: 'saveFoo(String) is also implemented by SaveImplementer'
        impl.getMethod('saveFoo', String)
                .getAnnotation(Implemented)
                .by() == SaveImplementer
    }

    void 'test delete by id with @Transactional(connection) routes through connection-aware API'() {
        when: "an abstract data service with @Transactional(connection='secondary') declares delete(Serializable)"
        def service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Transactional

@Service(Bar)
@Transactional(connection = 'secondary')
abstract class BarService {

    abstract Bar delete(Serializable id)

    abstract void deleteBar(Serializable id)

    abstract Number deleteMoreBars(String title)
}

@Entity
class Bar {
    String title

    static mapping = {
        datasource 'secondary'
    }
}
''')

        then: 'the class compiles without errors'
        !service.isInterface()

        when: 'the implementation is loaded'
        def impl = service.classLoader.loadClass('$BarServiceImplementation')

        then: 'the implementation exists and inherits the connection-aware @Transactional'
        impl != null
        impl.getAnnotation(Transactional) != null
        impl.getAnnotation(Transactional).connection() == 'secondary'

        and: 'delete(Serializable) returning domain type is implemented by FindAndDeleteImplementer'
        impl.getMethod('delete', Serializable)
                .getAnnotation(Implemented)
                .by() == FindAndDeleteImplementer

        and: 'void deleteBar(Serializable) is implemented by DeleteImplementer'
        impl.getMethod('deleteBar', Serializable)
                .getAnnotation(Implemented)
                .by() == DeleteImplementer

        and: 'deleteMoreBars(String) returning Number is implemented by DeleteImplementer'
        impl.getMethod('deleteMoreBars', String)
                .getAnnotation(Implemented)
                .by() == DeleteImplementer
    }

    void "test find by id with @Transactional(connection) routes through connection-aware API"() {
        when: "an abstract data service with @Transactional(connection='secondary') declares find(Serializable)"
        def service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Transactional

@Service(Baz)
@Transactional(connection = 'secondary')
abstract class BazService {

    abstract Baz find(Serializable id)

    abstract Baz get(Serializable id)

    abstract List<Baz> findByTitle(String title)
}

@Entity
class Baz {
    String title

    static mapping = {
        datasource 'secondary'
    }
}
''')

        then: 'the class compiles without errors'
        !service.isInterface()

        when: 'the implementation is loaded'
        def impl = service.classLoader.loadClass('$BazServiceImplementation')

        then: 'the implementation exists and inherits the connection-aware @Transactional'
        impl != null
        impl.getAnnotation(Transactional) != null
        impl.getAnnotation(Transactional).connection() == 'secondary'

        and: 'find(Serializable) is implemented by FindOneImplementer'
        impl.getMethod('find', Serializable)
                .getAnnotation(Implemented)
                .by() == FindOneImplementer

        and: 'get(Serializable) is implemented by FindOneImplementer'
        impl.getMethod('get', Serializable)
                .getAnnotation(Implemented)
                .by() == FindOneImplementer
    }

    void "test interface service with @Transactional(connection) compiles all CRUD methods"() {
        when: "an interface data service with @Transactional(connection='secondary') declares CRUD methods"
        def service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Transactional

@Service(Widget)
@Transactional(connection = 'secondary')
interface WidgetService {

    Widget save(Widget widget)

    Widget saveFoo(String name)

    Widget find(Serializable id)

    Widget get(Serializable id)

    Widget delete(Serializable id)

    void deleteWidget(Serializable id)

    Number deleteMoreWidgets(String name)

    List<Widget> findByName(String name)
}

@Entity
class Widget {
    String name

    static mapping = {
        datasource 'secondary'
    }
}
''')

        then: 'the interface compiles without errors'
        service.isInterface()

        when: 'the implementation is loaded'
        def impl = service.classLoader.loadClass('$WidgetServiceImplementation')

        then: 'the implementation exists and inherits the connection-aware @Transactional'
        impl != null
        impl.getAnnotation(Transactional) != null
        impl.getAnnotation(Transactional).connection() == 'secondary'

        and: 'save(Widget) is implemented'
        impl.getMethod('save', impl.classLoader.loadClass('Widget'))
                .getAnnotation(Implemented)
                .by() == SaveImplementer

        and: 'find(Serializable) is implemented'
        impl.getMethod('find', Serializable)
                .getAnnotation(Implemented)
                .by() == FindOneImplementer

        and: 'delete(Serializable) returning domain type is implemented by FindAndDeleteImplementer'
        impl.getMethod('delete', Serializable)
                .getAnnotation(Implemented)
                .by() == FindAndDeleteImplementer

        and: 'void deleteWidget(Serializable) is implemented by DeleteImplementer'
        impl.getMethod('deleteWidget', Serializable)
                .getAnnotation(Implemented)
                .by() == DeleteImplementer

        and: 'deleteMoreWidgets(String) returning Number is implemented by DeleteImplementer'
        impl.getMethod('deleteMoreWidgets', String)
                .getAnnotation(Implemented)
                .by() == DeleteImplementer
    }

    void "test service without @Transactional(connection) still compiles CRUD correctly"() {
        when: 'a data service WITHOUT connection annotation declares CRUD methods'
        def service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity

@Service(Thing)
interface ThingService {

    Thing save(Thing thing)

    Thing find(Serializable id)

    Thing delete(Serializable id)

    void deleteThing(Serializable id)

    List<Thing> findByTitle(String title)
}

@Entity
class Thing {
    String title
}
''')

        then: 'the interface compiles without errors'
        service.isInterface()

        when: 'the implementation is loaded'
        def impl = service.classLoader.loadClass('$ThingServiceImplementation')

        then: 'the implementation exists'
        impl != null

        and: 'save is implemented by SaveImplementer'
        impl.getMethod('save', impl.classLoader.loadClass('Thing'))
                .getAnnotation(Implemented)
                .by() == SaveImplementer

        and: 'find is implemented by FindOneImplementer'
        impl.getMethod('find', Serializable)
                .getAnnotation(Implemented)
                .by() == FindOneImplementer

        and: 'delete returning domain type is implemented by FindAndDeleteImplementer'
        impl.getMethod('delete', Serializable)
                .getAnnotation(Implemented)
                .by() == FindAndDeleteImplementer

        and: 'void deleteThing is implemented by DeleteImplementer'
        impl.getMethod("deleteThing", Serializable)
                .getAnnotation(Implemented)
                .by() == DeleteImplementer
    }

    void 'test save/delete/get with connection actually invoke connection-aware API at runtime'() {
        when: 'a service with connection routing is instantiated and methods are called'
        def service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Transactional

@Service(Gadget)
@Transactional(connection = 'secondary')
interface GadgetService {

    Gadget save(Gadget gadget)

    Gadget find(Serializable id)

    Gadget delete(Serializable id)
}

@Entity
class Gadget {
    String label

    static mapping = {
        datasource 'secondary'
    }
}
''')
        def impl = service.classLoader.loadClass('$GadgetServiceImplementation')
        def instance = impl.getDeclaredConstructor().newInstance()

        then: 'calling save throws IllegalStateException (no GORM backend) rather than routing to wrong datasource'
        // This confirms the generated code attempts to use GormEnhancer APIs
        // (which require an initialized datastore), rather than calling entity.save() directly
        when:
        instance.save(service.classLoader.loadClass('Gadget').getDeclaredConstructor().newInstance())

        then:
        thrown(IllegalStateException)

        when:
        instance.find(1L)

        then:
        thrown(IllegalStateException)

        when:
        instance.delete(1L)

        then:
        thrown(IllegalStateException)
    }

}
