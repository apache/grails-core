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

import java.util.Optional;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.JoinTable;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.hibernate.MappingException;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.usertype.UserCollectionType;

/** Interface for Hibernate persistent properties */
public interface GrailsHibernatePersistentProperty extends PersistentProperty<PropertyConfig> {

  default boolean isBidirectionalManyToOneWithListMapping(Property prop) {
    return false;
  }

  default HibernateAssociation getHibernateInverseSide() {
    return this instanceof Association<?> association
        ? (HibernateAssociation) association.getInverseSide()
        : null;
  }


  default GrailsHibernatePersistentEntity getHibernateAssociatedEntity() {
    return this instanceof Association<?> association
        ? (GrailsHibernatePersistentEntity) association.getAssociatedEntity()
        : null;
  }

  /**
   * @return The type name
   */
  default String getTypeName() {
    return getTypeName(getType());
  }

  /**
   * @param propertyType The property type
   * @return The type name
   */
  default String getTypeName(Class<?> propertyType) {
    return getTypeName(propertyType, getMappedForm(), getHibernateOwner().getMappedForm());
  }

  /**
   * @param config The property config
   * @param mapping The mapping
   * @return The type name
   */
  default String getTypeName(PropertyConfig config, Mapping mapping) {
    return getTypeName(getType(), config, mapping);
  }

  /**
   * @param propertyType The property type
   * @param config The property config
   * @param mapping The mapping
   * @return The type name
   */
  default String getTypeName(Class<?> propertyType, PropertyConfig config, Mapping mapping) {
    if (this instanceof Association
        && propertyType == getType()
        && getHibernateAssociatedEntity() != null) {
      return null;
    }
    String typeName =
        Optional.ofNullable(config)
            .map(PropertyConfig::getType)
            .map(
                typeObj -> typeObj instanceof Class<?> clazz ? clazz.getName() : typeObj.toString())
            .orElseGet(() -> mapping != null ? mapping.getTypeName(propertyType) : null);

    if (typeName == null
        && propertyType != null
        && getHibernateAssociatedEntity() == null
        && !propertyType.isEnum()) {
      return propertyType.getName();
    }
    return typeName;
  }

  default GrailsHibernatePersistentEntity getHibernateOwner() {
    return getOwner() instanceof GrailsHibernatePersistentEntity ghpe ? ghpe : null;
  }

  default Class<?> getUserType() {
    PropertyConfig config = getMappedForm();
    if (config == null) return null;
    Object typeObj = config.getType();
    Class<?> userType = null;
    if (typeObj instanceof Class<?>) {
      userType = (Class<?>) typeObj;
    } else if (typeObj != null) {
      String typeName = typeObj.toString();
      try {
        userType = Class.forName(typeName, true, Thread.currentThread().getContextClassLoader());
      } catch (ClassNotFoundException e) {

      }
    }
    return userType;
  }

  default boolean isUserButNotCollectionType() {
    return getUserType() != null && !UserCollectionType.class.isAssignableFrom(getUserType());
  }

  default boolean isEnumType() {
    return Optional.ofNullable(getType()).map(Class::isEnum).orElse(false);
  }

  default boolean isHibernateOneToOne() {
    return false;
  }

  default boolean isHibernateManyToOne() {
    return false;
  }

  default boolean isEmbedded() {
    return this instanceof Embedded;
  }

  default void validateAssociation() {
    if (this instanceof Association && getUserType() != null) {
      throw new MappingException(
          "Cannot bind association property ["
              + getName()
              + "] of type ["
              + getType()
              + "] to a user type");
    }
    if (this instanceof org.grails.datastore.mapping.model.types.OneToOne oneToOne) {
      if (oneToOne.isHasOne() && !oneToOne.isBidirectional()) {
        throw new MappingException(
            "hasOne property ["
                + getName()
                + "] is not bidirectional. Specify the other side of the relationship!");
      }
    }
  }

  default boolean isSerializableType() {
    return "serializable".equals(getTypeName());
  }

  /**
   * @return true if the property has a join key mapping
   */
  default boolean isJoinKeyMapped() {
    return getMappedForm() != null
        && getMappedForm().hasJoinKeyMapping()
        && supportsJoinColumnMapping();
  }

  default String getMappedColumnName() {
    if (getMappedForm() != null) {
      return getMappedForm().getColumn();
    }
    return null;
  }

  default String getColumnName(ColumnConfig cc) {
    return Optional.of(this)
        .filter(GrailsHibernatePersistentProperty::isJoinKeyMapped)
        .map(p -> p.getMappedForm().getJoinTable().getKey().getName())
        .orElseGet(
            () ->
                Optional.ofNullable(cc)
                    .map(ColumnConfig::getName)
                    .orElseGet(this::getMappedColumnName));
  }

  /**
   * @param simpleValue The Hibernate simple value
   * @return The type name
   */
  default String getTypeName(SimpleValue simpleValue) {
    return getTypeProperty(simpleValue).getTypeName();
  }

  /**
   * @param simpleValue The Hibernate simple value
   * @return The type parameters
   */
  default java.util.Properties getTypeParameters(SimpleValue simpleValue) {
    if (getTypeName(simpleValue) != null) {
      return Optional.ofNullable(getTypeProperty(simpleValue).getMappedForm())
          .map(PropertyConfig::getTypeParams)
          .orElse(null);
    }
    return null;
  }

  /**
   * @param simpleValue The Hibernate simple value
   * @return The property that defines the type
   */
  default GrailsHibernatePersistentProperty getTypeProperty(SimpleValue simpleValue) {
    if (simpleValue instanceof DependantValue) {
      return Optional.ofNullable(getHibernateOwner().getIdentity()).orElse(this);
    }
    return this;
  }
}
