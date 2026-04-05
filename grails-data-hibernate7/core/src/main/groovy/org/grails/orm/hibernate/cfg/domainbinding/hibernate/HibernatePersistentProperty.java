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

import org.checkerframework.checker.nullness.qual.Nullable;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Table;
import org.hibernate.usertype.UserCollectionType;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PropertyConfig;

import static java.util.Optional.ofNullable;
import static org.grails.orm.hibernate.cfg.GrailsHibernateUtil.isNotEmpty;
import static org.grails.orm.hibernate.cfg.GrailsHibernateUtil.qualify;

/** Interface for Hibernate persistent properties */
public interface HibernatePersistentProperty extends PersistentProperty<PropertyConfig> {

    private static @Nullable String getMappingName(Class<?> propertyClass, Mapping mapping) {
        return ofNullable(mapping)
                .map(__ -> __.getTypeName(propertyClass))
                .orElseGet(() -> getClassName(propertyClass));
    }

    private static @Nullable String getClassName(Class<?> propertyClass) {
        return ofNullable(propertyClass)
                .filter(__ -> !__.isEnum())
                .map(Class::getName)
                .orElse(null);
    }

    default boolean isBidirectionalManyToOneWithListMapping(Property prop) {
        return false;
    }

    default HibernateAssociation getHibernateInverseSide() {
        return this instanceof Association<?> association ? (HibernateAssociation) association.getInverseSide() : null;
    }

    default GrailsHibernatePersistentEntity getHibernateAssociatedEntity() {
        return this instanceof Association<?> association ?
                (GrailsHibernatePersistentEntity) association.getAssociatedEntity() :
                null;
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
        return ofNullable(config)
                .map(PropertyConfig::getTypeName)
                .orElseGet(() -> getMappingName(propertyType, mapping));
    }

    default GrailsHibernatePersistentEntity getHibernateOwner() {
        return (GrailsHibernatePersistentEntity) getOwner();
    }

    @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
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
            } catch (ClassNotFoundException ignored) {
                // ignore
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

    /**
     * @return Whether this property is an enum property.
     */
    default boolean isEnum() {
        return this instanceof HibernateEnumProperty;
    }

    default boolean isValidHibernateOneToOne() {
        return false;
    }

    default boolean isValidHibernateManyToOne() {
        return false;
    }

    default boolean isEmbedded() {
        return this instanceof Embedded;
    }

    default void validateAssociation() {}

    default boolean isSerializableType() {
        return "serializable".equals(getTypeName());
    }

    @Override
    default boolean isLazyAble() {
        return this instanceof HibernateAssociation ||
                !(this instanceof Embedded) && !this.equals(this.getOwner().getIdentity());
    }

    /**
     * @return The mapped form
     */
    default PropertyConfig getHibernateMappedForm() {
        return getMappedForm();
    }

    /**
     * Determines if the property should be lazy.
     * @return True if it should be lazy
     */
    default boolean isLazy() {
        return getHibernateOwner().isLazy(this);
    }

    /**
     * @return true if the property has a join key mapping
     */
    default boolean isJoinKeyMapped() {
        return getMappedForm() != null && getMappedForm().hasJoinKeyMapping() && supportsJoinColumnMapping();
    }

    default String getMappedColumnName() {
        return Optional.ofNullable(getMappedForm())
                .map(PropertyConfig::getColumn)
                .orElse(null);
    }

    default String getColumnName(ColumnConfig cc) {
        return Optional.of(this)
                .filter(HibernatePersistentProperty::isJoinKeyMapped)
                .map(p -> p.getMappedForm().getJoinTable().getKey().getName())
                .orElseGet(
                        () -> Optional.ofNullable(cc).map(ColumnConfig::getName).orElseGet(this::getMappedColumnName));
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
                    .orElse(new java.util.Properties());
        }
        return new java.util.Properties();
    }

    /**
     * @param simpleValue The Hibernate simple value
     * @return The property that defines the type
     */
    default HibernatePersistentProperty getTypeProperty(SimpleValue simpleValue) {
        if (simpleValue instanceof DependantValue) {
            return Optional.ofNullable(getHibernateOwner().getIdentity()).orElse(this);
        }
        return this;
    }

    default Table getTable() {
        return getPersistentClass().getTable();
    }

    default PersistentClass getPersistentClass() {
        return getHibernateOwner().getPersistentClass();
    }

    /**
     * Returns the generator name for this property. For identity properties the generator
     * is resolved from the owning entity; for regular properties it comes from the mapped form.
     *
     * @return The generator name, or {@code null} if none is configured
     */
    default @Nullable String getGeneratorName() {
        return Optional.ofNullable(getHibernateMappedForm()).map(PropertyConfig::getGenerator).orElse(null);
    }

    default HibernatePersistentProperty validateProperty() {
        return this;
    }

    default String getNameForPropertyAndPath(String path) {
        if (isNotEmpty(path)) {
            return qualify(path, getName());
        }
        return getName();
    }
}
