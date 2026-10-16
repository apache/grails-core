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
package org.grails.orm.hibernate.cfg.domainbinding.binder

import groovy.transform.CompileStatic
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.mapping.Collection
import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.Table

import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.HibernateCompositeIdentity
import org.grails.orm.hibernate.cfg.JoinTable
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.FOREIGN_KEY_SUFFIX

@CompileStatic
@SuppressWarnings('PMD.DataflowAnomalyAnalysis')
class ManyToOneBinder {

    private final MetadataBuildingContext metadataBuildingContext
    private final PersistentEntityNamingStrategy namingStrategy
    private final SimpleValueBinder simpleValueBinder
    private final ManyToOneValuesBinder manyToOneValuesBinder
    private final CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder

    ManyToOneBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            SimpleValueBinder simpleValueBinder,
            ManyToOneValuesBinder manyToOneValuesBinder,
            CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder) {
        this.metadataBuildingContext = metadataBuildingContext
        this.namingStrategy = namingStrategy
        this.simpleValueBinder = simpleValueBinder
        this.manyToOneValuesBinder = manyToOneValuesBinder
        this.compositeIdentifierToManyToOneBinder = compositeIdentifierToManyToOneBinder
    }

    ManyToOneBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            JdbcEnvironment jdbcEnvironment) {
        this(
                metadataBuildingContext,
                namingStrategy,
                new SimpleValueBinder(metadataBuildingContext, namingStrategy, jdbcEnvironment),
                new ManyToOneValuesBinder(),
                new CompositeIdentifierToManyToOneBinder(metadataBuildingContext, namingStrategy, jdbcEnvironment))
    }

    /** Binds a many-to-one association. */
    ManyToOne bindManyToOne(HibernateManyToOneProperty property, Table table, String path) {
        return doBind(property, property.hibernateAssociatedEntity, table, path)
    }

    /** Binds the inverse side of a many-to-many association as a collection element. */
    ManyToOne bindManyToOne(HibernateManyToManyProperty property, String path) {
        Collection collection = property.collection
        HibernateManyToManyProperty otherSide = (HibernateManyToManyProperty) property.hibernateInverseSide
        Table collectionTable = collection.collectionTable
        GrailsHibernatePersistentEntity refDomainClass = otherSide.hibernateOwner
        if (otherSide.isCircular()) {
            prepareCircularManyToMany(otherSide)
        }
        ManyToOne manyToOne = doBind(otherSide, refDomainClass, collectionTable, path)
        manyToOne.referencedEntityName = otherSide.owner.name
        return manyToOne
    }

    ManyToOne bindManyToOne(HibernateOneToOneProperty property, String path) {
        return doBind(property, property.hibernateAssociatedEntity, property.table, path)
    }

    private ManyToOne doBind(
            HibernateAssociation property,
            GrailsHibernatePersistentEntity refDomainClass,
            Table table,
            String path) {
        ManyToOne manyToOne = new ManyToOne(metadataBuildingContext, table)
        manyToOneValuesBinder.bindManyToOneValues(property, manyToOne)
        Optional<HibernateCompositeIdentity> compositeId = refDomainClass.hibernateCompositeIdentity
        if (compositeId.isPresent()) {
            compositeIdentifierToManyToOneBinder.bindCompositeIdentifierToManyToOne(
                    property, manyToOne, compositeId.get(), refDomainClass, path)
        } else {
            simpleValueBinder.bindSimpleValue(property, null, manyToOne, path)
        }
        return manyToOne
    }

    private void prepareCircularManyToMany(HibernateManyToManyProperty property) {
        Mapping ownerMapping = property.hibernateOwner.hibernateMappedForm
        if (ownerMapping != null && !ownerMapping.columns.containsKey(property.name)) {
            ownerMapping.columns.put(property.name, property.hibernateMappedForm)
        }
        if (!property.hibernateMappedForm.hasJoinKeyMapping()) {
            JoinTable jt = new JoinTable()
            Optional<HibernateCompositeIdentity> compositeId = property.hibernateOwner.hibernateCompositeIdentity
            List<ColumnConfig> keyColumns = []
            if (compositeId.isPresent() && compositeId.get().propertyNames != null && compositeId.get().propertyNames.length > 0) {
                List<ColumnConfig> joinKeys = property.hibernateMappedForm.joinTable != null ?
                        property.hibernateMappedForm.joinTable.keys : null
                String[] propNames = compositeId.get().propertyNames
                if (joinKeys != null && joinKeys.size() == propNames.length) {
                    for (int i = 0; i < propNames.length; i++) {
                        ColumnConfig cc = new ColumnConfig()
                        cc.name = joinKeys.get(i).name
                        keyColumns.add(cc)
                    }
                } else {
                    for (String propName : propNames) {
                        ColumnConfig cc = new ColumnConfig()
                        cc.name = namingStrategy.resolveColumnName(propName) + FOREIGN_KEY_SUFFIX
                        keyColumns.add(cc)
                    }
                }
            } else {
                ColumnConfig cc = new ColumnConfig()
                cc.name = namingStrategy.resolveColumnName(property.name) + FOREIGN_KEY_SUFFIX
                keyColumns.add(cc)
            }
            jt.keys = keyColumns
            property.hibernateMappedForm.joinTable = jt
        }
    }

}
