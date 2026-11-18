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
package org.grails.datastore.gorm

import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.CurrentTenantHolder
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionCallback
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * {@code AbstractGormApi} is exercised extensively but only incidentally through
 * {@code GormStaticApi}/{@code GormInstanceApi}/{@code GormValidationApi}'s own specs. This spec
 * targets what those miss: the null-datastore guard in {@code execute()}, the bound-tenant
 * delegation branch when the DEFAULT qualifier is in use (tested against a minimal purpose-built
 * subclass so {@code executeQualified}'s dispatch can be observed directly, rather than routing
 * through {@code GormStaticApi}'s own - singleton-registry-dependent - resolution), the
 * reflection-based {@code getMethods()}/{@code getExtendedMethods()} cataloging (never exercised
 * by any prior item), and the unused {@code ConstantDatastoreResolver} helper.
 */
class AbstractGormApiSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore

    void setup() {
        GormRegistry.instance.reset()
        datastore = new SimpleMapDatastore(AbstractGormApiThing)
    }

    void cleanup() {
        GormRegistry.instance.reset()
    }

    void "execute throws IllegalStateException when the resolved datastore is null"() {
        given:
        def resolver = new DatastoreResolver() {
            @Override Datastore resolve() { null }
        }
        def api = new GormStaticApi(AbstractGormApiThing, datastore.mappingContext, [], resolver, ConnectionSource.DEFAULT, new GormRegistry())

        when:
        api.get(1L)

        then:
        thrown(IllegalStateException)
    }

    void "execute delegates to executeQualified using the currently bound tenant when the DEFAULT qualifier is in use"() {
        given: "a multi-tenant-capable datastore with a tenant already bound"
        def mtds = Stub(MixedDatastore)
        def api = new MinimalGormApi(AbstractGormApiTenantThing, mtds, new GormRegistry())
        CurrentTenantHolder.set(mtds, 'tenant1')

        when:
        def result = api.execute({ Session s -> 'unused' } as SessionCallback)

        then: "the bound tenant id is passed through as the qualifier, not the DEFAULT qualifier the api was constructed with"
        api.qualifiedCallQualifier == 'tenant1'
        result == 'qualified-result'

        cleanup:
        CurrentTenantHolder.remove(mtds)
    }

    void "execute runs the callback directly against the datastore when no tenant is bound and the qualifier is DEFAULT"() {
        given:
        def mtds = Stub(MixedDatastore)
        def session = Stub(Session) { getDatastore() >> mtds }
        mtds.connect() >> session
        def api = new MinimalGormApi(AbstractGormApiTenantThing, mtds, new GormRegistry())

        when:
        def result = api.execute({ Session s -> 'ran-directly' } as SessionCallback)

        then:
        api.qualifiedCallQualifier == null
        result == 'ran-directly'
    }

    void "getMethods and getExtendedMethods catalog the api's public methods, initializing lazily and caching thereafter"() {
        given:
        def api = new CustomGormApi(AbstractGormApiThing, datastore)

        when:
        def methods = api.getMethods()
        def extendedMethods = api.getExtendedMethods()

        then: "getMethods includes both inherited GormStaticApi methods and the subclass's own"
        methods.any { it.name == 'get' }
        methods.any { it.name == 'customExtraMethod' }

        and: "getExtendedMethods includes only methods declared beyond the standard Gorm*Api classes"
        extendedMethods.any { it.name == 'customExtraMethod' }
        !extendedMethods.any { it.name == 'get' }

        and: "excluded Object/GroovyObject/GORM-housekeeping methods never appear in either list"
        ['getClass', 'getMetaClass', 'setMetaClass', 'getProperty', 'setProperty', 'invokeMethod',
         'wait', 'notify', 'notifyAll', 'equals', 'toString', 'hashCode', 'getMethods',
         'getExtendedMethods', 'getTransactionManager', 'setTransactionManager'].every { excluded ->
            !methods.any { it.name == excluded }
        }

        when: "requesting the methods again"
        def methodsAgain = api.getMethods()

        then: "the same cached list instance is returned"
        methodsAgain.is(methods)
    }

    void "EXCLUDES is publicly accessible and lists the exact Object/GroovyObject/GORM-housekeeping method names to hide"() {
        expect: "the field is reachable as a public static Groovy property, not just protected"
        AbstractGormApi.EXCLUDES.is(AbstractGormApi.getEXCLUDES())

        and: "the exact set jdaugherty's review restored, plus getTransactionManager for the new abstract getter"
        AbstractGormApi.EXCLUDES as Set == [
            'setProperty', 'getProperty', 'getMetaClass', 'setMetaClass', 'invokeMethod',
            'getMethods', 'getExtendedMethods', 'wait', 'equals', 'toString', 'hashCode',
            'getClass', 'notify', 'notifyAll', 'setTransactionManager', 'getTransactionManager'
        ] as Set
    }

    void "ConstantDatastoreResolver always resolves to the datastore it was constructed with"() {
        given:
        def ds = Stub(Datastore)
        def resolver = new AbstractGormApi.ConstantDatastoreResolver(ds)

        expect:
        resolver.resolve().is(ds)
    }

    interface MixedDatastore extends MultiTenantCapableDatastore, Datastore {}

    static class MinimalGormApi extends AbstractGormApi<AbstractGormApiTenantThing> {
        String qualifiedCallQualifier

        MinimalGormApi(Class cls, Datastore ds, GormRegistry registry) {
            super(cls, ds, registry)
        }

        @Override
        protected <T1> T1 executeQualified(String qualifier, SessionCallback<T1> callback) {
            qualifiedCallQualifier = qualifier
            return (T1) 'qualified-result'
        }

        @Override
        PlatformTransactionManager getTransactionManager() { null }
    }

    static class CustomGormApi extends GormStaticApi<AbstractGormApiThing> {
        CustomGormApi(Class<AbstractGormApiThing> cls, Datastore ds) {
            super(cls, ds, [])
        }

        String customExtraMethod() { 'custom' }
    }
}

@Entity
class AbstractGormApiThing {
    Long id
    String name
}

class AbstractGormApiTenantThing implements grails.gorm.MultiTenant<AbstractGormApiTenantThing> {
    Long id
    String name
}
