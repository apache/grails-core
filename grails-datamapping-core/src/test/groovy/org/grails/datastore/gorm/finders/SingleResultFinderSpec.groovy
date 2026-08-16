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
package org.grails.datastore.gorm.finders

import groovy.lang.MissingMethodException
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.query.Query
import org.springframework.core.convert.support.DefaultConversionService
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Exercises {@link SingleResultFinder} - the composed replacement for the deleted
 * AbstractFindByFinder/FindByFinder/FindByBooleanFinder/FindOrCreateByFinder/FindOrSaveByFinder
 * class hierarchy. Every factory method builds its own {@link DynamicFinder} grammar instance
 * (never shared across finders, since a shared instance would be a real thread-safety hazard
 * given finder instances are long-lived and invoked from arbitrary request threads), so each
 * test constructs the specific factory under test directly rather than reusing a single shared
 * field.
 */
class SingleResultFinderSpec extends Specification {

    PersistentProperty nameProperty = Stub(PersistentProperty) {
        getName() >> 'name'
        getType() >> String
    }
    PersistentProperty ageProperty = Stub(PersistentProperty) {
        getName() >> 'age'
        getType() >> Integer
    }
    MappingContext mappingContext = Stub(MappingContext)
    PersistentEntity persistentEntity = Stub(PersistentEntity)
    Datastore datastore = Stub(Datastore) {
        getMappingContext() >> mappingContext
    }

    // Every property lookup a test's method name touches must be stubbed explicitly: Spock's Stub
    // "smart null" returns a DUMMY PersistentProperty (not null) for any unstubbed
    // getPropertyByName(...) call, since PersistentProperty is an interface - that dummy's own
    // unstubbed getType() then needs a dummy java.lang.Class, which Spock cannot create (final
    // class), crashing every test that reaches convertArguments for an unstubbed property.
    void setup() {
        mappingContext.getConversionService() >> new DefaultConversionService()
        mappingContext.getPersistentEntity(FinderTestEntity.name) >> persistentEntity
        persistentEntity.getMappingContext() >> mappingContext
        persistentEntity.getPropertyByName('name') >> nameProperty
        persistentEntity.getPropertyByName('age') >> ageProperty
    }

    @Unroll
    void "findBy isMethodMatch('#methodName') == #matches"() {
        expect:
        SingleResultFinder.findBy(datastore).isMethodMatch(methodName) == matches

        where:
        methodName         | matches
        'findByName'       | true
        'findByNameAndAge' | true
        'somethingElse'    | false
    }

    @Unroll
    void "findByBoolean isMethodMatch('#methodName') == #matches"() {
        expect:
        SingleResultFinder.findByBoolean(datastore).isMethodMatch(methodName) == matches

        where:
        methodName         | matches
        'findActiveByName' | true
        'findActive'       | true
        'somethingElse'    | false
    }

    void "setPattern delegates to the grammar"() {
        given:
        SingleResultFinder finder = SingleResultFinder.findBy(datastore)

        expect:
        finder.isMethodMatch('findByName')
        !finder.isMethodMatch('customPrefixName')

        when:
        finder.setPattern('(customPrefix)([A-Z]\\w*)')

        then:
        finder.isMethodMatch('customPrefixName')
        !finder.isMethodMatch('findByName')
    }

    void "findBy runs the full round trip through the real DatastoreUtils.execute seam and returns singleResult()"() {
        given:
        FinderTestEntity expected = new FinderTestEntity(name: 'Bob')
        Query query = Mock(Query) {
            getEntity() >> persistentEntity
            singleResult() >> expected
        }
        Session session = Stub(Session) {
            createQuery(FinderTestEntity) >> query
        }
        // DatastoreUtils.execute() takes the "existing bound session" branch when
        // Datastore.hasCurrentSession() answers true, invoking the callback directly with that
        // session - the simplest reliable seam for exercising the real round trip without wiring
        // Spring's transaction synchronization machinery.
        datastore.hasCurrentSession() >> true
        datastore.getCurrentSession() >> session

        when:
        Object result = SingleResultFinder.findBy(datastore).invoke(FinderTestEntity, 'findByName', ['Bob'] as Object[])

        then:
        result.is(expected)
        1 * query.add({ it instanceof Query.Conjunction })
    }

    void "invoke(Class, methodName, DetachedCriteria, Object[]) merges the detached criteria onto the built query"() {
        given:
        // Called reflectively (dynamic Groovy dispatch, not part of FinderMethod) by
        // AbstractDetachedCriteria#methodMissing - this is the seam a real
        // `SomeDetachedCriteria.findByName(...)` call goes through.
        FinderTestEntity expected = new FinderTestEntity(name: 'Bob')
        Query query = Mock(Query) {
            getEntity() >> persistentEntity
            singleResult() >> expected
        }
        Session session = Stub(Session) {
            createQuery(FinderTestEntity) >> query
        }
        datastore.hasCurrentSession() >> true
        datastore.getCurrentSession() >> session
        grails.gorm.DetachedCriteria detachedCriteria = Stub(grails.gorm.DetachedCriteria) {
            getFetchStrategies() >> [:]
            getCriteria() >> [org.grails.datastore.mapping.query.Restrictions.eq('age', 42)]
            getProjections() >> []
            getOrders() >> []
        }

        when:
        Object result = SingleResultFinder.findBy(datastore).invoke(FinderTestEntity, 'findByName', detachedCriteria, ['Bob'] as Object[])

        then:
        result.is(expected)
        1 * query.add({ it instanceof Query.PropertyCriterion })
        1 * query.add({ it instanceof Query.Conjunction })
    }

    void "invoke(Class, methodName, DetachedCriteria, Object[]) with a null detachedCriteria never merges anything onto the built query"() {
        given:
        // Same reflective entry point as above, but for the case AbstractDetachedCriteria's own
        // detached criteria has nothing to merge - proving the null-check actually guards the merge
        // rather than it happening to be a no-op some other way.
        FinderTestEntity expected = new FinderTestEntity(name: 'Bob')
        Query query = Mock(Query) {
            getEntity() >> persistentEntity
            singleResult() >> expected
        }
        Session session = Stub(Session) {
            createQuery(FinderTestEntity) >> query
        }
        datastore.hasCurrentSession() >> true
        datastore.getCurrentSession() >> session

        when:
        Object result = SingleResultFinder.findBy(datastore).invoke(FinderTestEntity, 'findByName', (grails.gorm.DetachedCriteria) null, ['Bob'] as Object[])

        then:
        result.is(expected)
        0 * query.add({ it instanceof Query.PropertyCriterion })
        1 * query.add({ it instanceof Query.Conjunction })
    }

    void "invoke(Class, methodName) throws IllegalStateException when constructed in stateless mode"() {
        when:
        SingleResultFinder.findBy(mappingContext).invoke(FinderTestEntity, 'findByName', ['Bob'] as Object[])

        then:
        thrown(IllegalStateException)
    }

    void "findByBoolean invoke throws IllegalStateException when constructed in stateless mode"() {
        when:
        SingleResultFinder.findByBoolean(mappingContext).invoke(FinderTestEntity, 'findActiveByName', ['Bob'] as Object[])

        then:
        thrown(IllegalStateException)
    }

    void "findOrCreateBy rejects the Or operator without ever touching the datastore"() {
        given:
        Datastore datastoreThatMustNotBeUsed = Mock(Datastore) {
            getMappingContext() >> mappingContext
        }

        when:
        SingleResultFinder.findOrCreateBy(datastoreThatMustNotBeUsed).invoke(
                FinderTestEntity, 'findOrCreateByNameOrAge', ['Bob', 42] as Object[])

        then:
        thrown(MissingMethodException)
        0 * datastoreThatMustNotBeUsed.hasCurrentSession()
        0 * datastoreThatMustNotBeUsed.connect()
    }

    @Unroll
    void "findOrCreateBy rejects a #expressionType.simpleName expression as not equality-based"() {
        given:
        Datastore datastoreThatMustNotBeUsed = Mock(Datastore) {
            getMappingContext() >> mappingContext
        }

        when:
        SingleResultFinder.findOrCreateBy(datastoreThatMustNotBeUsed).invoke(
                FinderTestEntity, "findOrCreateByAge${expressionType.simpleName}", [18] as Object[])

        then:
        thrown(ConfigurationException)

        where:
        expressionType << [MethodExpression.GreaterThan, MethodExpression.LessThan,
                            MethodExpression.GreaterThanEquals, MethodExpression.LessThanEquals]
    }

    void "findOrCreateBy constructs a new instance via the Map constructor when the query returns null, without saving it"() {
        given:
        Query query = Mock(Query) {
            getEntity() >> persistentEntity
            singleResult() >> null
        }
        Session session = Stub(Session) {
            createQuery(FinderTestEntity) >> query
        }
        datastore.hasCurrentSession() >> true
        datastore.getCurrentSession() >> session

        when:
        Object result = SingleResultFinder.findOrCreateBy(datastore).invoke(FinderTestEntity, 'findOrCreateByName', ['Bob'] as Object[])

        then:
        result instanceof FinderTestEntity
        result.name == 'Bob'
        !result.saved
    }

    void "findOrCreateBy wraps a ConversionException from singleResult() into a MissingMethodException"() {
        given:
        // Guards the onNullResult-configured path (findOrCreateBy/findOrSaveBy only): if the query
        // itself fails to convert an argument rather than simply returning null, that failure must
        // still surface as a MissingMethodException, not propagate as a raw ConversionException.
        Query query = Mock(Query) {
            getEntity() >> persistentEntity
            singleResult() >> { throw new org.springframework.core.convert.ConversionFailedException(
                    org.springframework.core.convert.TypeDescriptor.valueOf(String), org.springframework.core.convert.TypeDescriptor.valueOf(Integer), 'Bob', new IllegalArgumentException()) }
        }
        Session session = Stub(Session) {
            createQuery(FinderTestEntity) >> query
        }
        datastore.hasCurrentSession() >> true
        datastore.getCurrentSession() >> session

        when:
        SingleResultFinder.findOrCreateBy(datastore).invoke(FinderTestEntity, 'findOrCreateByName', ['Bob'] as Object[])

        then:
        thrown(MissingMethodException)
    }

    void "findOrCreateBy invoke throws IllegalStateException when constructed in stateless mode"() {
        when:
        SingleResultFinder.findOrCreateBy(mappingContext).invoke(FinderTestEntity, 'findOrCreateByName', ['Bob'] as Object[])

        then:
        thrown(IllegalStateException)
    }

    void "findOrCreateBy throws MissingMethodException on the null-result path when a non-Equal expression reached it"() {
        given:
        // validate only rejects GT/LT/GTE/LTE - Like/InList/etc slip past it, so this defensive
        // check in the null-result construction path is a genuinely separate branch.
        Query query = Mock(Query) {
            getEntity() >> persistentEntity
            singleResult() >> null
        }
        Session session = Stub(Session) {
            createQuery(FinderTestEntity) >> query
        }
        datastore.hasCurrentSession() >> true
        datastore.getCurrentSession() >> session

        when:
        SingleResultFinder.findOrCreateBy(datastore).invoke(FinderTestEntity, 'findOrCreateByNameLike', ['Bo%'] as Object[])

        then:
        thrown(MissingMethodException)
    }

    void "findOrSaveBy rejects the Or operator, mirroring findOrCreateBy"() {
        when:
        SingleResultFinder.findOrSaveBy(datastore).invoke(FinderTestEntity, 'findOrSaveByNameOrAge', ['Bob', 42] as Object[])

        then:
        thrown(MissingMethodException)
    }

    void "findOrSaveBy constructs a new instance and saves it when the query returns null"() {
        given:
        Query query = Mock(Query) {
            getEntity() >> persistentEntity
            singleResult() >> null
        }
        Session session = Stub(Session) {
            createQuery(FinderTestEntity) >> query
        }
        datastore.hasCurrentSession() >> true
        datastore.getCurrentSession() >> session

        when:
        Object result = SingleResultFinder.findOrSaveBy(datastore).invoke(FinderTestEntity, 'findOrSaveByName', ['Bob'] as Object[])

        then:
        result instanceof FinderTestEntity
        result.name == 'Bob'
        result.saved
    }

    void "findOrSaveBy invoke throws IllegalStateException when constructed in stateless mode"() {
        when:
        SingleResultFinder.findOrSaveBy(mappingContext).invoke(FinderTestEntity, 'findOrSaveByName', ['Bob'] as Object[])

        then:
        thrown(IllegalStateException)
    }

    void "findOrSaveBy throws MissingMethodException on the null-result path when a non-Equal expression reached it"() {
        given:
        Query query = Mock(Query) {
            getEntity() >> persistentEntity
            singleResult() >> null
        }
        Session session = Stub(Session) {
            createQuery(FinderTestEntity) >> query
        }
        datastore.hasCurrentSession() >> true
        datastore.getCurrentSession() >> session

        when:
        SingleResultFinder.findOrSaveBy(datastore).invoke(FinderTestEntity, 'findOrSaveByNameLike', ['Bo%'] as Object[])

        then:
        thrown(MissingMethodException)
    }
}
