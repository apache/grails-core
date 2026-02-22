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
package org.grails.orm.hibernate.cfg;

import grails.gorm.hibernate.HibernateEntity;
import groovy.lang.Closure;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.grails.datastore.gorm.GormEntity;
import org.grails.datastore.mapping.config.AbstractGormMappingFactory;
import org.grails.datastore.mapping.config.Property;
import org.grails.datastore.mapping.config.groovy.MappingConfigurationBuilder;
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller;
import org.grails.datastore.mapping.model.*;
import org.grails.datastore.mapping.reflect.ClassUtils;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsJpaMappingConfigurationStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateBasicProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateCustomProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedCollectionProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedPersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateIdentityMapping;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateIdentityProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateMappingBuilder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateTenantIdProperty;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings;
import org.grails.orm.hibernate.proxy.HibernateProxyHandler;

/**
 * A Mapping context for Hibernate
 *
 * @author Graeme Rocher
 * @since 5.0
 */
public class HibernateMappingContext extends AbstractMappingContext {

  private final HibernateMappingFactory mappingFactory;
  private final MappingConfigurationStrategy syntaxStrategy;

  /**
   * Construct a HibernateMappingContext for the given arguments
   *
   * @param settings The {@link HibernateConnectionSourceSettings} settings
   * @param contextObject The context object (for example a Spring ApplicationContext)
   * @param persistentClasses The persistent classes
   */
  public HibernateMappingContext(
      HibernateConnectionSourceSettings settings,
      Object contextObject,
      Class... persistentClasses) {
    this.mappingFactory = new HibernateMappingFactory();

    // The mapping factory needs to be configured before initialize can be safely called
    initialize(settings);

    if (settings != null) {
      this.mappingFactory.setDefaultMapping(settings.getDefault().getMapping());
      this.mappingFactory.setDefaultConstraints(settings.getDefault().getConstraints());
    }
    this.mappingFactory.setContextObject(contextObject);
    this.syntaxStrategy = new GrailsJpaMappingConfigurationStrategy(mappingFactory);
    this.proxyFactory = new HibernateProxyHandler();
    addPersistentEntities(persistentClasses);
  }

  public HibernateMappingContext(
      HibernateConnectionSourceSettings settings, Class... persistentClasses) {
    this(settings, null, persistentClasses);
  }

  public HibernateMappingContext() {
    this(new HibernateConnectionSourceSettings());
  }

  /**
   * Sets the default constraints to be used
   *
   * @param defaultConstraints The default constraints
   */
  public void setDefaultConstraints(Closure defaultConstraints) {
    this.mappingFactory.setDefaultConstraints(defaultConstraints);
  }

  @Override
  public MappingConfigurationStrategy getMappingSyntaxStrategy() {
    return syntaxStrategy;
  }

  @Override
  public MappingFactory getMappingFactory() {
    return mappingFactory;
  }

  @Override
  protected PersistentEntity createPersistentEntity(Class javaClass) {
    if (GormEntity.class.isAssignableFrom(javaClass)) {
      Object mappingStrategy = resolveMappingStrategy(javaClass);
      if (isValidMappingStrategy(javaClass, mappingStrategy)) {
        return new HibernatePersistentEntity(javaClass, this);
      }
    }
    return null;
  }

  @Override
  protected boolean isValidMappingStrategy(Class javaClass, Object mappingStrategy) {
    return HibernateEntity.class.isAssignableFrom(javaClass)
        || super.isValidMappingStrategy(javaClass, mappingStrategy);
  }

  @Override
  protected PersistentEntity createPersistentEntity(Class javaClass, boolean external) {
    return createPersistentEntity(javaClass);
  }

  @Override
  public PersistentEntity createEmbeddedEntity(Class type) {
    HibernateEmbeddedPersistentEntity embedded = new HibernateEmbeddedPersistentEntity(type, this);
    embedded.initialize();
    return embedded;
  }

  @Override
  public PersistentEntity getPersistentEntity(String name) {
    final int proxyIndicator = name.indexOf("$HibernateProxy$");
    if (proxyIndicator > -1) {
      name = name.substring(0, proxyIndicator);
    }
    return super.getPersistentEntity(name);
  }

  public Collection<GrailsHibernatePersistentEntity> getHibernatePersistentEntities(
      String dataSourceName) {
    List<GrailsHibernatePersistentEntity> result = new ArrayList<>();
    if (persistentEntities != null) {
      for (PersistentEntity entity : persistentEntities) {
        if (entity instanceof GrailsHibernatePersistentEntity hibernateEntity) {
          hibernateEntity.setDataSourceName(dataSourceName);
          result.add(hibernateEntity);
        }
      }
    }
    return result;
  }

  class HibernateMappingFactory extends AbstractGormMappingFactory<Mapping, PropertyConfig> {

    public HibernateMappingFactory() {}

    @Override
    protected MappingConfigurationBuilder createConfigurationBuilder(
        PersistentEntity entity, Mapping mapping) {
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
    public org.grails.datastore.mapping.model.types.TenantId<PropertyConfig> createTenantId(
        PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
      HibernateTenantIdProperty tenantId = new HibernateTenantIdProperty(owner, context, pd);
      tenantId.setMapping(createDerivedPropertyMapping(tenantId, owner));
      return tenantId;
    }

    @Override
    public org.grails.datastore.mapping.model.types.Custom<PropertyConfig> createCustom(
        PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
      final Class<?> propertyType = pd.getPropertyType();
      CustomTypeMarshaller customTypeMarshaller = findCustomType(context, propertyType);
      if (customTypeMarshaller == null && propertyType.isEnum()) {
        customTypeMarshaller = findCustomType(context, Enum.class);
      }
      HibernateCustomProperty custom =
          new HibernateCustomProperty(owner, context, pd, customTypeMarshaller);
      custom.setMapping(createPropertyMapping(custom, owner));
      return custom;
    }

    @Override
    public org.grails.datastore.mapping.model.types.Simple<PropertyConfig> createSimple(
        PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
      HibernateSimpleProperty simple = new HibernateSimpleProperty(owner, context, pd);
      simple.setMapping(createPropertyMapping(simple, owner));
      return simple;
    }

    @Override
    public org.grails.datastore.mapping.model.types.ToOne createOneToOne(
        PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
      HibernateOneToOneProperty oneToOne = new HibernateOneToOneProperty(entity, context, property);
      oneToOne.setMapping(createPropertyMapping(oneToOne, entity));
      return oneToOne;
    }

    @Override
    public org.grails.datastore.mapping.model.types.ToOne createManyToOne(
        PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
      HibernateManyToOneProperty manyToOne =
          new HibernateManyToOneProperty(entity, context, property);
      manyToOne.setMapping(createPropertyMapping(manyToOne, entity));
      return manyToOne;
    }

    @Override
    public org.grails.datastore.mapping.model.types.OneToMany createOneToMany(
        PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
      HibernateOneToManyProperty oneToMany =
          new HibernateOneToManyProperty(entity, context, property);
      oneToMany.setMapping(createPropertyMapping(oneToMany, entity));
      return oneToMany;
    }

    @Override
    public org.grails.datastore.mapping.model.types.ManyToMany createManyToMany(
        PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
      HibernateManyToManyProperty manyToMany =
          new HibernateManyToManyProperty(entity, context, property);
      manyToMany.setMapping(createPropertyMapping(manyToMany, entity));
      return manyToMany;
    }

    @Override
    public org.grails.datastore.mapping.model.types.Embedded createEmbedded(
        PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
      HibernateEmbeddedProperty embedded = new HibernateEmbeddedProperty(entity, context, property);
      embedded.setMapping(createPropertyMapping(embedded, entity));
      return embedded;
    }

    @Override
    public org.grails.datastore.mapping.model.types.EmbeddedCollection createEmbeddedCollection(
        PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
      HibernateEmbeddedCollectionProperty embedded =
          new HibernateEmbeddedCollectionProperty(entity, context, property);
      embedded.setMapping(createPropertyMapping(embedded, entity));
      return embedded;
    }

    @Override
    public org.grails.datastore.mapping.model.types.Basic createBasicCollection(
        PersistentEntity entity,
        MappingContext context,
        PropertyDescriptor property,
        Class collectionType) {
      HibernateBasicProperty basic =
          new HibernateBasicProperty((GrailsHibernatePersistentEntity) entity, context, property);
      basic.setMapping(createPropertyMapping(basic, entity));

      CustomTypeMarshaller customTypeMarshaller =
          findCustomType(context, property.getPropertyType());
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

    @Override
    public IdentityMapping createIdentityMapping(final ClassMapping classMapping) {
      final Mapping mappedForm = createMappedForm(classMapping.getEntity());
      final Object identity = mappedForm.getIdentity();
      final ValueGenerator generator;
      if (identity instanceof Identity) {
        Identity id = (Identity) identity;
        String generatorName = id.getGenerator();
        if (generatorName != null) {
          ValueGenerator resolvedGenerator;
          try {
            resolvedGenerator =
                ValueGenerator.valueOf(generatorName.toUpperCase(java.util.Locale.ENGLISH));
          } catch (IllegalArgumentException e) {
            if (generatorName.equalsIgnoreCase("table")) {
              resolvedGenerator = ValueGenerator.CUSTOM;
            } else if (ClassUtils.isPresent(generatorName)) {
              resolvedGenerator = ValueGenerator.CUSTOM;
            } else {
              throw new DatastoreConfigurationException(
                  "Invalid id generation strategy for entity ["
                      + classMapping.getEntity().getName()
                      + "]: "
                      + generatorName);
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
}
