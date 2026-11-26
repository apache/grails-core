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
package grails.gorm.tests.inheritance

import org.hibernate.Hibernate

import grails.gorm.annotation.Entity
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * A to-one association typed as the root of a hierarchy is loaded as a proxy of the root class. Dynamic
 * Groovy code must be able to call what a subclass implements or overrides through that proxy, before
 * and after the proxy is initialized.
 */
class InitializedSubclassProxySpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.registerDomainClasses(Shape, Circle, Drawing)
    }

    void "a method only the subclass implements is callable through an initialized proxy of the superclass"() {
        given:
        Shape shape = loadShapeProxy()
        Hibernate.initialize(shape)

        expect:
        manager.session.mappingContext.proxyFactory.isProxy(shape)
        kindPropertyOf(shape) == 'circle'
        kindOf(shape) == 'circle'
    }

    void "a method the subclass overrides is callable through an initialized proxy of the superclass"() {
        given:
        Shape shape = loadShapeProxy()
        Hibernate.initialize(shape)

        expect:
        manager.session.mappingContext.proxyFactory.isProxy(shape)
        descriptionOf(shape) == 'a circle named round'
    }

    void "a subclass method stays callable after the call that initializes the proxy"() {
        given:
        Shape shape = loadShapeProxy()

        expect: 'the first call initializes the proxy, the second goes through the initialized proxy'
        !Hibernate.isInitialized(shape)
        kindOf(shape) == 'circle'
        Hibernate.isInitialized(shape)
        kindOf(shape) == 'circle'
    }

    private Shape loadShapeProxy() {
        new Drawing(shape: new Circle(name: 'round').save(failOnError: true)).save(failOnError: true)
        manager.session.flush()
        manager.session.clear()
        Drawing.first().@shape
    }

    void "a #persistentClass.simpleName proxy keeps its metaclass after initialization"() {
        given:
        Long id = new Circle(name: 'round').save(failOnError: true).id
        manager.session.flush()
        manager.session.clear()
        Shape shape = persistentClass.load(id)

        expect:
        !Hibernate.isInitialized(shape)
        shape.getMetaClass().theClass == persistentClass
        metaClassTypeOf(shape) == persistentClass
        metaClassPropertyTypeOf(shape) == persistentClass
        !Hibernate.isInitialized(shape)

        when:
        Hibernate.initialize(shape)

        then:
        Hibernate.isInitialized(shape)
        Hibernate.unproxy(shape) instanceof Circle
        shape.getMetaClass().theClass == persistentClass
        metaClassTypeOf(shape) == persistentClass
        metaClassPropertyTypeOf(shape) == persistentClass
        shape.name == 'round'
        kindOf(shape) == 'circle'
        descriptionOf(shape) == 'a circle named round'

        where:
        persistentClass << [Shape, Circle]
    }

    // Spock rewrites the calls in a condition, so the calls under test are made from ordinary methods

    private static Class metaClassTypeOf(Shape shape) {
        shape.metaClass.theClass
    }

    private static Class metaClassPropertyTypeOf(Shape shape) {
        shape.getProperty('metaClass').theClass
    }

    private static String kindPropertyOf(Shape shape) {
        shape.kind
    }

    private static String kindOf(Shape shape) {
        shape.getKind()
    }

    private static String descriptionOf(Shape shape) {
        shape.describe()
    }
}

@Entity
abstract class Shape {

    String name

    static transients = ['kind']

    abstract String getKind()

    String describe() {
        "a shape named $name"
    }
}

@Entity
class Circle extends Shape {

    @Override
    String getKind() {
        'circle'
    }

    @Override
    String describe() {
        "a circle named $name"
    }
}

@Entity
class Drawing {
    Shape shape
}
