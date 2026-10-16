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

import groovy.transform.CompileStatic
import org.hibernate.FetchMode
import org.hibernate.MappingException
import org.hibernate.type.ForeignKeyDirection

import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.mapping.OneToOneWithMapping
import org.grails.orm.hibernate.cfg.PropertyConfig

import java.beans.PropertyDescriptor

/** Hibernate implementation of {@link org.grails.datastore.mapping.model.types.OneToOne} */
@CompileStatic
class HibernateOneToOneProperty extends OneToOneWithMapping<PropertyConfig> implements HibernateToOneProperty {

    HibernateOneToOneProperty(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        super(entity, context, property)
    }

    @Override
    void validateAssociation() {
        HibernateToOneProperty.super.validateAssociation()
        if (hasOne && !bidirectional) {
            throw new MappingException("hasOne property [${name}] is not bidirectional. " +
                    'Specify the other side of the relationship!')
        }
    }

    @Override
    GrailsHibernatePersistentEntity getHibernateAssociatedEntity() {
        return (GrailsHibernatePersistentEntity) super.getAssociatedEntity()
    }

    @Override
    HibernateOneToOneProperty getHibernateInverseSide() {
        return (HibernateOneToOneProperty) inverseSide
    }

    /** True when the FK is on this side (hasOne on the other side). Maps to Hibernate constrained. */
    boolean isHibernateConstrained() {
        HibernateOneToOneProperty otherSide = hibernateInverseSide
        return otherSide != null && otherSide.hasOne
    }

    /**
     * The entity name that Hibernate should reference. When the other side exists, it is the other
     * side's owner; otherwise the directly associated entity.
     */
    String getHibernateReferencedEntityName() {
        HibernateOneToOneProperty otherSide = hibernateInverseSide
        return otherSide != null ?
                otherSide.owner.name :
                associatedEntity.name
    }

    /**
     * The property name on the referenced entity that back-references this association. Only
     * meaningful when {@link #isHibernateConstrained()} is false and the other side exists.
     */
    String getHibernateReferencedPropertyName() {
        HibernateOneToOneProperty otherSide = hibernateInverseSide
        return otherSide != null ? otherSide.name : null
    }

    /** FK direction: FROM_PARENT when constrained (hasOne on other side), TO_PARENT otherwise. */
    ForeignKeyDirection getHibernateForeignKeyDirection() {
        return hibernateConstrained ? ForeignKeyDirection.FROM_PARENT : ForeignKeyDirection.TO_PARENT
    }

    /** Resolved fetch mode: uses the configured value or falls back to {@link FetchMode#DEFAULT}. */
    FetchMode getHibernateFetchMode() {
        PropertyConfig config = hibernateMappedForm
        return (config != null && config.fetchMode != null) ? config.fetchMode : FetchMode.DEFAULT
    }

    /**
     * True when Hibernate should bind a simple column value rather than a referenced property name.
     * This is the case when the FK is on this side (constrained) or no inverse side exists.
     */
    boolean needsSimpleValueBinding() {
        return hibernateConstrained || hibernateReferencedPropertyName == null
    }

    @Override
    boolean isValidHibernateOneToOne() {
        validateAssociation()
        return canBindOneToOneWithSingleColumnAndForeignKey() ||
                hasOne && bidirectional && inverseSide != null
    }

    @Override
    boolean isValidHibernateManyToOne() {
        validateAssociation()
        return !validHibernateOneToOne
    }

    @Override
    boolean isAssociationColumnNullable() {
        if (bidirectional && !owningSide) {
            HibernateOneToOneProperty inverseSide = hibernateInverseSide
            return inverseSide == null || !inverseSide.hasOne
        }
        return true
    }

}
