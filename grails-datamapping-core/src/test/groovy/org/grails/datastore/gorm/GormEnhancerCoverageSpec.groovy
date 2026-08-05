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

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * {@link GormEnhancerAllQualifiersSpec} (pre-existing) covers {@code allQualifiers}'s
 * same-datastore path and {@code registerEntity}/{@code close}'s happy paths thoroughly. This
 * spec targets the remaining gaps: the deprecated static/protected delegators, the
 * {@code allQualifiers} foreign-datastore branch, and the missing-method/property dispatch that
 * routes through the {@code GormEntity} trait hooks when a real call goes through Groovy
 * dispatch on the actual entity class, not when calling the underlying API object directly (as
 * {@code GormStaticApiSpec}/item 2 does). Bootstrap performs no metaclass mutation:
 * {@code addStaticMethods}/{@code addInstanceMethods} are only reachable through
 * {@code enhance(..)}, which is gated on {@code dynamicEnhance} (always {@code false} from the
 * settings constructor). The datastore's own internal {@code GormEnhancer} (constructed
 * automatically by {@code SimpleMapDatastore}) already registers the entity class against the
 * {@code GormRegistry} singleton, so most tests below don't need to construct their own
 * {@code GormEnhancer} at all.
 */
class GormEnhancerCoverageSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore

    void setup() {
        GormRegistry.instance.reset()
        datastore = new SimpleMapDatastore(GormEnhancerCoverageThing)
    }

    void cleanup() {
        GormRegistry.instance.reset()
    }

    void "the deprecated 2-arg and 3-arg constructors delegate to the primary constructor"() {
        when:
        def viaTwoArg = new GormEnhancer(datastore, datastore.transactionManager)

        then:
        viaTwoArg.datastore == datastore
        !viaTwoArg.failOnError

        when:
        def viaThreeArg = new GormEnhancer(datastore, datastore.transactionManager, true)

        then:
        viaThreeArg.failOnError
    }

    void "allQualifiers resolves connection names by which registered datastore they map to, for a datastore other than the enhancer's own"() {
        given: "a MultiTenant entity, since the foreign-datastore scan only runs inside the multi-tenant/ALL-datasource branch"
        def tenantDs = new SimpleMapDatastore(MultiTenantCoverageThing)
        def registry = new GormRegistry()
        def enhancer = new GormEnhancer(tenantDs, null, new ConnectionSourceSettings(), registry)
        def foreignDs = Stub(Datastore)
        registry.registerDatastoreByQualifier('secondary', foreignDs)
        def entity = tenantDs.mappingContext.getPersistentEntity(MultiTenantCoverageThing.name)

        when: "resolving qualifiers for a datastore that is not the enhancer's own"
        def qualifiers = enhancer.allQualifiers(foreignDs, entity)

        then: "it falls back to scanning datastoresByQualifier for an entry matching the foreign datastore"
        qualifiers == ['secondary']

        cleanup:
        tenantDs.close()
    }

    void "allQualifiers returns just DEFAULT for a non-multi-tenant entity with no explicit datasource"() {
        given:
        def enhancer = new GormEnhancer(datastore, null, new ConnectionSourceSettings())
        def entity = datastore.mappingContext.getPersistentEntity(GormEnhancerCoverageThing.name)

        expect:
        enhancer.allQualifiers(datastore, entity) == [ConnectionSource.DEFAULT]
    }

    void "the deprecated static and protected delegator methods resolve via the registry"() {
        given:
        def enhancer = new GormEnhancer(datastore, null, new ConnectionSourceSettings())

        expect:
        GormEnhancer.findStaticApi(GormEnhancerCoverageThing) != null
        GormEnhancer.findStaticApi(GormEnhancerCoverageThing, ConnectionSource.DEFAULT) != null
        GormEnhancer.findInstanceApi(GormEnhancerCoverageThing) != null
        GormEnhancer.findInstanceApi(GormEnhancerCoverageThing, ConnectionSource.DEFAULT) != null
        GormEnhancer.findValidationApi(GormEnhancerCoverageThing) != null
        GormEnhancer.findValidationApi(GormEnhancerCoverageThing, ConnectionSource.DEFAULT) != null
        GormEnhancer.findDatastore(GormEnhancerCoverageThing) == datastore
        GormEnhancer.findDatastore(GormEnhancerCoverageThing, ConnectionSource.DEFAULT) == datastore
        enhancer.getStaticApi(GormEnhancerCoverageThing) != null
        enhancer.getStaticApi(GormEnhancerCoverageThing, ConnectionSource.DEFAULT) != null
        enhancer.getInstanceApi(GormEnhancerCoverageThing) != null
        enhancer.getInstanceApi(GormEnhancerCoverageThing, ConnectionSource.DEFAULT) != null
        enhancer.getValidationApi(GormEnhancerCoverageThing) != null
        enhancer.getValidationApi(GormEnhancerCoverageThing, ConnectionSource.DEFAULT) != null
        !enhancer.createDynamicFinders().isEmpty()
        !enhancer.createDynamicFinders(datastore).isEmpty()
    }

    void "GormEnhancer.getRegistry and the static findEntity helper resolve via the singleton registry"() {
        expect:
        GormEnhancer.registry == GormRegistry.instance
        GormEnhancer.findEntity(GormEnhancerCoverageThing) != null
    }

    void "constructing an enhancer performs no metaclass mutation and enhance() is a no-op while dynamicEnhance is false"() {
        given:
        def ds = new SimpleMapDatastore(GormEnhancerGatingThing)
        def enhancer = new GormEnhancer(ds, null, new ConnectionSourceSettings())

        expect: "bootstrap did not force an ExpandoMetaClass onto the entity class"
        !enhancer.dynamicEnhance
        !(GroovySystem.metaClassRegistry.getMetaClass(GormEnhancerGatingThing) instanceof ExpandoMetaClass)

        when: "enhance() is called with dynamicEnhance false"
        enhancer.enhance()

        then: "still no metaclass mutation"
        !(GroovySystem.metaClassRegistry.getMetaClass(GormEnhancerGatingThing) instanceof ExpandoMetaClass)

        cleanup:
        ds.close()
    }

    void "a real dynamic-finder call on the entity class routes through the trait's static methodMissing hook"() {
        given:
        datastore.withSession {
            def instance = new GormEnhancerCoverageThing(name: 'find-me')
            it.persist(instance)
            it.flush()
        }

        expect: "the trait's staticMethodMissing hook resolves the dynamic finder and executes it"
        GormEnhancerCoverageThing.findByName('find-me') != null
    }

    void "an unresolvable static property access routes through the trait's staticPropertyMissing and reports MissingPropertyException"() {
        when:
        GormEnhancerCoverageThing.someCompletelyUnknownStaticProperty

        then:
        thrown(MissingPropertyException)
    }

    void "an unresolvable instance property get/set routes through the trait's propertyMissing hooks"() {
        given:
        def instance = new GormEnhancerCoverageThing(name: 'a')

        when:
        instance.someCompletelyUnknownInstanceProperty

        then:
        thrown(MissingPropertyException)

        when:
        instance.someCompletelyUnknownInstanceProperty = 'value'

        then:
        thrown(MissingPropertyException)
    }

    void "an unresolvable instance method call routes through the trait's methodMissing hook"() {
        given:
        def instance = new GormEnhancerCoverageThing(name: 'a')

        when:
        instance.someCompletelyUnknownInstanceMethod()

        then:
        thrown(MissingMethodException)
    }
}

@Entity
class GormEnhancerCoverageThing {

    String name
}

@Entity
class MultiTenantCoverageThing implements MultiTenant<MultiTenantCoverageThing> {

    String name
}

@Entity
class GormEnhancerGatingThing {

    String name
}
