/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.cfg.domainbinding.util

import org.hibernate.boot.model.relational.Database
import org.hibernate.boot.model.relational.SqlStringGenerationContext
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.mapping.Property
import org.hibernate.mapping.Value
import org.hibernate.service.ServiceRegistry
import org.hibernate.type.Type
import spock.lang.Specification
import spock.lang.Subject

class GeneratorCreationContextWrapperSpec extends Specification {

    def delegate = Mock(GeneratorCreationContext)
    def overrideValue = Mock(Value)

    @Subject
    GeneratorCreationContextWrapper wrapper

    def setup() {
        wrapper = new GeneratorCreationContextWrapper(delegate, overrideValue)
    }

    def 'getValue returns the override value when it is not null'() {
        when:
        def result = wrapper.getValue()

        then:
        result.is(overrideValue)
        0 * delegate.getValue()
    }

    def 'getValue falls back to delegate when override value is null'() {
        given:
        def delegateValue = Mock(Value)
        wrapper = new GeneratorCreationContextWrapper(delegate, null)

        when:
        def result = wrapper.getValue()

        then:
        1 * delegate.getValue() >> delegateValue
        result.is(delegateValue)
    }

    def 'getDatabase delegates to the wrapped context'() {
        given:
        def db = Mock(Database)

        when:
        def result = wrapper.getDatabase()

        then:
        1 * delegate.getDatabase() >> db
        result.is(db)
    }

    def 'getServiceRegistry delegates to the wrapped context'() {
        given:
        def registry = Mock(ServiceRegistry)

        when:
        def result = wrapper.getServiceRegistry()

        then:
        1 * delegate.getServiceRegistry() >> registry
        result.is(registry)
    }

    def 'getDefaultCatalog delegates to the wrapped context'() {
        when:
        def result = wrapper.getDefaultCatalog()

        then:
        1 * delegate.getDefaultCatalog() >> 'my_catalog'
        result == 'my_catalog'
    }

    def 'getDefaultSchema delegates to the wrapped context'() {
        when:
        def result = wrapper.getDefaultSchema()

        then:
        1 * delegate.getDefaultSchema() >> 'my_schema'
        result == 'my_schema'
    }

    def 'getPersistentClass delegates to the wrapped context'() {
        when:
        def result = wrapper.getPersistentClass()

        then:
        1 * delegate.getPersistentClass() >> null
        result == null
    }

    def 'getRootClass delegates to the wrapped context'() {
        when:
        def result = wrapper.getRootClass()

        then:
        1 * delegate.getRootClass() >> null
        result == null
    }

    def 'getProperty delegates to the wrapped context'() {
        given:
        def property = Mock(Property)

        when:
        def result = wrapper.getProperty()

        then:
        1 * delegate.getProperty() >> property
        result.is(property)
    }

    def 'getType delegates to the wrapped context'() {
        given:
        def type = Mock(Type)

        when:
        def result = wrapper.getType()

        then:
        1 * delegate.getType() >> type
        result.is(type)
    }

    def 'getSqlStringGenerationContext delegates to the wrapped context'() {
        given:
        def ctx = Mock(SqlStringGenerationContext)

        when:
        def result = wrapper.getSqlStringGenerationContext()

        then:
        1 * delegate.getSqlStringGenerationContext() >> ctx
        result.is(ctx)
    }
}
