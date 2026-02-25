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

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.FOREIGN_KEY_SUFFIX;

import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.CompositeIdentity;
import org.grails.orm.hibernate.cfg.JoinTable;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher;
import org.hibernate.MappingException;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.ManyToOne;

public class ManyToOneBinder {

  private final MetadataBuildingContext metadataBuildingContext;
  private final PersistentEntityNamingStrategy namingStrategy;
  private final SimpleValueBinder simpleValueBinder;
  private final ManyToOneValuesBinder manyToOneValuesBinder;
  private final CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder;
  private final SimpleValueColumnFetcher simpleValueColumnFetcher;

  public ManyToOneBinder(
      MetadataBuildingContext metadataBuildingContext,
      PersistentEntityNamingStrategy namingStrategy,
      SimpleValueBinder simpleValueBinder,
      ManyToOneValuesBinder manyToOneValuesBinder,
      CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder,
      SimpleValueColumnFetcher simpleValueColumnFetcher) {
    this.metadataBuildingContext = metadataBuildingContext;
    this.namingStrategy = namingStrategy;
    this.simpleValueBinder = simpleValueBinder;
    this.manyToOneValuesBinder = manyToOneValuesBinder;
    this.compositeIdentifierToManyToOneBinder = compositeIdentifierToManyToOneBinder;
    this.simpleValueColumnFetcher = simpleValueColumnFetcher;
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
            metadataBuildingContext, namingStrategy, jdbcEnvironment),
        new SimpleValueColumnFetcher());
  }

  /** Binds a many-to-one relationship to the */
  @SuppressWarnings("unchecked")
  public ManyToOne bindManyToOne(
      HibernateAssociation property, org.hibernate.mapping.Table table, String path) {
    ManyToOne manyToOne = new ManyToOne(metadataBuildingContext, table);
    manyToOneValuesBinder.bindManyToOneValues(property, manyToOne);
    GrailsHibernatePersistentEntity refDomainClass =
        (property instanceof HibernateManyToManyProperty
            ? property.getHibernateOwner()
            : property.getHibernateAssociatedEntity());
    Mapping mapping = refDomainClass.getMappedForm();

    boolean isComposite = mapping != null && mapping.hasCompositeIdentifier();
    if (isComposite) {
      CompositeIdentity ci = (CompositeIdentity) mapping.getIdentity();
      compositeIdentifierToManyToOneBinder.bindCompositeIdentifierToManyToOne(
          property, manyToOne, ci, refDomainClass, path);
    } else {
      if (property.isCircular() && (property instanceof HibernateManyToManyProperty)) {
        PropertyConfig pc = property.getMappedForm();

        if (mapping != null && pc.getColumns().isEmpty()) {
          mapping.getColumns().put(property.getName(), pc);
        }
        if (!pc.hasJoinKeyMapping()) {
          JoinTable jt = new JoinTable();
          final ColumnConfig columnConfig = new ColumnConfig();
          columnConfig.setName(
              namingStrategy.resolveColumnName(property.getName()) + FOREIGN_KEY_SUFFIX);
          jt.setKey(columnConfig);
          pc.setJoinTable(jt);
        }
        // set type
        simpleValueBinder.bindSimpleValue(property, null, manyToOne, path);
      } else {
        // bind column
        // set type
        simpleValueBinder.bindSimpleValue(property, null, manyToOne, path);
      }
    }

    PropertyConfig config = property.getMappedForm();
    boolean isOneToOne = property instanceof HibernateOneToOneProperty;
    boolean notComposite = !isComposite;
    if (isOneToOne && notComposite) {
      manyToOne.setAlternateUniqueKey(true);
      Column c = simpleValueColumnFetcher.getColumnForSimpleValue(manyToOne);
      if (c == null) {
        throw new MappingException("There is no column for property [" + property.getName() + "]");
      }
      if (!config.isUniqueWithinGroup()) {
        c.setUnique(config.isUnique());
      } else {
        if (property.isBidirectional()
            && property.getHibernateInverseSide() instanceof HibernateToOneProperty inverseSide
            && inverseSide.isHibernateOneToOne()) {
          c.setUnique(true);
        }
      }
    }
    return manyToOne;
  }
}
