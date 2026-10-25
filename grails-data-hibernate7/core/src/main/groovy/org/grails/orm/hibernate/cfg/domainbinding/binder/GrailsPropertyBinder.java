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
package org.grails.orm.hibernate.cfg.domainbinding.binder;

import jakarta.annotation.Nonnull;

import org.hibernate.MappingException;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateCustomProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEnumProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateTenantIdProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

public class GrailsPropertyBinder {

    private static final Logger LOG = LoggerFactory.getLogger(GrailsPropertyBinder.class);

    private final EnumTypeBinder enumTypeBinder;
    private final ComponentBinder componentBinder;
    private final CollectionBinder collectionBinder;
    private final SimpleValueBinder simpleValueBinder;
    private final CompositeUserTypeBinder compositeUserTypeBinder;
    private final OneToOneBinder oneToOneBinder;
    private final ManyToOneBinder manyToOneBinder;
    private final ForeignKeyOneToOneBinder foreignKeyOneToOneBinder;

    public GrailsPropertyBinder(
            EnumTypeBinder enumTypeBinder,
            ComponentBinder componentBinder,
            CollectionBinder collectionBinder,
            SimpleValueBinder simpleValueBinder,
            CompositeUserTypeBinder compositeUserTypeBinder,
            OneToOneBinder oneToOneBinder,
            ManyToOneBinder manyToOneBinder,
            ForeignKeyOneToOneBinder foreignKeyOneToOneBinder) {
        this.enumTypeBinder = enumTypeBinder;
        this.componentBinder = componentBinder;
        this.collectionBinder = collectionBinder;
        this.simpleValueBinder = simpleValueBinder;
        this.compositeUserTypeBinder = compositeUserTypeBinder;
        this.oneToOneBinder = oneToOneBinder;
        this.manyToOneBinder = manyToOneBinder;
        this.foreignKeyOneToOneBinder = foreignKeyOneToOneBinder;
    }

    /**
     * Creates a property binder without a {@link CompositeUserTypeBinder}. It binds every property the other
     * constructor binds, except one mapped with a Hibernate {@link org.hibernate.usertype.CompositeUserType}, for
     * which {@link #bindProperty} throws a {@link MappingException}.
     *
     * @deprecated use the constructor that takes a {@link CompositeUserTypeBinder}
     */
    @Deprecated(since = "8.0.1")
    public GrailsPropertyBinder(
            EnumTypeBinder enumTypeBinder,
            ComponentBinder componentBinder,
            CollectionBinder collectionBinder,
            SimpleValueBinder simpleValueBinder,
            OneToOneBinder oneToOneBinder,
            ManyToOneBinder manyToOneBinder,
            ForeignKeyOneToOneBinder foreignKeyOneToOneBinder) {
        this(
                enumTypeBinder,
                componentBinder,
                collectionBinder,
                simpleValueBinder,
                null,
                oneToOneBinder,
                manyToOneBinder,
                foreignKeyOneToOneBinder);
    }

    public Value bindProperty(
            @Nonnull HibernatePersistentProperty currentGrailsProp,
            HibernatePersistentProperty parentProperty,
            String path) {
        Table table = currentGrailsProp.getTable();
        if (LOG.isDebugEnabled()) {
            LOG.debug("[GrailsPropertyBinder] Binding persistent property [{}]", currentGrailsProp.getName());
        }

        Value value;

        if (currentGrailsProp.getCompositeUserType() != null) {
            // A composite user type spans one column per attribute of its embeddable class, so it is a
            // component of that type rather than a basic value: Hibernate resolves the type of a basic value
            // as a BasicType or a UserType only. This comes before the enum branch, since an enum property
            // mapped with a composite user type is such a component too, not an enum column.
            value = bindCompositeUserType(currentGrailsProp, parentProperty, path);
        } else if (currentGrailsProp instanceof HibernateEnumProperty hibernateEnumProperty &&
                !hibernateEnumProperty.isCollectionElement()) {
            // A hasMany-of-enum property is also a HibernateEnumProperty, but it must still go
            // through collectionBinder.bindCollection() below so its join table gets created;
            // EnumTypeBinder only binds its element column, from BasicCollectionElementBinder.
            value = enumTypeBinder.bindEnumType(hibernateEnumProperty, path);
        } else if (currentGrailsProp.isUserButNotCollectionType()) {
            value = simpleValueBinder.bindBasicValue(currentGrailsProp, parentProperty, path);
        } else if (currentGrailsProp instanceof HibernateOneToOneProperty oneToOne &&
                oneToOne.isValidHibernateOneToOne()) {
            value = oneToOneBinder.bindOneToOne(oneToOne, path);
        } else if (currentGrailsProp instanceof HibernateOneToOneProperty oneToOne) {
            value = foreignKeyOneToOneBinder.bind(oneToOne, path);
        } else if (currentGrailsProp instanceof HibernateManyToOneProperty manyToOne) {
            value = manyToOneBinder.bindManyToOne(manyToOne, table, path);
        } else if (HibernateToManyProperty.isBoundAsCollection(currentGrailsProp)) {
            value = collectionBinder.bindCollection((HibernateToManyProperty) currentGrailsProp, path);
        } else if (currentGrailsProp instanceof HibernateEmbeddedProperty embedded) {
            value = componentBinder.bindComponent(embedded, path);
        } else if (currentGrailsProp instanceof HibernateSimpleProperty simple) {
            value = simpleValueBinder.bindBasicValue(simple, parentProperty, path);
        } else if (currentGrailsProp instanceof HibernateCustomProperty custom) {
            value = simpleValueBinder.bindBasicValue(custom, parentProperty, path);
        } else if (currentGrailsProp instanceof HibernateTenantIdProperty tenantId) {
            value = simpleValueBinder.bindBasicValue(tenantId, parentProperty, path);
        } else if (currentGrailsProp instanceof HibernateToManyProperty toMany &&
                currentGrailsProp.isSerializableType()) {
            value = simpleValueBinder.bindBasicValue(toMany, parentProperty, path);
        } else {
            throw new RuntimeException(
                    "Unsupported property type: " + currentGrailsProp.getClass().getName());
        }

        return value;
    }

    private Value bindCompositeUserType(
            HibernatePersistentProperty property, HibernatePersistentProperty parentProperty, String path) {
        if (compositeUserTypeBinder == null) {
            throw new MappingException("Property [" + property.getName() + "] of class [" +
                    property.getOwner().getName() + "] is mapped with the composite user type [" +
                    property.getCompositeUserType().getName() + "], which a " +
                    GrailsPropertyBinder.class.getSimpleName() + " created without a " +
                    CompositeUserTypeBinder.class.getSimpleName() + " cannot bind");
        }
        return compositeUserTypeBinder.bindCompositeUserType(property, parentProperty, path);
    }
}
