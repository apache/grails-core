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

import org.grails.datastore.mapping.model.types.Association;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.hibernate.FetchMode;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.mapping.OneToOne;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.type.ForeignKeyDirection;

public class OneToOneBinder {

  private final MetadataBuildingContext metadataBuildingContext;
  private final SimpleValueBinder simpleValueBinder;

  public OneToOneBinder(
      MetadataBuildingContext metadataBuildingContext,
      SimpleValueBinder simpleValueBinder) {
    this.metadataBuildingContext = metadataBuildingContext;
    this.simpleValueBinder = simpleValueBinder;
  }

  public OneToOneBinder(
      MetadataBuildingContext metadataBuildingContext,
      PersistentEntityNamingStrategy namingStrategy,
      JdbcEnvironment jdbcEnvironment) {
    this(
        metadataBuildingContext,
            new SimpleValueBinder(metadataBuildingContext, namingStrategy, jdbcEnvironment));
  }

  public OneToOne bindOneToOne(
      final org.grails.datastore.mapping.model.types.OneToOne property,
      PersistentClass owner,
      org.hibernate.mapping.Table table,
      String path) {
    OneToOne oneToOne = new OneToOne(metadataBuildingContext, table, owner);
    PropertyConfig config = ((HibernatePersistentProperty) property).getMappedForm();
    final Association otherSide = property.getInverseSide();

    final boolean hasOne = otherSide != null && otherSide.isHasOne();
    oneToOne.setConstrained(hasOne);
    oneToOne.setForeignKeyType(
        oneToOne.isConstrained() ? ForeignKeyDirection.FROM_PARENT : ForeignKeyDirection.TO_PARENT);
    oneToOne.setAlternateUniqueKey(true);

    if (config != null && config.getFetchMode() != null) {
      oneToOne.setFetchMode(config.getFetchMode());
    } else {
      oneToOne.setFetchMode(FetchMode.DEFAULT);
    }

    oneToOne.setReferencedEntityName(
        otherSide != null
            ? otherSide.getOwner().getName()
            : property.getAssociatedEntity().getName());
    oneToOne.setPropertyName(property.getName());
    oneToOne.setReferenceToPrimaryKey(false);

    if (hasOne || otherSide == null) {
      simpleValueBinder.bindSimpleValue(
          (HibernatePersistentProperty) property, null, oneToOne, path);
    } else {
      oneToOne.setReferencedPropertyName(otherSide.getName());
    }
    return oneToOne;
  }
}
