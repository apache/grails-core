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

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.JPA_DEFAULT_DISCRIMINATOR_TYPE;

import jakarta.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.orm.hibernate.cfg.CompositeIdentity;
import org.grails.orm.hibernate.cfg.DiscriminatorConfig;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.util.ConfigureDerivedPropertiesConsumer;
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher;
import org.grails.orm.hibernate.cfg.domainbinding.util.NamespaceNameExtractor;
import org.hibernate.boot.spi.InFlightMetadataCollector;

/** Common interface for Hibernate persistent entities */
public interface GrailsHibernatePersistentEntity extends PersistentEntity {
  Mapping getMappedForm();

  @Nonnull
  default GrailsHibernatePersistentEntity getHibernateRootEntity() {
    return (GrailsHibernatePersistentEntity) getRootEntity();
  }

  default Mapping getRootMapping() {
    return getHibernateRootEntity().getMappedForm();
  }

  default boolean isTablePerHierarchy() {
    Mapping mapping = getMappedForm();
    return mapping == null || mapping.getTablePerHierarchy();
  }

  default boolean isTablePerConcreteClass() {
    Mapping mapping = getMappedForm();
    return mapping != null && mapping.isTablePerConcreteClass();
  }

  default boolean isTableAbstract() {
    return !isTablePerHierarchy() && isTablePerConcreteClass() && isAbstract();
  }

  default boolean isTablePerHierarchySubclass() {
    Mapping rootMapping = getRootMapping();
    return !this.isRoot() && (rootMapping == null || rootMapping.getTablePerHierarchy());
  }

  default Set<String> buildDiscriminatorSet() {
    String quote =
        Optional.ofNullable(getRootMapping())
            .filter(m -> m.getDatasources() != null)
            .map(Mapping::getDiscriminator)
            .filter(config -> config.getType() != null && !config.getType().equals("string"))
            .map(config -> "")
            .orElse("'");

    String quotedDiscriminator = quote + getDiscriminatorValue() + quote;

    return Stream.concat(
            Stream.of(quotedDiscriminator),
            getChildEntities().stream()
                .map(GrailsHibernatePersistentEntity::buildDiscriminatorSet)
                .flatMap(Collection::stream))
        .collect(Collectors.toSet());
  }

  @Override
  HibernatePersistentProperty getIdentity();

  @Override
  HibernatePersistentProperty[] getCompositeIdentity();

  default Optional<CompositeIdentity> getHibernateCompositeIdentity() {
    return Optional.ofNullable(getMappedForm())
        .filter(Mapping::hasCompositeIdentifier)
        .map(Mapping::getIdentity)
        .filter(CompositeIdentity.class::isInstance)
        .map(CompositeIdentity.class::cast);
  }

  default String getDiscriminatorValue() {
    return Optional.ofNullable(getMappedForm())
        .map(Mapping::getDiscriminator)
        .map(DiscriminatorConfig::getValue)
        .orElse(getJavaClass().getSimpleName());
  }

  void setDataSourceName(String dataSourceName);

  String getDataSourceName();

  boolean forGrailsDomainMapping(String dataSourceName);

  boolean usesConnectionSource(String dataSourceName);

  boolean isAbstract();

  /**
   * @return The properties that should be bound to the Hibernate meta model
   */
  default List<HibernatePersistentProperty> getPersistentPropertiesToBind() {
    List<HibernatePersistentProperty> properties = getHibernatePersistentProperties();
    if (properties == null) {
      return java.util.Collections.emptyList();
    }
    return properties.stream()
        .filter(Objects::nonNull)
        .filter(p -> p.getMappedForm() != null)
        .filter(p -> !p.isIdentityProperty())
        .filter(p -> !p.isCompositeIdProperty())
        .filter(p -> !GormProperties.VERSION.equals(p.getName()))
        .filter(p -> !p.isInherited())
        .toList();
  }

  @Override
  HibernatePersistentProperty getVersion();

  /**
   * @param parentType The type of the parent entity
   * @return The parent property if it exists
   */
  default Optional<HibernatePersistentProperty> getHibernateParentProperty(Class<?> parentType) {
    List<HibernatePersistentProperty> properties = getHibernatePersistentProperties();
    if (properties == null) {
      return Optional.empty();
    }
    return properties.stream()
        .filter(Objects::nonNull)
        .filter(p -> p.getType().equals(parentType))
        .findFirst();
  }

  /**
   * @param parentType The type of the parent entity to exclude from the results
   * @return The properties that should be bound to the Hibernate meta model
   */
  default List<HibernatePersistentProperty> getHibernatePersistentProperties(Class<?> parentType) {
    List<HibernatePersistentProperty> properties = getHibernatePersistentProperties();
    if (properties == null) {
      return java.util.Collections.emptyList();
    }
    return properties.stream()
        .filter(Objects::nonNull)
        .filter(p -> p.getMappedForm() != null)
        .filter(p -> !p.equals(getIdentity()))
        .filter(p -> !GormProperties.VERSION.equals(p.getName()))
        .filter(p -> !p.getType().equals(parentType))
        .toList();
  }

  default List<GrailsHibernatePersistentEntity> getChildEntities() {
    return getChildEntities(getDataSourceName());
  }

  default List<GrailsHibernatePersistentEntity> getChildEntities(String dataSourceName) {
    return getMappingContext().getDirectChildEntities(this).stream()
        .filter(GrailsHibernatePersistentEntity.class::isInstance)
        .map(GrailsHibernatePersistentEntity.class::cast)
        .filter(persistentEntity -> persistentEntity.usesConnectionSource(dataSourceName))
        .filter(sub -> sub.getJavaClass().getSuperclass().equals(this.getJavaClass()))
        .toList();
  }

  default boolean isComponentPropertyNullable(PersistentProperty embeddedProperty) {
    if (embeddedProperty == null) return false;
    final Mapping mapping = getMappedForm();
    return !isRoot() && (mapping == null || mapping.isTablePerHierarchy())
        || embeddedProperty.isNullable();
  }

  default void configureDerivedProperties() {
    getHibernatePersistentProperties()
        .forEach(new ConfigureDerivedPropertiesConsumer(getMappedForm()));
  }

  default HibernatePersistentProperty getHibernateTenantId() {
    return (HibernatePersistentProperty) getTenantId();
  }

  default String getMultiTenantFilterCondition(DefaultColumnNameFetcher fetcher) {
    return Optional.ofNullable(getHibernateTenantId())
        .map(fetcher::getDefaultColumnName)
        .map(defaultColumnName -> ":tenantId = " + defaultColumnName)
        .orElse(null);
  }

  default String getSchema(@Nonnull InFlightMetadataCollector mappings) {
    return Optional.ofNullable(getMappedForm())
        .map(Mapping::getTable)
        .map(org.grails.orm.hibernate.cfg.Table::getSchema)
        .orElse(NamespaceNameExtractor.getSchemaName(mappings));
  }

  default String getCatalog(@Nonnull InFlightMetadataCollector mappings) {
    return Optional.ofNullable(getMappedForm())
        .map(Mapping::getTable)
        .map(org.grails.orm.hibernate.cfg.Table::getCatalog)
        .orElse(NamespaceNameExtractor.getCatalogName(mappings));
  }

  /**
   * Evaluates the table name for the given entity
   *
   * @param persistentEntityNamingStrategy The naming strategy
   * @return The table name
   */
  default String getTableName(PersistentEntityNamingStrategy persistentEntityNamingStrategy) {
    return Optional.ofNullable(getMappedForm())
        .map(Mapping::getTableName)
        .or(
            () ->
                Optional.ofNullable(getRootMapping())
                    .filter(Mapping::isTablePerHierarchy)
                    .map(Mapping::getTableName))
        .orElseGet(() -> persistentEntityNamingStrategy.resolveTableName(this));
  }

  default String getDiscriminatorColumnName() {
    return Optional.ofNullable(getRootMapping())
        .map(Mapping::getDiscriminator)
        .map(GrailsHibernatePersistentEntity::resolveDiscriminatorValue)
        .orElse(JPA_DEFAULT_DISCRIMINATOR_TYPE);
  }

  private static String resolveDiscriminatorValue(DiscriminatorConfig discriminatorConfig) {
    return discriminatorConfig.getColumn() != null
        ? discriminatorConfig.getColumn().getName()
        : discriminatorConfig.getFormula();
  }

  default List<HibernatePersistentProperty> getHibernatePersistentProperties() {
    var properties =
        new java.util.ArrayList<>(
            getPersistentProperties().stream()
                .filter(HibernatePersistentProperty.class::isInstance)
                .map(HibernatePersistentProperty.class::cast)
                .toList());
    properties.sort(
        (p1, p2) -> {
          if (p1
                  instanceof
                  org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty
              && !(p2
                  instanceof
                  org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty)) {
            return -1;
          } else if (!(p1
                  instanceof
                  org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty)
              && p2
                  instanceof
                  org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty) {
            return 1;
          }
          return p1.getName().compareTo(p2.getName());
        });
    return properties;
  }
  ;
}
