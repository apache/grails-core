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
package org.grails.datastore.gorm.validation.constraints.eval

import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.types.TenantId
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings.MultiTenancyMode
import org.springframework.context.support.StaticMessageSource
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

class DefaultConstraintEvaluatorSpec extends Specification {

    void "test importFrom honors default nullable"() {
        given:
        def evaluator = new DefaultConstraintEvaluator(new DefaultConstraintRegistry(new StaticMessageSource()), new KeyValueMappingContext("test"), Collections.emptyMap())

        when:
        def constraints = evaluator.evaluate(Constraints, true)

        then:
        constraints.name.isNullable()
        !constraints.name.isBlank()

        when:
        constraints = evaluator.evaluate(MoreConstraints, true)

        then:
        constraints.name.isNullable()
        !constraints.name.isBlank()

        when:
        constraints = evaluator.evaluate(MoreConstraints, false)

        then:
        !constraints.name.isNullable()
        !constraints.name.isBlank()
    }

    @Unroll
    @Issue('https://github.com/apache/grails-core/issues/14365')
    void 'a tenant id keeps the constraints declared on it, and gets no default constraints, in #mode mode'() {
        given:
        def evaluator = new DefaultConstraintEvaluator(new DefaultConstraintRegistry(new StaticMessageSource()),
                mappingContext(mode), Collections.emptyMap())

        when:
        def constraints = evaluator.evaluate(TenantCustomer)

        then: 'the tenant id keeps what is declared on it'
        constraints.firmId.hasAppliedConstraint('nullable')
        !constraints.firmId.nullable
        constraints.firmId.maxSize == 5

        and: 'the other properties get their default constraints, as before'
        constraints.name.hasAppliedConstraint('nullable')

        when:
        constraints = evaluator.evaluate(TenantOrder)

        then: 'a tenant id declared without nullable is not given the default nullable'
        constraints.firmId.maxSize == 5
        !constraints.firmId.hasAppliedConstraint('nullable')

        when:
        constraints = evaluator.evaluate(TenantInvoice)

        then: 'a tenant id without declared constraints is not constrained'
        !constraints.containsKey('tenantId')
        constraints.number.hasAppliedConstraint('nullable')

        where:
        mode << MultiTenancyMode.values()
    }

    @Issue('https://github.com/apache/grails-core/issues/14365')
    void 'a tenant id gets a shared constraint declared on it, but not the global default constraints'() {
        given:
        def evaluator = new DefaultConstraintEvaluator(new DefaultConstraintRegistry(new StaticMessageSource()),
                mappingContext(MultiTenancyMode.DISCRIMINATOR), ['*': [blank: false], tenantCode: [maxSize: 3]])

        when:
        def constraints = evaluator.evaluate(TenantOrder)

        then:
        constraints.name.hasAppliedConstraint('blank')
        !constraints.firmId.hasAppliedConstraint('blank')

        when:
        constraints = evaluator.evaluate(TenantAccount)

        then:
        constraints.firmId.maxSize == 3
        !constraints.firmId.hasAppliedConstraint('blank')

        when:
        constraints = evaluator.evaluate(TenantInvoice)

        then: 'a tenant id without declared constraints is not constrained by the global default constraints either'
        !constraints.containsKey('tenantId')
        constraints.number.hasAppliedConstraint('blank')
    }

    @Unroll
    @Issue('https://github.com/apache/grails-core/issues/14365')
    void 'a tenant id named tenantId keeps the constraints declared on it, and gets no default constraints, in #mode mode'() {
        given:
        def evaluator = new DefaultConstraintEvaluator(new DefaultConstraintRegistry(new StaticMessageSource()),
                mappingContext(mode), ['*': [blank: false]])

        when:
        def constraints = evaluator.evaluate(TenantLedger)

        then: 'it is the tenant id, as it is when nothing is declared on it'
        !constraints.tenantId.nullable
        constraints.tenantId.maxSize == 5
        !constraints.tenantId.hasAppliedConstraint('blank')
        constraints.number.hasAppliedConstraint('blank')

        when:
        constraints = evaluator.evaluate(TenantVoucher)

        then:
        constraints.tenantId.maxSize == 5
        !constraints.tenantId.hasAppliedConstraint('nullable')
        !constraints.tenantId.hasAppliedConstraint('blank')

        where:
        mode << MultiTenancyMode.values()
    }

    private static KeyValueMappingContext mappingContext(MultiTenancyMode mode) {
        def settings = new ConnectionSourceSettings()
        settings.multiTenancy.mode = mode
        def mappingContext = new KeyValueMappingContext('test', settings)
        mappingContext.addPersistentEntities(TenantCustomer, TenantOrder, TenantInvoice, TenantAccount, TenantLedger,
                TenantVoucher)
        // The tenant id is mapped as one in every mode, though only discriminator mode assigns it
        assert mappingContext.getPersistentEntity(TenantCustomer.name).getPropertyByName('firmId') instanceof TenantId
        assert mappingContext.getPersistentEntity(TenantInvoice.name).getPropertyByName('tenantId') instanceof TenantId
        assert mappingContext.getPersistentEntity(TenantLedger.name).getPropertyByName('tenantId') instanceof TenantId
        assert mappingContext.getPersistentEntity(TenantVoucher.name).getPropertyByName('tenantId') instanceof TenantId
        return mappingContext
    }

    class Constraints {
        String name
        static constraints = {
            name blank: false
        }
    }

    class MoreConstraints {
        String name
        static constraints = {
            importFrom Constraints
        }
    }
}

// Stands for grails.gorm.MultiTenant, which this module does not depend on: GORM recognises a multi-tenant entity by
// the simple name of the interface it implements
interface MultiTenant {
}

class TenantCustomer implements MultiTenant {
    Long id
    String name
    String firmId

    static constraints = {
        firmId nullable: false, maxSize: 5
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

class TenantInvoice implements MultiTenant {
    Long id
    String number
    String tenantId
}

class TenantOrder implements MultiTenant {
    Long id
    String name
    String firmId

    static constraints = {
        firmId maxSize: 5
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

class TenantAccount implements MultiTenant {
    Long id
    String firmId

    static constraints = {
        firmId shared: 'tenantCode'
    }

    static mapping = {
        tenantId name: 'firmId'
    }
}

class TenantLedger implements MultiTenant {
    Long id
    String number
    String tenantId

    static constraints = {
        tenantId nullable: false, maxSize: 5
    }
}

class TenantVoucher implements MultiTenant {
    Long id
    String number
    String tenantId

    static constraints = {
        tenantId maxSize: 5
    }
}
