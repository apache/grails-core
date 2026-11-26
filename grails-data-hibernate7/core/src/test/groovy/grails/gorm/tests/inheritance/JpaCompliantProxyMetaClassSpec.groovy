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

import org.apache.grails.data.hibernate7.core.GrailsDataHibernate7TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * With {@code hibernate.jpa.compliance.proxy} enabled, Hibernate initializes a proxy when its identifier
 * is read. Asking a proxy for its metaclass must not read the identifier, so that it does not load the
 * entity.
 */
class JpaCompliantProxyMetaClassSpec extends GrailsDataTckSpec<GrailsDataHibernate7TckManager> {

    void setupSpec() {
        // nested config: only nested config reaches the arbitrary hibernate settings map, producing the
        // flattened hibernate.jpa.compliance.proxy property
        manager.grailsConfig.hibernate.jpa.compliance.proxy = 'true'
        manager.registerDomainClasses(Shape, Circle, Drawing)
    }

    void "the setting is in effect: the identifier getter initializes the proxy"() {
        given:
        Shape shape = loadShapeProxy()

        expect:
        !Hibernate.isInitialized(shape)
        shape.getId()
        Hibernate.isInitialized(shape)
    }

    void "asking an uninitialized proxy for its metaclass through #request does not initialize it"() {
        given:
        Shape shape = loadShapeProxy()

        expect:
        !Hibernate.isInitialized(shape)
        metaClassType(shape) == Shape
        !Hibernate.isInitialized(shape)

        where:
        request                    | metaClassType
        'getMetaClass()'           | { Shape proxy -> proxy.getMetaClass().theClass }
        'the metaClass property'   | { Shape proxy -> metaClassTypeOf(proxy) }
        "getProperty('metaClass')" | { Shape proxy -> metaClassPropertyTypeOf(proxy) }
    }

    private Shape loadShapeProxy() {
        Long id = new Circle(name: 'round').save(failOnError: true).id
        manager.session.flush()
        manager.session.clear()
        Shape.load(id)
    }

    // Spock rewrites the calls in a condition, so the calls under test are made from ordinary methods

    private static Class metaClassTypeOf(Shape shape) {
        shape.metaClass.theClass
    }

    private static Class metaClassPropertyTypeOf(Shape shape) {
        shape.getProperty('metaClass').theClass
    }
}
