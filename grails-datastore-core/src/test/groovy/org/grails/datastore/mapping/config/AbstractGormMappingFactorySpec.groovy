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
package org.grails.datastore.mapping.config

import java.beans.PropertyDescriptor

import grails.gorm.multitenancy.MultiTenant
import spock.lang.Specification

import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.document.config.Attribute
import org.grails.datastore.mapping.document.config.Collection
import org.grails.datastore.mapping.document.config.DocumentMappingContext
import org.grails.datastore.mapping.keyvalue.mapping.config.Family
import org.grails.datastore.mapping.keyvalue.mapping.config.GormKeyValueMappingFactory
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValue
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.config.JpaMappingConfigurationStrategy

class AbstractGormMappingFactorySpec extends Specification {

    void "the entity mapped form is created once and evaluates default, static and constraint closures"() {
        given:
        ConnectionSourceSettings settings = new ConnectionSourceSettings()
        settings.default.mapping = { version false }
        settings.default.constraints = { pages maxSize: 10 }
        KeyValueMappingContext context = new KeyValueMappingContext('space', settings)

        when:
        PersistentEntity entity = context.addPersistentEntity(GmfBook)
        Family family = entity.mapping.mappedForm

        then:
        !family.version
        family.family == 'books'
        entity.getPropertyByName('title').mapping.mappedForm.key == 'ttl'
        entity.getPropertyByName('title').mapping.mappedForm.minSize == 2
        entity.getPropertyByName('title').mapping.mappedForm.maxSize == null
        entity.getPropertyByName('pages').mapping.mappedForm.maxSize == 10
        context.mappingFactory.createMappedForm(entity).is(family)
    }

    void "versioning and nullability defaults are configurable"() {
        given:
        GormKeyValueMappingFactory factory = new GormKeyValueMappingFactory('space')
        factory.versionByDefault = false
        factory.defaultNullable = false
        KeyValueMappingContext context = new KeyValueMappingContext('space')
        context.mappingFactory = factory
        context.syntaxStrategy = new JpaMappingConfigurationStrategy(factory)

        when:
        PersistentEntity entity = context.addPersistentEntity(GmfPlain)

        then:
        !entity.mapping.mappedForm.version
        !entity.getPropertyByName('name').mapping.mappedForm.nullable
        entity.getPropertyByName('name').mapping.mappedForm.key == 'name'
    }

    void "a context object is passed to mapping and constraint closures"() {
        given:
        GormKeyValueMappingFactory factory = new GormKeyValueMappingFactory('space')
        List seen = []
        factory.contextObject = 'ctx'
        factory.defaultMapping = { Object ctx -> seen << ctx }
        factory.defaultConstraints = { Object ctx -> seen << ctx }
        KeyValueMappingContext context = new KeyValueMappingContext('space')
        context.mappingFactory = factory

        when:
        context.addPersistentEntity(GmfPlain)

        then:
        seen == ['ctx', 'ctx']
    }

    void "mapping definitions configure document mapped forms but not key value families"() {
        given:
        DocumentMappingContext documentContext = new DocumentMappingContext('db', new ConnectionSourceSettings())
        KeyValueMappingContext keyValueContext = new KeyValueMappingContext('space')

        when:
        PersistentEntity document = documentContext.addPersistentEntity(GmfDefined)
        PersistentEntity keyValue = keyValueContext.addPersistentEntity(GmfDefined)

        then:
        ((Collection) document.mapping.mappedForm).collection == 'defined'
        ((Family) keyValue.mapping.mappedForm).family == GmfDefined.name
    }

    void "tenant id properties are recognised from the tenant identity mapping"() {
        given:
        KeyValueMappingContext context = new KeyValueMappingContext('space')
        PersistentEntity mapped = context.addPersistentEntity(GmfTenantMapped)
        PersistentEntity plain = context.addPersistentEntity(GmfTenantPlain)
        PersistentEntity single = context.addPersistentEntity(GmfPlain)

        expect:
        context.mappingFactory.isTenantId(mapped, context, new PropertyDescriptor('org', GmfTenantMapped))
        !context.mappingFactory.isTenantId(mapped, context, new PropertyDescriptor('tenantId', null, null))
        !context.mappingFactory.isTenantId(mapped, context, new PropertyDescriptor('name', GmfTenantMapped))
        context.mappingFactory.isTenantId(plain, context, new PropertyDescriptor('tenantId', GmfTenantPlain))
        !context.mappingFactory.isTenantId(plain, context, new PropertyDescriptor('name', GmfTenantPlain))
        !context.mappingFactory.isTenantId(single, context, new PropertyDescriptor('name', GmfPlain))
    }

    void "custom identity mappings and wildcard property defaults are honoured"() {
        given:
        KeyValueMappingContext context = new KeyValueMappingContext('space')

        when:
        PersistentEntity entity = context.addPersistentEntity(GmfCustomId)
        KeyValue identity = entity.identity.mapping.mappedForm
        KeyValue name = entity.getPropertyByName('name').mapping.mappedForm
        KeyValue other = entity.getPropertyByName('other').mapping.mappedForm

        then:
        entity.mapping.identifier.identifierName == ['id'] as String[]
        identity.key == 'pk'
        name.key == 'name'
        name.maxSize == 5
        name.nullable
        other.maxSize == 5
        !name.is(other)
    }

}

@grails.gorm.annotation.Entity
class GmfBook {

    Long id
    String title
    Integer pages

    static mapping = {
        family 'books'
        title key: 'ttl'
    }

    static constraints = {
        title minSize: 2
    }

}

@grails.gorm.annotation.Entity
class GmfPlain {

    Long id
    String name

}

@grails.gorm.annotation.Entity
class GmfDefined {

    Long id
    String name

    static mapping = new MappingDefinition<Collection, Attribute>() {
        @Override
        Collection configure(Collection existing) {
            existing.collection = 'defined'
            return existing
        }
        @Override
        Collection build() {
            return configure(new Collection())
        }
    }

}

@grails.gorm.annotation.Entity
class GmfTenantMapped implements MultiTenant {

    Long id
    String org
    String name

    static mapping = {
        tenantId name: 'org'
    }

}

@grails.gorm.annotation.Entity
class GmfTenantPlain implements MultiTenant {

    Long id
    String tenantId
    String name

}

@grails.gorm.annotation.Entity
class GmfCustomId {

    Long id
    String name
    String other

    static mapping = {
        id key: 'pk'
        '*' maxSize: 5
    }

}
