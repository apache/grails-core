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
package org.grails.orm.hibernate.cfg.domainbinding.hibernate;

import java.beans.PropertyDescriptor;
import java.util.Locale;

import org.grails.datastore.mapping.config.AbstractGormMappingFactory;
import org.grails.datastore.mapping.config.groovy.MappingConfigurationBuilder;
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller;
import org.grails.datastore.mapping.model.*;
import org.grails.datastore.mapping.model.types.*;
import org.grails.datastore.mapping.reflect.ClassUtils;
import org.grails.orm.hibernate.cfg.Identity;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PropertyConfig;

/**
 * The {@link AbstractGormMappingFactory} implementation for Hibernate, responsible for
 * creating all Hibernate-specific persistent property and identity mapping instances.
 */
public class HibernateMappingFactory extends AbstractGormMappingFactory<Mapping, PropertyConfig> {

  public HibernateMappingFactory() {
  }

  @Override
  protected MappingConfigurationBuilder createConfigurationBuilder(PersistentEntity entity, Mapping mapping) {
    return new HibernateMappingBuilder(mapping, entity.getName(), defaultConstraints);
  }

  @Override
  public org.grails.datastore.mapping.model.types.Identity<PropertyConfig> createIdentity(
      PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
    HibernateIdentityProperty identity = new HibernateIdentityProperty(owner, context, pd);
    identity.setMapping(createPropertyMapping(identity, owner));
    return identity;
  }

  @Override
  public TenantId<PropertyConfig> createTenantId(
      PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
    HibernateTenantIdProperty tenantId = new HibernateTenantIdProperty(owner, context, pd);
    tenantId.setMapping(createDerivedPropertyMapping(tenantId, owner));
    return tenantId;
  }

  @Override
  public Custom<PropertyConfig> createCustom(
      PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
    final Class<?> propertyType = pd.getPropertyType();
    CustomTypeMarshaller customTypeMarshaller = findCustomType(context, propertyType);
    if (customTypeMarshaller == null && propertyType.isEnum()) {
      customTypeMarshaller = findCustomType(context, Enum.class);
    }
    HibernateCustomProperty custom = new HibernateCustomProperty(owner, context, pd, customTypeMarshaller);
    custom.setMapping(createPropertyMapping(custom, owner));
    return custom;
  }

  @Override
  public Simple<PropertyConfig> createSimple(
      PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
    HibernateSimpleProperty simple = new HibernateSimpleProperty(owner, context, pd);
    simple.setMapping(createPropertyMapping(simple, owner));
    return simple;
  }

  @Override
  public ToOne createOneToOne(
      PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
    HibernateOneToOneProperty oneToOne = new HibernateOneToOneProperty(entity, context, property);
    oneToOne.setMapping(createPropertyMapping(oneToOne, entity));
    return oneToOne;
  }

  @Override
  public ToOne createManyToOne(
      PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
    HibernateManyToOneProperty manyToOne = new HibernateManyToOneProperty(entity, context, property);
    manyToOne.setMapping(createPropertyMapping(manyToOne, entity));
    return manyToOne;
  }

  @Override
  public OneToMany createOneToMany(
      PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
    HibernateOneToManyProperty oneToMany = new HibernateOneToManyProperty(entity, context, property);
    oneToMany.setMapping(createPropertyMapping(oneToMany, entity));
    return oneToMany;
  }

  @Override
  public ManyToMany createManyToMany(
      PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
    HibernateManyToManyProperty manyToMany = new HibernateManyToManyProperty(entity, context, property);
    manyToMany.setMapping(createPropertyMapping(manyToMany, entity));
    return manyToMany;
  }

  @Override
  public Embedded createEmbedded(
      PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
    HibernateEmbeddedProperty embedded = new HibernateEmbeddedProperty(entity, context, property);
    embedded.setMapping(createPropertyMapping(embedded, entity));
    return embedded;
  }

  @Override
  public EmbeddedCollection createEmbeddedCollection(
      PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
    HibernateEmbeddedCollectionProperty embedded =
        new HibernateEmbeddedCollectionProperty(entity, context, property);
    embedded.setMapping(createPropertyMapping(embedded, entity));
    return embedded;
  }

  @Override
  public Basic createBasicCollection(
      PersistentEntity entity, MappingContext context, PropertyDescriptor property, Class collectionType) {
    if (entity instanceof GrailsHibernatePersistentEntity ghpEntity) {
      HibernateBasicProperty basic = new HibernateBasicProperty(ghpEntity, context, property);
      basic.setMapping(createPropertyMapping(basic, entity));
      CustomTypeMarshaller customTypeMarshaller = findCustomType(context, property.getPropertyType());
      if (collectionType != null && collectionType.isEnum()) {
        customTypeMarshaller = findCustomType(context, collectionType);
        if (customTypeMarshaller == null) {
          customTypeMarshaller = findCustomType(context, Enum.class);
        }
      }
      if (customTypeMarshaller != null) {
        basic.setCustomTypeMarshaller(customTypeMarshaller);
      }
      return basic;
    }
    return null;
  }

  @Override
  public IdentityMapping createIdentityMapping(final ClassMapping classMapping) {
    final Mapping mappedForm = (Mapping) createMappedForm(classMapping.getEntity());
    final HibernateIdentity identity = mappedForm.getIdentity();
    final ValueGenerator generator;

    if (identity instanceof Identity id) {
      String generatorName = id.getGenerator();
      if (generatorName != null) {
        ValueGenerator resolvedGenerator;
        try {
          resolvedGenerator = ValueGenerator.valueOf(generatorName.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
          if (generatorName.equalsIgnoreCase("table") || ClassUtils.isPresent(generatorName)) {
            resolvedGenerator = ValueGenerator.CUSTOM;
          } else {
            throw new DatastoreConfigurationException(
                String.format("Invalid id generation strategy for entity [%s]: %s",
                    classMapping.getEntity().getName(), generatorName));
          }
        }
        generator = resolvedGenerator;
      } else {
        generator = ValueGenerator.AUTO;
      }
    } else {
      generator = ValueGenerator.AUTO;
    }
    return new HibernateIdentityMapping(identity, generator, classMapping);
  }

  @Override
  protected boolean allowArbitraryCustomTypes() {
    return true;
  }

  @Override
  protected Class<PropertyConfig> getPropertyMappedFormType() {
    return PropertyConfig.class;
  }

  @Override
  protected Class<Mapping> getEntityMappedFormType() {
    return Mapping.class;
  }
}
