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

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.generator.EventType
import org.hibernate.generator.GeneratorCreationContext
import jakarta.persistence.GenerationType
import spock.lang.Subject

import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsNativeGenerator

class GrailsNativeGeneratorSpec extends HibernateGormDatastoreSpec {

    def "should return currentValue if not null (assigned identifier)"() {
        given:
        def context = Mock(GeneratorCreationContext)
        def database = Mock(org.hibernate.boot.model.relational.Database)
        context.getDatabase() >> database
        database.getDialect() >> getGrailsDomainBinder().getJdbcEnvironment().getDialect()
        
        def session = Mock(SharedSessionContractImplementor)
        def entity = new Object()
        def currentValue = 123L
        def eventType = EventType.INSERT
        
        @Subject
        def generator = Spy(GrailsNativeGenerator, constructorArgs: [context])

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
        database.getDialect() >> getGrailsDomainBinder().getJdbcEnvironment().getDialect()
        
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
}
