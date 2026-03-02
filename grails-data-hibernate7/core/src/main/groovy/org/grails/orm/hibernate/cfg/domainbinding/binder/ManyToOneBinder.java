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
import org.hibernate.mapping.ManyToOne;

import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.CompositeIdentity;
import org.grails.orm.hibernate.cfg.JoinTable;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.mapping.ManyToOne;

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
        new CompositeIdentifierToManyToOneBinder(
            metadataBuildingContext, namingStrategy, jdbcEnvironment));
  }

  /** Binds a many-to-one association. */
  public ManyToOne bindManyToOne(
      HibernateManyToOneProperty property, org.hibernate.mapping.Table table, String path) {
    GrailsHibernatePersistentEntity refDomainClass = property.getHibernateAssociatedEntity();
    return doBind(property, refDomainClass, isCompositeIdentifier(refDomainClass), table, path);
  }

  /** Binds the inverse side of a many-to-many association as a collection element. */
  public ManyToOne bindManyToOne(
      HibernateManyToManyProperty property, org.hibernate.mapping.Table table, String path) {
    GrailsHibernatePersistentEntity refDomainClass = property.getHibernateOwner();
    boolean isComposite = isCompositeIdentifier(refDomainClass);
    if (!isComposite && property.isCircular()) {
      prepareCircularManyToMany(property, refDomainClass.getMappedForm());
    }
    return doBind(property, refDomainClass, isComposite, table, path);
  }

  static boolean isCompositeIdentifier(GrailsHibernatePersistentEntity entity) {
    Mapping mapping = entity.getMappedForm();
    return mapping != null && mapping.hasCompositeIdentifier();
  }

  ManyToOne doBind(
      HibernateAssociation property,
      GrailsHibernatePersistentEntity refDomainClass,
      boolean isComposite,
      org.hibernate.mapping.Table table,
      String path) {
    ManyToOne manyToOne = new ManyToOne(metadataBuildingContext, table);
    manyToOneValuesBinder.bindManyToOneValues(property, manyToOne);
    if (isComposite) {
      Mapping mapping = refDomainClass.getMappedForm();
      CompositeIdentity ci = (CompositeIdentity) mapping.getIdentity();
      compositeIdentifierToManyToOneBinder.bindCompositeIdentifierToManyToOne(
          property, manyToOne, ci, refDomainClass, path);
    } else {
      simpleValueBinder.bindSimpleValue(property, null, manyToOne, path);
    }
    return manyToOne;
  }

  private void prepareCircularManyToMany(HibernateManyToManyProperty property, Mapping mapping) {
    PropertyConfig pc = property.getMappedForm();
    if (mapping != null && pc.getColumns().isEmpty()) {
      mapping.getColumns().put(property.getName(), pc);
    }
    if (!pc.hasJoinKeyMapping()) {
      JoinTable jt = new JoinTable();
      ColumnConfig columnConfig = new ColumnConfig();
      columnConfig.setName(
          namingStrategy.resolveColumnName(property.getName()) + FOREIGN_KEY_SUFFIX);
      jt.setKey(columnConfig);
      pc.setJoinTable(jt);
    }
  }
}
