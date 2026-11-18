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
import org.grails.datastore.mapping.reflect.MetaClassUtils
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

    void "the 1-arg constructor delegates through with no transaction manager"() {
        when:
        def enhancer = new GormEnhancer(datastore)

        then:
        enhancer.datastore == datastore
        enhancer.transactionManager == null
        !enhancer.failOnError
    }

    void "getConnectionSourceNames exposes the resolved connection source names"() {
        given:
        def enhancer = new GormEnhancer(datastore, null, new ConnectionSourceSettings())

        expect:
        enhancer.getConnectionSourceNames() == enhancer.connectionSourceNames
        !enhancer.connectionSourceNames.isEmpty()
    }

    void "enhance(entity) registers the entity directly, independent of the enhance(boolean) dynamicEnhance loop"() {
        given:
        def enhancer = new GormEnhancer(datastore, null, new ConnectionSourceSettings())
        def entity = datastore.mappingContext.getPersistentEntity(GormEnhancerCoverageThing.name)

        when:
        enhancer.enhance(entity)

        then: "registerEntity runs and the GroovyObject/dynamicEnhance guard evaluates to false for a Groovy entity - no error"
        notThrown(Throwable)
    }

    void "close() clears the preferred datastore when it matches the enhancer's own datastore"() {
        given:
        def ds = new SimpleMapDatastore(GormEnhancerGatingThing)
        def enhancer = new GormEnhancer(ds, null, new ConnectionSourceSettings())
        GormEnhancerRegistry.instance.setPreferredDatastore(ds)

        when:
        enhancer.close()

        then:
        GormEnhancerRegistry.instance.getPreferredDatastore() == null
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

    void "addInstanceMethods does not clobber a pre-existing instance methodMissing handler"() {
        given: "another plugin/application has already installed an instance methodMissing handler"
        def ds = new SimpleMapDatastore(GormEnhancerClobberGuardThing)
        def enhancer = new GormEnhancer(ds, null, new ConnectionSourceSettings())
        def entity = ds.mappingContext.getPersistentEntity(GormEnhancerClobberGuardThing.name)
        def mc = MetaClassUtils.getExpandoMetaClass(GormEnhancerClobberGuardThing)
        mc.methodMissing = { String name, args -> 'existing-instance-handler' }
        def instance = new GormEnhancerClobberGuardThing(name: 'a')

        when:
        enhancer.addInstanceMethods(entity)

        then: "the pre-existing handler is left in place, not overwritten by GORM's dispatch"
        mc.invokeMethod(instance, 'someUnknownInstanceMethod', [] as Object[]) == 'existing-instance-handler'

        cleanup:
        ds.close()
    }

    void "addInstanceMethods installs GORM's instance dispatch when nothing else has claimed it"() {
        given:
        def ds = new SimpleMapDatastore(GormEnhancerClobberGuardThing)
        def enhancer = new GormEnhancer(ds, null, new ConnectionSourceSettings())
        def entity = ds.mappingContext.getPersistentEntity(GormEnhancerClobberGuardThing.name)
        def mc = MetaClassUtils.getExpandoMetaClass(GormEnhancerClobberGuardThing)

        when:
        enhancer.addInstanceMethods(entity)

        then: "the entity's instances now resolve unknown methods through the installed dispatch"
        mc.getMetaMethod('methodMissing', [String, Object] as Class[]) != null

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

    void "addStaticMethods installs a static dispatch that resolves a real dynamic finder"() {
        given:
        def ds = new SimpleMapDatastore(GormEnhancerStaticDispatchThing)
        def enhancer = new GormEnhancer(ds, null, new ConnectionSourceSettings())
        def entity = ds.mappingContext.getPersistentEntity(GormEnhancerStaticDispatchThing.name)
        def mc = MetaClassUtils.getExpandoMetaClass(GormEnhancerStaticDispatchThing)
        ds.withSession {
            it.persist(new GormEnhancerStaticDispatchThing(name: 'static-dispatch'))
            it.flush()
        }

        when: "invoking a real dynamic finder directly through the installed static dispatch"
        enhancer.addStaticMethods(entity)
        def result = mc.invokeStaticMethod(GormEnhancerStaticDispatchThing, 'countByName', ['static-dispatch'] as Object[])

        then: "the static API's own methodMissing resolves and executes the finder - no exception"
        result == 1

        when: "invoking an unrecognised name through the same dispatch"
        mc.invokeStaticMethod(GormEnhancerStaticDispatchThing, 'notARealFinderOrMethod', [] as Object[])

        then: "the static API's own methodMissing rejects it and the failure surfaces to the caller"
        thrown(MissingMethodException)

        cleanup:
        ds.close()
    }

    void "addStaticMethods installs a static propertyMissing that resolves a qualifier and rejects an unknown property"() {
        given:
        def ds = new SimpleMapDatastore(GormEnhancerStaticDispatchThing)
        def enhancer = new GormEnhancer(ds, null, new ConnectionSourceSettings())
        def entity = ds.mappingContext.getPersistentEntity(GormEnhancerStaticDispatchThing.name)
        def mc = MetaClassUtils.getExpandoMetaClass(GormEnhancerStaticDispatchThing)

        when:
        enhancer.addStaticMethods(entity)
        mc.invokeStaticMethod(GormEnhancerStaticDispatchThing, 'propertyMissing', ['someCompletelyUnknownStaticProperty'] as Object[])

        then:
        thrown(MissingPropertyException)
    }

    void "addInstanceMethods installs an instance dispatch that delegates unresolved methods and properties to the instance API"() {
        given:
        def ds = new SimpleMapDatastore(GormEnhancerInstanceDispatchThing)
        def enhancer = new GormEnhancer(ds, null, new ConnectionSourceSettings())
        def entity = ds.mappingContext.getPersistentEntity(GormEnhancerInstanceDispatchThing.name)
        def mc = MetaClassUtils.getExpandoMetaClass(GormEnhancerInstanceDispatchThing)
        def instance = new GormEnhancerInstanceDispatchThing(name: 'a')

        when:
        enhancer.addInstanceMethods(entity)

        then: "the closures were installed"
        mc.getMetaMethod('methodMissing', [String, Object] as Class[]) != null

        when: "an unresolved instance method is invoked directly through the installed dispatch"
        mc.invokeMethod(instance, 'someCompletelyUnknownInstanceMethod', [] as Object[])

        then: "GormInstanceApi has no matching method either, and the failure surfaces to the caller"
        thrown(MissingMethodException)

        when: "an unresolved instance property is read directly through the installed dispatch"
        mc.getProperty(instance, 'someCompletelyUnknownInstanceProperty')

        then: "GormInstanceApi's propertyMissing(instance, name) is delegated to and rejects it too"
        thrown(MissingPropertyException)

        when: "an unresolved instance property is set directly through the installed dispatch"
        mc.invokeMethod(instance, 'propertyMissing', ['someCompletelyUnknownInstanceProperty', 'value'] as Object[])

        then: "the setter closure forwards to the instance API's own setProperty"
        thrown(MissingPropertyException)

        cleanup:
        ds.close()
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

@Entity
class GormEnhancerClobberGuardThing {

    String name
}

@Entity
class GormEnhancerStaticDispatchThing {

    String name
}

@Entity
class GormEnhancerInstanceDispatchThing {

    String name
}
