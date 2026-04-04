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

import java.util.Optional;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.ManyToOne;
import org.hibernate.mapping.Table;

import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.HibernateCompositeIdentity;
import org.grails.orm.hibernate.cfg.JoinTable;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty;

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.FOREIGN_KEY_SUFFIX;

@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class ManyToOneBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final PersistentEntityNamingStrategy namingStrategy;
    private final SimpleValueBinder simpleValueBinder;
    private final ManyToOneValuesBinder manyToOneValuesBinder;
    private final CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder;

    public ManyToOneBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            SimpleValueBinder simpleValueBinder,
            ManyToOneValuesBinder manyToOneValuesBinder,
            CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.namingStrategy = namingStrategy;
        this.simpleValueBinder = simpleValueBinder;
        this.manyToOneValuesBinder = manyToOneValuesBinder;
        this.compositeIdentifierToManyToOneBinder = compositeIdentifierToManyToOneBinder;
    }

    public ManyToOneBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            JdbcEnvironment jdbcEnvironment) {
        this(
                metadataBuildingContext,
                namingStrategy,
                new SimpleValueBinder(metadataBuildingContext, namingStrategy, jdbcEnvironment),
                new ManyToOneValuesBinder(),
                new CompositeIdentifierToManyToOneBinder(metadataBuildingContext, namingStrategy, jdbcEnvironment));
    }

    /** Binds a many-to-one association. */
    public ManyToOne bindManyToOne(HibernateManyToOneProperty property, Table table, String path) {
        return doBind(property, property.getHibernateAssociatedEntity(), table, path);
    }

    /** Binds the inverse side of a many-to-many association as a collection element. */
    public ManyToOne bindManyToOne(HibernateManyToManyProperty property, String path) {
        Collection collection = property.getCollection();
        HibernateManyToManyProperty otherSide = (HibernateManyToManyProperty) property.getHibernateInverseSide();
        Table collectionTable = collection.getCollectionTable();
        GrailsHibernatePersistentEntity refDomainClass = otherSide.getHibernateOwner();
        Optional<HibernateCompositeIdentity> compositeId = refDomainClass.getHibernateCompositeIdentity();
        if (compositeId.isEmpty() && otherSide.isCircular()) {
            prepareCircularManyToMany(otherSide);
        }
        ManyToOne manyToOne = doBind(otherSide, refDomainClass, collectionTable, path);
        manyToOne.setReferencedEntityName(otherSide.getOwner().getName());
        return manyToOne;
    }

    public ManyToOne bindManyToOne(HibernateOneToOneProperty property, String path) {
        return doBind(property, property.getHibernateAssociatedEntity(), property.getTable(), path);
    }

    private ManyToOne doBind(
            HibernateAssociation property,
            GrailsHibernatePersistentEntity refDomainClass,
            org.hibernate.mapping.Table table,
            String path) {
        ManyToOne manyToOne = new ManyToOne(metadataBuildingContext, table);
        manyToOneValuesBinder.bindManyToOneValues(property, manyToOne);
        Optional<HibernateCompositeIdentity> compositeId = refDomainClass.getHibernateCompositeIdentity();
        if (compositeId.isPresent()) {
            compositeIdentifierToManyToOneBinder.bindCompositeIdentifierToManyToOne(
                    property, manyToOne, compositeId.get(), refDomainClass, path);
        } else {
            simpleValueBinder.bindSimpleValue(property, null, manyToOne, path);
        }
        return manyToOne;
    }

    private void prepareCircularManyToMany(HibernateManyToManyProperty property) {
        Mapping ownerMapping = property.getHibernateOwner().getHibernateMappedForm();
        if (ownerMapping != null && !ownerMapping.getColumns().containsKey(property.getName())) {
            ownerMapping.getColumns().put(property.getName(), property.getHibernateMappedForm());
        }
        if (!property.getHibernateMappedForm().hasJoinKeyMapping()) {
            JoinTable jt = new JoinTable();
            ColumnConfig columnConfig = new ColumnConfig();
            columnConfig.setName(namingStrategy.resolveColumnName(property.getName()) + FOREIGN_KEY_SUFFIX);
            jt.setKey(columnConfig);
            property.getHibernateMappedForm().setJoinTable(jt);
        }
    }
}
