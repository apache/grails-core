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

package org.grails.gorm.rx.transform

import org.grails.datastore.gorm.services.implementers.FindAllImplementer
import org.grails.datastore.gorm.services.implementers.FindOneImplementer
import org.grails.datastore.mapping.services.Service
import rx.Observable
import rx.Single
import spock.lang.Specification

/**
 * Exercises the {@code @RxSchedule} AST transformation ({@link RxScheduleIOTransformation}) and the
 * AST helper methods on {@link RxAstUtils} it depends on, through the public {@code @Service} /
 * {@code @RxSchedule} annotations, in the same way {@code ObservableServiceTransformSpec} does.
 */
class RxScheduleTransformSpec extends Specification {

    void "test @RxSchedule with the default scheduler on an Observable returning method"() {
        given:
        ClassLoader cl = new GroovyShell().evaluate('''
import grails.gorm.annotation.Entity
import grails.gorm.rx.services.RxSchedule
import grails.gorm.services.Service
import rx.Observable
import org.grails.gorm.rx.services.implementers.*

@Entity
class Book {
  String title
}
@Service(value=Book, adapters = [ObservableServiceImplementerAdapter])
interface BookService {
    @RxSchedule
    Observable<Book> find(String title)
}
return Book.classLoader
''')

        when:
        Class impl = cl.loadClass('$BookServiceImplementation')

        then:
        Service.isAssignableFrom(impl)
        impl.getMethod("find", String).getAnnotation(org.grails.datastore.gorm.services.Implemented).by() == FindAllImplementer
        impl.declaredMethods*.name.any { it.startsWith(RxScheduleIOTransformation.RENAMED_METHOD_PREFIX) }

        when:"the decorated method is invoked"
        def instance = impl.newInstance()
        Observable o = instance.find("test")

        then:"an observable is returned"
        o != null

        when:"the observable result is produced"
        o.toBlocking().first()

        then:"the underlying GORM call fails because no datastore is configured"
        def e = thrown(IllegalStateException)
        e.message.startsWith('No GORM implementations configured')
    }

    void "test @RxSchedule with the default scheduler on a Single returning method"() {
        given:
        ClassLoader cl = new GroovyShell().evaluate('''
import grails.gorm.annotation.Entity
import grails.gorm.rx.services.RxSchedule
import grails.gorm.services.Service
import rx.Single
import org.grails.gorm.rx.services.implementers.*

@Entity
class Book {
  String title
}
@Service(value=Book, adapters = [ObservableServiceImplementerAdapter])
interface BookService {
    @RxSchedule
    Single<Book> find(String title)
}
return Book.classLoader
''')

        when:
        Class impl = cl.loadClass('$BookServiceImplementation')

        then:
        Service.isAssignableFrom(impl)
        impl.getMethod("find", String).getAnnotation(org.grails.datastore.gorm.services.Implemented).by() == FindOneImplementer
        impl.declaredMethods*.name.any { it.startsWith(RxScheduleIOTransformation.RENAMED_METHOD_PREFIX) }

        when:"the decorated method is invoked"
        def instance = impl.newInstance()
        Single o = instance.find("test")

        then:"a single is returned"
        o != null

        when:"the single result is produced"
        o.toBlocking().value()

        then:"the underlying GORM call fails because no datastore is configured"
        def e = thrown(IllegalStateException)
        e.message.startsWith('No GORM implementations configured')
    }

    void "test that a domain class implementing RxEntity is not automatically wrapped with RxSchedule"() {
        given:"a domain class that already implements RxEntity directly"
        ClassLoader cl = new GroovyShell().evaluate('''
import grails.gorm.rx.RxEntity
import grails.gorm.services.Service
import jakarta.persistence.Entity
import rx.Observable
import org.grails.gorm.rx.services.implementers.*

@Entity
class Book implements RxEntity<Book> {
  String title
}
@Service(value=Book, adapters = [ObservableServiceImplementerAdapter])
interface BookService {
    Observable<Book> find(String title)
}
return Book.classLoader
''')

        when:"the generated implementation is loaded"
        Class impl = cl.loadClass('$BookServiceImplementation')

        then:"the method is implemented normally"
        Service.isAssignableFrom(impl)
        impl.getMethod("find", String).getAnnotation(org.grails.datastore.gorm.services.Implemented).by() == FindAllImplementer

        and:"RxSchedule was not woven in automatically because the domain class is already an RxEntity"
        impl.declaredMethods*.name.every { !it.startsWith(RxScheduleIOTransformation.RENAMED_METHOD_PREFIX) }
    }
}
