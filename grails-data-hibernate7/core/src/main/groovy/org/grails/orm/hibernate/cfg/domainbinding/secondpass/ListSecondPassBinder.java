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
package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import java.util.Map;

import jakarta.annotation.Nonnull;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.Backref;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.IndexBackref;
import org.hibernate.mapping.List;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.Value;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover;

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.UNDERSCORE;

/** Refactored from CollectionBinder to handle list second pass binding. */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class ListSecondPassBinder {

  private final MetadataBuildingContext metadataBuildingContext;
  private final CollectionSecondPassBinder collectionSecondPassBinder;
  private final PersistentEntityNamingStrategy namingStrategy;
  private final SimpleValueColumnBinder simpleValueColumnBinder;
  private final InFlightMetadataCollector mappings;

  public ListSecondPassBinder(
      MetadataBuildingContext metadataBuildingContext,
      PersistentEntityNamingStrategy namingStrategy,
      CollectionSecondPassBinder collectionSecondPassBinder,
      SimpleValueColumnBinder simpleValueColumnBinder,
      InFlightMetadataCollector mappings) {
    this.metadataBuildingContext = metadataBuildingContext;
    this.collectionSecondPassBinder = collectionSecondPassBinder;
    this.namingStrategy = namingStrategy;
    this.simpleValueColumnBinder = simpleValueColumnBinder;
    this.mappings = mappings;
  }

  public void bindListSecondPass(
      @Nonnull HibernateToManyProperty property,
      Map<?, ?> persistentClasses,
      @Nonnull List list) {

    collectionSecondPassBinder.bindCollectionSecondPass(
        property, mappings, persistentClasses, list);
    String columnName = property.getIndexColumnName(namingStrategy);
    final boolean isManyToMany = property instanceof HibernateManyToManyProperty;

    if (isManyToMany && !property.isOwningSide()) {
      throw new MappingException(
          "Invalid association ["
              + property
              + "]. List collection types only supported on the owning side of a many-to-many relationship.");
    }

    public void bindListSecondPass(
            @Nonnull HibernateToManyProperty property, Map<?, ?> persistentClasses, @Nonnull List list) {

        collectionSecondPassBinder.bindCollectionSecondPass(property, persistentClasses, list);
        String columnName = property.getIndexColumnName(namingStrategy);
        final boolean isManyToMany = property instanceof HibernateManyToManyProperty;

        if (isManyToMany && !property.isOwningSide()) {
            throw new MappingException("Invalid association [" +
                    property +
                    "]. List collection types only supported on the owning side of a many-to-many relationship.");
        }

        Table collectionTable = list.getCollectionTable();
        String type = property.getIndexColumnType("integer");
        BasicValue iv = simpleValueColumnBinder.bindSimpleValue(
                metadataBuildingContext, collectionTable, type, columnName, true);
        list.setIndex(iv);
        list.setBaseIndex(0);
        list.setInverse(false);

        Value v = list.getElement();
        v.createForeignKey();

        if (property.isBidirectional()) {

            String entityName;
            HibernateAssociation inverseSide = property.getHibernateInverseSide();
            if (inverseSide instanceof HibernateManyToOneProperty manyToOne) {
                entityName = manyToOne.getReferencedEntityName();
            } else {
                entityName = inverseSide.getReferencedEntityName();
            }

            PersistentClass referenced = mappings.getEntityBinding(entityName);

            boolean compositeIdProperty = property.getHibernateInverseSide().isCompositeIdProperty();
            if (!compositeIdProperty) {
                Backref prop = new Backref();
                final PersistentEntity owner = property.getOwner();
                prop.setEntityName(owner.getName());
                String s2 = property.getName();
                prop.setName(UNDERSCORE +
                        new BackticksRemover().apply(owner.getJavaClass().getSimpleName()) +
                        UNDERSCORE +
                        new BackticksRemover().apply(s2) +
                        "Backref");
                prop.setSelectable(false);
                prop.setUpdatable(false);
                if (isManyToMany) {
                    prop.setInsertable(false);
                }
                prop.setCollectionRole(list.getRole());
                prop.setValue(list.getKey());

                DependantValue value = (DependantValue) prop.getValue();
                if (!property.isCircular()) {
                    value.setNullable(false);
                }
                value.setUpdateable(true);
                prop.setOptional(false);

                referenced.addProperty(prop);
            }

            if ((!list.getKey().isNullable() && !list.isInverse()) || compositeIdProperty) {
                IndexBackref ib = new IndexBackref();
                ib.setName(UNDERSCORE + property.getName() + "IndexBackref");
                ib.setUpdatable(false);
                ib.setSelectable(false);
                if (isManyToMany) {
                    ib.setInsertable(false);
                }
                ib.setCollectionRole(list.getRole());
                ib.setEntityName(list.getOwner().getEntityName());
                ib.setValue(list.getIndex());
                referenced.addProperty(ib);
            }
        }
    }
}
