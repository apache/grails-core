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
package org.grails.datastore.mapping.keyvalue.mapping.config

import java.beans.PropertyDescriptor

import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.engine.AssociationIndexer
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.PropertyValueIndexer
import org.grails.datastore.mapping.keyvalue.engine.AbstractKeyValueEntityPersister
import org.grails.datastore.mapping.keyvalue.engine.KeyValueEntry
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.MappingConfigurationStrategy
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.query.Query

class KeyValueMappingSupportSpec extends Specification {

    void "the key value mapping context requires a keyspace and exposes its collaborators"() {
        when:
        new KeyValueMappingContext(null)

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Argument [keyspace] cannot be null'

        when:
        new KeyValueMappingContext(null, new ConnectionSourceSettings())

        then:
        e = thrown()
        e.message == 'Argument [keyspace] cannot be null'

        when:
        KeyValueMappingContext context = new KeyValueMappingContext('space', new ConnectionSourceSettings())
        MappingConfigurationStrategy strategy = Mock(MappingConfigurationStrategy)
        KeyValueMappingFactory factory = new KeyValueMappingFactory('other')

        then:
        context.keyspace == 'space'
        context.mappingFactory instanceof GormKeyValueMappingFactory
        context.mappingSyntaxStrategy != null
        KeyValueMappingContext.GROOVY_OBJECT_CLASS == 'groovy.lang.GroovyObject'

        when:
        context.syntaxStrategy = strategy
        context.mappingFactory = factory
        context.canInitializeEntities = false

        then:
        context.mappingSyntaxStrategy.is(strategy)
        context.mappingFactory.is(factory)
        1 * strategy.setCanExpandMappingContext(false)
    }

    void "key value persistent entities resolve dotted property names through embedded properties"() {
        given:
        KeyValueMappingContext context = new KeyValueMappingContext('space')
        context.addPersistentEntities(KvOwner, KvOther)

        when:
        KeyValuePersistentEntity owner = context.getPersistentEntity(KvOwner.name)
        KeyValuePersistentEntity other = context.getPersistentEntity(KvOther.name)

        then:
        owner.getPropertyByName('address.street').name == 'street'
        owner.getPropertyByName('name.first') == null
        owner.getPropertyByName('missing') == null
        owner.getPropertyByName(null) == null
        owner.getPropertyByName('name').name == 'name'
        owner.parentEntity == null
        other.parentEntity.is(owner)
        other.mapping.mappedForm instanceof Family
        other.mapping.entity.is(other)
        ((Family) other.mapping.mappedForm).family == KvOther.name
    }

    void "the plain key value mapping factory creates families and key values from names"() {
        given:
        KeyValueMappingFactory factory = new KeyValueMappingFactory('space')
        PersistentEntity entity = Stub(PersistentEntity) { getName() >> 'com.example.Thing' }
        PersistentProperty property = Stub(PersistentProperty) { getName() >> 'title' }

        expect:
        factory.createMappedForm(entity).keyspace == 'space'
        factory.createMappedForm(entity).family == 'com.example.Thing'
        factory.createMappedForm(property).key == 'title'
        !factory.isTenantId(entity, Stub(MappingContext), new PropertyDescriptor('title', KvOwner, 'getName', null))
    }

    void "key value entries remember their family"() {
        when:
        KeyValueEntry entry = new KeyValueEntry('people')
        entry.name = 'x'

        then:
        entry.family == 'people'
        entry == [name: 'x']
    }

    void "key value persisters derive family, keyspace and native property keys from the mapping"() {
        given:
        KeyValueMappingContext context = new KeyValueMappingContext('space')
        PersistentEntity mapped = context.addPersistentEntity(KvMapped)
        PersistentEntity plain = context.addPersistentEntity(KvOwner)
        Session session = Stub(Session) { getMappingContext() >> context }
        ClassMapping unmapped = Stub(ClassMapping) { getMappedForm() >> null }

        when:
        KvPersister mappedPersister = new KvPersister(context, mapped, session, null)
        KvPersister plainPersister = new KvPersister(context, plain, session, null)

        then:
        mappedPersister.entityFamily == 'mapped_family'
        mappedPersister.classMapping.is(mapped.mapping)
        mappedPersister.keyspaceFor(mapped.mapping, 'fallback') == 'mapped_space'
        mappedPersister.nativeKeyFor(mapped.getPropertyByName('name')) == 'nm'
        mappedPersister.nativeKeyFor(mapped.getPropertyByName('other')) == 'other'
        plainPersister.entityFamily == KvOwner.name
        plainPersister.keyspaceFor(plain.mapping, 'fallback') == 'space'
        plainPersister.familyFor(plain, unmapped) == KvOwner.name
        plainPersister.keyspaceFor(unmapped, 'fallback') == 'fallback'
    }

    static class KvPersister extends AbstractKeyValueEntityPersister<Map, Object> {

        KvPersister(MappingContext context, PersistentEntity entity, Session session, ApplicationEventPublisher publisher) {
            super(context, entity, session, publisher)
        }

        String nativeKeyFor(PersistentProperty property) { getNativePropertyKey(property) }

        String familyFor(PersistentEntity entity, ClassMapping mapping) { getFamily(entity, mapping) }

        String keyspaceFor(ClassMapping mapping, String defaultValue) { getKeyspace(mapping, defaultValue) }

        @Override
        protected void deleteEntry(String family, Object key, Object entry) { }

        @Override
        protected Object generateIdentifier(PersistentEntity persistentEntity, Map entry) { null }

        @Override
        PropertyValueIndexer getPropertyIndexer(PersistentProperty property) { null }

        @Override
        AssociationIndexer getAssociationIndexer(Map nativeEntry, Association association) { null }

        @Override
        protected Map createNewEntry(String family) { [:] }

        @Override
        protected Object getEntryValue(Map nativeEntry, String property) { nativeEntry[property] }

        @Override
        protected void setEntryValue(Map nativeEntry, String key, Object value) { nativeEntry[key] = value }

        @Override
        protected Map retrieveEntry(PersistentEntity persistentEntity, String family, Serializable key) { null }

        @Override
        protected Object storeEntry(PersistentEntity persistentEntity, EntityAccess entityAccess, Object storeId, Map nativeEntry) { storeId }

        @Override
        protected void updateEntry(PersistentEntity persistentEntity, EntityAccess entityAccess, Object key, Map entry) { }

        @Override
        protected void deleteEntries(String family, List<Object> keys) { }

        @Override
        Query createQuery() { null }

    }

}

@grails.gorm.annotation.Entity
class KvOwner {

    Long id
    String name
    KvAddress address

    static embedded = ['address']

}

class KvAddress {

    String street

}

@grails.gorm.annotation.Entity
class KvOther extends KvOwner {

    String extra

}

@grails.gorm.annotation.Entity
class KvMapped {

    Long id
    String name
    String other

    static mapping = {
        keyspace 'mapped_space'
        family 'mapped_family'
        name key: 'nm'
    }

}
