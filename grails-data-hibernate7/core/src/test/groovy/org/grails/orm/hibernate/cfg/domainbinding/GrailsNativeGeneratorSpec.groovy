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
package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import jakarta.persistence.GenerationType
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsNativeGenerator
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.generator.EventType
import org.hibernate.generator.Generator
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.mapping.Column
import org.hibernate.mapping.Property
import org.hibernate.mapping.Value
import org.hibernate.type.Type
import spock.lang.Subject

import java.lang.reflect.Field

class GrailsNativeGeneratorSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(NativeGeneratorSpecEntity)
    }

    /** A creation context backed by the running H2 datastore, whose native strategy is IDENTITY. */
    private GeneratorCreationContext buildContext() {
        def column = new Column("id")
        def value = Mock(Value) {
            getColumns() >> [column]
        }
        def property = Mock(Property) {
            getName() >> "id"
            getValue() >> value
        }
        def type = Mock(Type) {
            getReturnedClass() >> Long
        }
        Mock(GeneratorCreationContext) {
            getServiceRegistry() >> serviceRegistry
            getDatabase() >> datastore.metadata.database
            getProperty() >> property
            getValue() >> value
            getType() >> type
        }
    }

    private JdbcEnvironment jdbcEnvironment() {
        serviceRegistry.requireService(JdbcEnvironment)
    }

    def "should mark the identifier column as identity when the dialect generates identity values"() {
        given:
        def context = buildContext()

        when:
        def generator = new GrailsNativeGenerator(context, jdbcEnvironment())

        then:
        generator.generationType == GenerationType.IDENTITY
        context.property.value.columns[0].identity
    }

    def "should return currentValue if not null (assigned identifier)"() {
        given:
        def session = Mock(SharedSessionContractImplementor)
        def entity = new Object()
        def currentValue = "assigned-id"
        def generator = new GrailsNativeGenerator(buildContext(), jdbcEnvironment())

        when:
        def result = generator.generate(session, entity, currentValue, EventType.INSERT)

        then:
        result == currentValue
    }

    def "should return null if generation type is IDENTITY"() {
        given:
        def session = Mock(SharedSessionContractImplementor)
        def entity = new Object()

        @Subject
        def generator = Spy(GrailsNativeGenerator, constructorArgs: [buildContext(), jdbcEnvironment()])
        generator.getGenerationType() >> GenerationType.IDENTITY

        when:
        def result = generator.generate(session, entity, null, EventType.INSERT)

        then:
        result == null
    }

    def "should delegate to the standard logic when the delegate is not an identity generator"() {
        given:
        def session = Mock(SharedSessionContractImplementor)
        def entity = new Object()

        @Subject
        def generator = Spy(GrailsNativeGenerator, constructorArgs: [buildContext(), jdbcEnvironment()])

        Field field = org.hibernate.id.NativeGenerator.getDeclaredField("dialectNativeGenerator")
        field.setAccessible(true)
        field.set(generator, Mock(Generator))

        generator.getGenerationType() >> GenerationType.SEQUENCE

        when:
        // super.generate() casts the delegate to BeforeExecutionGenerator, which the mock is not
        generator.generate(session, entity, null, EventType.INSERT)

        then:
        thrown(ClassCastException)
    }
}

@Entity
class NativeGeneratorSpecEntity {
    String name
}
