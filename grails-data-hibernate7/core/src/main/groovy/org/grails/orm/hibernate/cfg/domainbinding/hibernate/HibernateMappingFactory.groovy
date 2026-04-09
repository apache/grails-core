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
package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import java.beans.PropertyDescriptor

import groovy.transform.CompileStatic

import org.grails.datastore.mapping.config.AbstractGormMappingFactory
import org.grails.datastore.mapping.config.groovy.MappingConfigurationBuilder
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.DatastoreConfigurationException
import org.grails.datastore.mapping.model.IdentityMapping
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.ValueGenerator
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.Custom
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.model.types.EmbeddedCollection
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.OneToMany
import org.grails.datastore.mapping.model.types.Simple
import org.grails.datastore.mapping.model.types.TenantId
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PropertyConfig

/**
 * The {@link AbstractGormMappingFactory} implementation for Hibernate, responsible for
 * creating all Hibernate-specific persistent property and identity mapping instances.
 */
@CompileStatic
class HibernateMappingFactory extends AbstractGormMappingFactory<Mapping, PropertyConfig> {

    @Override
    protected MappingConfigurationBuilder createConfigurationBuilder(PersistentEntity entity, Mapping mapping) {
        new HibernateMappingBuilder(mapping, entity.name, defaultConstraints)
    }

    @Override
    org.grails.datastore.mapping.model.types.Identity<PropertyConfig> createIdentity(
            PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
        HibernateSimpleIdentityProperty identity = new HibernateSimpleIdentityProperty(owner, context, pd)
        identity.setMapping(createPropertyMapping(identity, owner))
        identity
    }

    HibernateSimpleIdentityProperty createSimpleIdentityProperty(
            PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
        HibernateSimpleIdentityProperty identity = new HibernateSimpleIdentityProperty(owner, context, pd)
        identity.setMapping(createPropertyMapping(identity, owner))
        identity
    }

    HibernateCompositeIdentityProperty createCompositeIdentityProperty(
            PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
        HibernateCompositeIdentityProperty identity = new HibernateCompositeIdentityProperty(owner, context, pd)
        identity.setMapping(createPropertyMapping(identity, owner))
        identity
    }

    @Override
    TenantId<PropertyConfig> createTenantId(
            PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
        HibernateTenantIdProperty tenantId = new HibernateTenantIdProperty(owner, context, pd)
        tenantId.setMapping(createDerivedPropertyMapping(tenantId, owner))
        tenantId
    }

    @Override
    Custom<PropertyConfig> createCustom(
            PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
        Class<?> propertyType = pd.propertyType
        CustomTypeMarshaller customTypeMarshaller = findCustomType(context, propertyType)
        if (customTypeMarshaller == null && propertyType.isEnum()) {
            customTypeMarshaller = findCustomType(context, Enum)
        }
        HibernateCustomProperty custom = propertyType.isEnum()
                ? new HibernateCustomEnumProperty(owner, context, pd, customTypeMarshaller)
                : new HibernateCustomProperty(owner, context, pd, customTypeMarshaller)
        custom.setMapping(createPropertyMapping(custom, owner))
        custom
    }

    @Override
    Simple<PropertyConfig> createSimple(
            PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
        HibernateSimpleProperty simple = pd.propertyType.isEnum()
                ? new HibernateSimpleEnumProperty(owner, context, pd)
                : new HibernateSimpleProperty(owner, context, pd)
        simple.setMapping(createPropertyMapping(simple, owner))
        simple
    }

    @Override
    ToOne createOneToOne(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        HibernateOneToOneProperty oneToOne = new HibernateOneToOneProperty(entity, context, property)
        oneToOne.setMapping(createPropertyMapping(oneToOne, entity))
        oneToOne
    }

    @Override
    ToOne createManyToOne(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        HibernateManyToOneProperty manyToOne = new HibernateManyToOneProperty(entity, context, property)
        manyToOne.setMapping(createPropertyMapping(manyToOne, entity))
        manyToOne
    }

    @Override
    OneToMany createOneToMany(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        HibernateOneToManyProperty oneToMany = new HibernateOneToManyProperty(entity, context, property)
        oneToMany.setMapping(createPropertyMapping(oneToMany, entity))
        oneToMany
    }

    @Override
    ManyToMany createManyToMany(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        HibernateManyToManyProperty manyToMany = new HibernateManyToManyProperty(entity, context, property)
        manyToMany.setMapping(createPropertyMapping(manyToMany, entity))
        manyToMany
    }

    @Override
    Embedded createEmbedded(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        HibernateEmbeddedProperty embedded = new HibernateEmbeddedProperty(entity, context, property)
        embedded.setMapping(createPropertyMapping(embedded, entity))
        embedded
    }

    @Override
    EmbeddedCollection createEmbeddedCollection(
            PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        HibernateEmbeddedCollectionProperty embedded =
                new HibernateEmbeddedCollectionProperty(entity, context, property)
        embedded.setMapping(createPropertyMapping(embedded, entity))
        embedded
    }

    @Override
    Basic createBasicCollection(
            PersistentEntity entity, MappingContext context, PropertyDescriptor property, Class collectionType) {
        if (entity instanceof GrailsHibernatePersistentEntity) {
            GrailsHibernatePersistentEntity ghpEntity = (GrailsHibernatePersistentEntity) entity
            HibernateBasicProperty basic = new HibernateBasicProperty(ghpEntity, context, property)
            basic.setMapping(createPropertyMapping(basic, entity))
            CustomTypeMarshaller customTypeMarshaller = findCustomType(context, property.propertyType)
            if (collectionType != null && collectionType.isEnum()) {
                customTypeMarshaller = findCustomType(context, collectionType)
                if (customTypeMarshaller == null) {
                    customTypeMarshaller = findCustomType(context, Enum)
                }
            }
            if (customTypeMarshaller != null) {
                basic.setCustomTypeMarshaller(customTypeMarshaller)
            }
            return basic
        }
        null
    }

    @Override
    IdentityMapping createIdentityMapping(ClassMapping classMapping) {
        Mapping mappedForm = (Mapping) createMappedForm(classMapping.entity)
        HibernatePropertyIdentity identity = mappedForm.identity
        ValueGenerator generator

        if (identity instanceof HibernateSimpleIdentity) {
            HibernateSimpleIdentity id = (HibernateSimpleIdentity) identity
            String generatorName = id.generator
            if (generatorName != null) {
                ValueGenerator resolvedGenerator
                try {
                    resolvedGenerator = ValueGenerator.valueOf(generatorName.toUpperCase(Locale.ENGLISH))
                } catch (IllegalArgumentException ignored) {
                    if (generatorName.equalsIgnoreCase('table') || ClassUtils.isPresent(generatorName)) {
                        resolvedGenerator = ValueGenerator.CUSTOM
                    } else {
                        throw new DatastoreConfigurationException(
                                "Invalid id generation strategy for entity [${classMapping.entity.name}]: $generatorName")
                    }
                }
                generator = resolvedGenerator
            } else {
                generator = ValueGenerator.AUTO
            }
        } else {
            generator = ValueGenerator.AUTO
        }
        new HibernateIdentityMapping(identity, generator, classMapping)
    }

    @Override
    protected boolean allowArbitraryCustomTypes() { true }

    @Override
    protected Class<PropertyConfig> getPropertyMappedFormType() { PropertyConfig }

    @Override
    protected Class<Mapping> getEntityMappedFormType() { Mapping }
}
