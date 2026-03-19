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

import java.util.List;

import org.hibernate.MappingException;
import org.hibernate.mapping.ManyToOne;
import org.hibernate.mapping.Property;

import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PropertyConfig;

/**
 * Common interface for all Hibernate association properties (both ToOne and ToMany). Extends {@link
 * HibernatePersistentProperty} and declares the key {@link
 * org.grails.datastore.mapping.model.types.Association} methods directly so callers can use them
 * without casting. Note: {@code Association} is an abstract class so cannot be listed as a
 * super-interface; the implementing classes satisfy these contracts through their class hierarchy.
 *
 * @see HibernateToOneProperty
 * @see HibernateToManyProperty
 */
public interface HibernateAssociation extends HibernatePersistentProperty {

    // --- Association contract (satisfied by the class hierarchy of all implementors) ---

    org.grails.datastore.mapping.model.PersistentProperty<?> getInverseSide();

    org.grails.datastore.mapping.model.PersistentEntity getAssociatedEntity();

    boolean isBidirectional();

    boolean isOwningSide();

    boolean isCircular();

    boolean isBidirectionalOneToManyMap();

    /**
     * Returns the nullable value for the FK column when this property is an association without a
     * user type. The default is {@code true}; subtypes override for their specific semantics.
     */
    default boolean isAssociationColumnNullable() {
        return true;
    }

    // --- Hibernate-typed overrides, removing instanceof guards ---

    /** Returns the inverse side as a {@link HibernateAssociation}, eliminating cast at call sites. */
    @Override
    default HibernateAssociation getHibernateInverseSide() {
        return (HibernateAssociation) getInverseSide();
    }

    @Override
    default GrailsHibernatePersistentEntity getHibernateAssociatedEntity() {
        return (GrailsHibernatePersistentEntity) getAssociatedEntity();
    }

    default String getReferencedEntityName() {
        return getHibernateAssociatedEntity().getName();
    }

    @Override
    default void validateAssociation() {
        if (getUserType() != null) {
            throw new MappingException(
                    "Cannot bind association property [" + getName() + "] of type [" + getType() + "] to a user type");
        }
    }

    default boolean isBidirectionalManyToOneWithListMapping(Property prop) {
        return isBidirectional() &&
                getInverseSide() != null &&
                List.class.isAssignableFrom(getType()) &&
                prop != null &&
                prop.getValue() instanceof ManyToOne;
    }

    /**
     * @param propertyType The property type
     * @param config The property config
     * @param mapping The mapping
     * @return The type name
     */
    default String getTypeName(Class<?> propertyType, PropertyConfig config, Mapping mapping) {
        if (propertyType == getType() && getHibernateAssociatedEntity() != null) {
            return null;
        }
        return HibernatePersistentProperty.super.getTypeName(propertyType, config, mapping);
    }
}
