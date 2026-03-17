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

import org.hibernate.FetchMode;
import org.hibernate.MappingException;
import org.hibernate.type.ForeignKeyDirection;

import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.mapping.OneToOneWithMapping;
import org.grails.orm.hibernate.cfg.PropertyConfig;

/** Hibernate implementation of {@link org.grails.datastore.mapping.model.types.OneToOne} */
public class HibernateOneToOneProperty extends OneToOneWithMapping<PropertyConfig> implements HibernateToOneProperty {
    public HibernateOneToOneProperty(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        super(entity, context, property);
    }

    @Override
    public void validateAssociation() {
        HibernateToOneProperty.super.validateAssociation();
        if (isHasOne() && !isBidirectional()) {
            throw new MappingException("hasOne property ["
                    + getName()
                    + "] is not bidirectional. Specify the other side of the relationship!");
        }
    }

    @Override
    public GrailsHibernatePersistentEntity getHibernateAssociatedEntity() {
        return (GrailsHibernatePersistentEntity) super.getAssociatedEntity();
    }

    @Override
    public HibernateOneToOneProperty getHibernateInverseSide() {
        return (HibernateOneToOneProperty) getInverseSide();
    }

    /** True when the FK is on this side (hasOne on the other side). Maps to Hibernate constrained. */
    public boolean isHibernateConstrained() {
        HibernateOneToOneProperty otherSide = getHibernateInverseSide();
        return otherSide != null && otherSide.isHasOne();
    }

    /**
     * The entity name that Hibernate should reference. When the other side exists, it is the other
     * side's owner; otherwise the directly associated entity.
     */
    public String getHibernateReferencedEntityName() {
        HibernateOneToOneProperty otherSide = getHibernateInverseSide();
        return otherSide != null
                ? otherSide.getOwner().getName()
                : getAssociatedEntity().getName();
    }

    /**
     * The property name on the referenced entity that back-references this association. Only
     * meaningful when {@link #isHibernateConstrained()} is false and the other side exists.
     */
    public String getHibernateReferencedPropertyName() {
        HibernateOneToOneProperty otherSide = getHibernateInverseSide();
        return otherSide != null ? otherSide.getName() : null;
    }

    /** FK direction: FROM_PARENT when constrained (hasOne on other side), TO_PARENT otherwise. */
    public ForeignKeyDirection getHibernateForeignKeyDirection() {
        return isHibernateConstrained() ? ForeignKeyDirection.FROM_PARENT : ForeignKeyDirection.TO_PARENT;
    }

    /** Resolved fetch mode: uses the configured value or falls back to {@link FetchMode#DEFAULT}. */
    public FetchMode getHibernateFetchMode() {
        PropertyConfig config = getMappedForm();
        return (config != null && config.getFetchMode() != null) ? config.getFetchMode() : FetchMode.DEFAULT;
    }

    /**
     * True when Hibernate should bind a simple column value rather than a referenced property name.
     * This is the case when the FK is on this side (constrained) or no inverse side exists.
     */
    public boolean needsSimpleValueBinding() {
        return isHibernateConstrained() || getHibernateReferencedPropertyName() == null;
    }

    public boolean isValidHibernateOneToOne() {
        validateAssociation();
        return canBindOneToOneWithSingleColumnAndForeignKey()
                || isHasOne() && isBidirectional() && getInverseSide() != null;
    }

    public boolean isValidHibernateManyToOne() {
        validateAssociation();
        return !isValidHibernateOneToOne();
    }

    @Override
    public boolean isAssociationColumnNullable() {
        if (isBidirectional() && !isOwningSide()) {
            HibernateOneToOneProperty inverseSide = getHibernateInverseSide();
            return inverseSide == null || !inverseSide.isHasOne();
        }
        return true;
    }
}
