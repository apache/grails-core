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

package org.grails.datastore.gorm.events

import spock.lang.Specification

import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.context.ApplicationContext

import grails.gorm.annotation.CreatedBy
import grails.gorm.annotation.CreatedDate
import grails.gorm.annotation.LastModifiedBy
import grails.gorm.annotation.LastModifiedDate
import org.grails.datastore.gorm.timestamp.AuditorAware
import org.grails.datastore.gorm.timestamp.TimestampProvider
import org.grails.datastore.mapping.config.Entity
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.event.PostInsertEvent
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.PropertyMapping
import org.grails.datastore.mapping.config.Property

/**
 * Covers the surface of {@link AutoTimestampEventListener} that
 * {@code org.grails.datastore.gorm.timestamp.AutoTimestampEventListenerSpec} does not exercise:
 * construction via a real {@link Datastore}/{@link MappingContext} (which drives the real
 * {@code storeDateCreatedAndLastUpdatedInfo} scanning logic, including annotation detection),
 * event dispatch via the public {@code onApplicationEvent}/{@code supportsEventType} surface, and
 * {@code setApplicationContext}. The protected {@code AutoTimestampEventListener(MappingContext)}
 * constructor is exercised only through {@code org.grails.gorm.rx.events.AutoTimestampEventListener},
 * its sole subclass, covered by that module's own spec.
 */
class AutoTimestampEventListenerConstructionSpec extends Specification {

    void "construction scans already-initialized entities and registers a dateCreated/lastUpdated-by-name property"() {
        given:
        PersistentEntity entity = entityFor(WithConventionalNames, true)

        when:
        AutoTimestampEventListener listener = new AutoTimestampEventListener(datastoreFor([entity]))

        then:
        listener.getDateCreatedPropertyNames(WithConventionalNames.name) == ['dateCreated'] as Set
        listener.getLastUpdatedPropertyNames(WithConventionalNames.name) == ['lastUpdated'] as Set
    }

    void "construction registers @CreatedDate/@LastModifiedDate annotated properties regardless of their name"() {
        given:
        PersistentEntity entity = entityFor(WithDateAnnotations, true)

        when:
        AutoTimestampEventListener listener = new AutoTimestampEventListener(datastoreFor([entity]))

        then:
        listener.getDateCreatedPropertyNames(WithDateAnnotations.name) == ['whenCreated'] as Set
        listener.getLastUpdatedPropertyNames(WithDateAnnotations.name) == ['whenModified'] as Set
    }

    void "construction registers @CreatedBy/@LastModifiedBy annotated properties as auditor fields"() {
        given:
        PersistentEntity entity = entityFor(WithAuditorAnnotations, true)

        when:
        AutoTimestampEventListener listener = new AutoTimestampEventListener(datastoreFor([entity]))

        then:
        listener.getCreatedByPropertyNames(WithAuditorAnnotations.name) == ['createdBy'] as Set
        listener.getUpdatedByPropertyNames(WithAuditorAnnotations.name) == ['lastModifiedBy'] as Set
    }

    void "construction ignores plain properties that carry no auto-timestamp or auditor annotation"() {
        given:
        PersistentEntity entity = entityFor(PlainDomain, true)

        when:
        AutoTimestampEventListener listener = new AutoTimestampEventListener(datastoreFor([entity]))

        then:
        listener.getDateCreatedPropertyNames(PlainDomain.name) == null
        listener.getLastUpdatedPropertyNames(PlainDomain.name) == null
        listener.getCreatedByPropertyNames(PlainDomain.name) == null
        listener.getUpdatedByPropertyNames(PlainDomain.name) == null
    }

    void "construction skips scanning an entity whose mapping explicitly disables autoTimestamp"() {
        given:
        PersistentEntity entity = entityFor(WithConventionalNames, true, false)

        when:
        AutoTimestampEventListener listener = new AutoTimestampEventListener(datastoreFor([entity]))

        then:
        listener.getDateCreatedPropertyNames(WithConventionalNames.name) == null
        listener.getLastUpdatedPropertyNames(WithConventionalNames.name) == null
    }

    void "construction scans an entity that has no mapped form at all as if autoTimestamp were enabled"() {
        given:
        PersistentEntity entity = Stub(PersistentEntity) {
            getName() >> WithConventionalNames.name
            isInitialized() >> true
            getMapping() >> Stub(ClassMapping) { getMappedForm() >> null }
            getPersistentProperties() >> propertiesFor(WithConventionalNames)
        }

        when:
        AutoTimestampEventListener listener = new AutoTimestampEventListener(datastoreFor([entity]))

        then:
        listener.getDateCreatedPropertyNames(WithConventionalNames.name) == ['dateCreated'] as Set
    }

    void "storeTimestampAvailability does not register a dateCreated/lastUpdated property whose type the TimestampProvider does not support"() {
        given:
        PersistentEntity entity = entityFor(WithConventionalNames, true)
        AutoTimestampEventListener listener = new AutoTimestampEventListener(datastoreFor([]))
        listener.setTimestampProvider(Stub(TimestampProvider) {
            supportsCreating(_) >> false
        })

        when:
        listener.persistentEntityAdded(entity)

        then:
        listener.getDateCreatedPropertyNames(WithConventionalNames.name) == null
        listener.getLastUpdatedPropertyNames(WithConventionalNames.name) == null
    }

    void "an uninitialized entity is deferred and only scanned once beforeInsert is actually invoked for it"() {
        given: 'an entity that starts out not initialized, but becomes initialized by the time its hook fires'
        boolean initialized = false
        PersistentEntity entity = Stub(PersistentEntity) {
            getName() >> WithConventionalNames.name
            isInitialized() >> { initialized }
            getMapping() >> Stub(ClassMapping) { getMappedForm() >> new Entity() }
            getPersistentProperties() >> propertiesFor(WithConventionalNames)
        }
        AutoTimestampEventListener listener = new AutoTimestampEventListener(datastoreFor([entity]))

        expect: 'nothing was scanned yet, since the entity reported itself as not initialized'
        listener.getDateCreatedPropertyNames(WithConventionalNames.name) == null

        when:
        initialized = true
        WithConventionalNames domain = new WithConventionalNames()
        EntityAccess ea = Stub(EntityAccess) {
            getEntity() >> domain
            getPropertyValue(_) >> null
            getPropertyType(_) >> Date
        }
        listener.beforeInsert(entity, ea)

        then: 'the deferred scan ran, so the property is now known and was applied to the entity access'
        listener.getDateCreatedPropertyNames(WithConventionalNames.name) == ['dateCreated'] as Set
    }

    void "setApplicationContext wires the AuditorAware bean when one is present"() {
        given:
        AuditorAware auditorAware = Stub(AuditorAware)
        ApplicationContext applicationContext = Stub(ApplicationContext) {
            getBean(AuditorAware) >> auditorAware
        }
        AutoTimestampEventListener listener = new AutoTimestampEventListener(datastoreFor([]))

        when:
        listener.setApplicationContext(applicationContext)

        then:
        listener.auditorAware.is(auditorAware)
    }

    void "setApplicationContext silently leaves auditorAware unset when no AuditorAware bean is registered"() {
        given:
        ApplicationContext applicationContext = Stub(ApplicationContext) {
            getBean(AuditorAware) >> { throw new NoSuchBeanDefinitionException(AuditorAware) }
        }
        AutoTimestampEventListener listener = new AutoTimestampEventListener(datastoreFor([]))

        when:
        listener.setApplicationContext(applicationContext)

        then:
        noExceptionThrown()
        listener.auditorAware == null
    }

    void "supportsEventType accepts only PreInsertEvent and PreUpdateEvent"() {
        given:
        AutoTimestampEventListener listener = new AutoTimestampEventListener(datastoreFor([]))

        expect:
        listener.supportsEventType(PreInsertEvent)
        listener.supportsEventType(PreUpdateEvent)
        !listener.supportsEventType(PostInsertEvent)
    }

    void "onApplicationEvent dispatches a PreInsertEvent to beforeInsert"() {
        given:
        PersistentEntity entity = entityFor(WithConventionalNames, true)
        Datastore datastore = datastoreFor([entity])
        AutoTimestampEventListener listener = new AutoTimestampEventListener(datastore)
        WithConventionalNames domain = new WithConventionalNames()
        EntityAccess ea = Mock(EntityAccess) {
            getEntity() >> domain
            getPropertyType(_) >> Date
        }

        when:
        listener.onApplicationEvent(new PreInsertEvent(datastore, entity, ea))

        then:
        2 * ea.setProperty(_, _)
    }

    void "onApplicationEvent dispatches a PreUpdateEvent to beforeUpdate"() {
        given:
        PersistentEntity entity = entityFor(WithConventionalNames, true)
        Datastore datastore = datastoreFor([entity])
        AutoTimestampEventListener listener = new AutoTimestampEventListener(datastore)
        WithConventionalNames domain = new WithConventionalNames()
        EntityAccess ea = Mock(EntityAccess) {
            getEntity() >> domain
            getPropertyType(_) >> Date
        }

        when:
        listener.onApplicationEvent(new PreUpdateEvent(datastore, entity, ea))

        then:
        1 * ea.setProperty('lastUpdated', _)
    }

    void "onApplicationEvent ignores an event whose entity is null"() {
        given:
        Datastore datastore = datastoreFor([])
        AutoTimestampEventListener listener = new AutoTimestampEventListener(datastore)
        EntityAccess ea = Mock(EntityAccess)

        when:
        listener.onApplicationEvent(new PreInsertEvent(datastore, null, ea))

        then:
        noExceptionThrown()
        0 * ea.setProperty(*_)
    }

    void "onApplicationEvent ignores event types other than PreInsert and PreUpdate"() {
        given:
        PersistentEntity entity = entityFor(WithConventionalNames, true)
        Datastore datastore = datastoreFor([entity])
        AutoTimestampEventListener listener = new AutoTimestampEventListener(datastore)
        EntityAccess ea = Mock(EntityAccess) { getEntity() >> new WithConventionalNames() }

        when:
        listener.onApplicationEvent(new PostInsertEvent(datastore, entity, ea))

        then:
        noExceptionThrown()
        0 * ea.setProperty(*_)
    }

    private Datastore datastoreFor(List<PersistentEntity> entities) {
        Stub(Datastore) {
            getMappingContext() >> Stub(MappingContext) {
                getPersistentEntities() >> entities
            }
        }
    }

    private PersistentEntity entityFor(Class<?> javaClass, boolean initialized, Boolean autoTimestamp = null) {
        Entity mappedForm = autoTimestamp == null ? new Entity() : new Entity(autoTimestamp: autoTimestamp)
        Stub(PersistentEntity) {
            getName() >> javaClass.name
            isInitialized() >> initialized
            getMapping() >> Stub(ClassMapping) { getMappedForm() >> mappedForm }
            getPersistentProperties() >> propertiesFor(javaClass)
        }
    }

    private List<PersistentProperty> propertiesFor(Class<?> javaClass) {
        javaClass.declaredFields.findAll { !it.synthetic }.collect { field ->
            Property mappedForm = new Property()
            PersistentEntity owner = Stub(PersistentEntity) {
                getName() >> javaClass.name
                getJavaClass() >> javaClass
            }
            Stub(PersistentProperty) {
                getName() >> field.name
                getType() >> field.type
                getOwner() >> owner
                getMapping() >> Stub(PropertyMapping) { getMappedForm() >> mappedForm }
            }
        }
    }
}

class WithConventionalNames {

    Date dateCreated
    Date lastUpdated
}

class WithDateAnnotations {

    @CreatedDate
    Date whenCreated

    @LastModifiedDate
    Date whenModified
}

class WithAuditorAnnotations {

    @CreatedBy
    String createdBy

    @LastModifiedBy
    String lastModifiedBy
}

class PlainDomain {

    String name
}
