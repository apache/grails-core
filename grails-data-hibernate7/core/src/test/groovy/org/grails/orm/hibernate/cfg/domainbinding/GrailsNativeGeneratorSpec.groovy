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

import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsNativeGenerator
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.generator.EventType
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.id.enhanced.SequenceStyleGenerator
import spock.lang.Specification
import spock.lang.Subject
import jakarta.persistence.GenerationType

class GrailsNativeGeneratorSpec extends Specification {

    def "should return currentValue if not null (assigned identifier)"() {
        given:
        def context = Mock(GeneratorCreationContext)
        def session = Mock(SharedSessionContractImplementor)
        def entity = new Object()
        def currentValue = "assigned-id"
        def eventType = EventType.INSERT
        
        def generator = new GrailsNativeGenerator(context)

        when:
        def result = generator.generate(session, entity, currentValue, eventType)

        then:
        result == currentValue
    }

    def "should return null if generation type is IDENTITY"() {
        given:
        def context = Mock(GeneratorCreationContext)
        def database = Mock(org.hibernate.boot.model.relational.Database)
        context.getDatabase() >> database
        database.getDialect() >> new org.hibernate.dialect.H2Dialect()
        
        def session = Mock(SharedSessionContractImplementor)
        def entity = new Object()
        def eventType = EventType.INSERT
        
        @Subject
        def generator = Spy(GrailsNativeGenerator, constructorArgs: [context])
        generator.getGenerationType() >> GenerationType.IDENTITY

        when:
        def result = generator.generate(session, entity, null, eventType)

        then:
        result == null
    }

    def "should throw HibernateException if SequenceStyleGenerator is not initialized"() {
        given:
        def context = Mock(GeneratorCreationContext)
        def database = Mock(org.hibernate.boot.model.relational.Database)
        context.getDatabase() >> database
        database.getDialect() >> new org.hibernate.dialect.H2Dialect()
        
        def session = Mock(SharedSessionContractImplementor)
        def entity = new Object()
        def eventType = EventType.INSERT
        
        @Subject
        def generator = Spy(GrailsNativeGenerator, constructorArgs: [context])
        def ssg = Mock(SequenceStyleGenerator)
        
        // We need to mock the private field access or ensure getDelegate() returns ssg
        // Since we are using Spy and getDelegate is not easily overridable if private
        // but our implementation uses reflection. In the test, we'll mock the field.
        
        java.lang.reflect.Field field = org.hibernate.id.NativeGenerator.class.getDeclaredField("dialectNativeGenerator")
        field.setAccessible(true)
        field.set(generator, ssg)

        generator.getGenerationType() >> GenerationType.SEQUENCE
        ssg.getDatabaseStructure() >> null

        when:
        generator.generate(session, entity, null, eventType)

        then:
        def e = thrown(org.hibernate.HibernateException)
        e.message.contains("was not properly initialized")
    }
}
