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

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.JPA_DEFAULT_DISCRIMINATOR_TYPE;

import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.DiscriminatorConfig;
import org.grails.orm.hibernate.cfg.Mapping;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Formula;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Table;

public class DiscriminatorPropertyBinder {

  private static final String STRING_TYPE = "string";

  private final MetadataBuildingContext metadataBuildingContext;
  private final SimpleValueColumnBinder simpleValueColumnBinder;
  private final ColumnConfigToColumnBinder columnConfigToColumnBinder;

  public DiscriminatorPropertyBinder(
      MetadataBuildingContext metadataBuildingContext,
      SimpleValueColumnBinder simpleValueColumnBinder,
      ColumnConfigToColumnBinder columnConfigToColumnBinder) {
    this.metadataBuildingContext = metadataBuildingContext;
    this.simpleValueColumnBinder = simpleValueColumnBinder;
    this.columnConfigToColumnBinder = columnConfigToColumnBinder;
  }

  /**
   * Creates and binds the discriminator property used in table-per-hierarchy inheritance to
   * discriminate between sub class instances
   *
   * @param entity The root class entity
   * @param someMapping The mappings instance
   */
  public void bindDiscriminatorProperty(RootClass entity, Mapping someMapping) {
    Table table = entity.getTable();
    SimpleValue d = new BasicValue(metadataBuildingContext, table);
    entity.setDiscriminator(d);
    DiscriminatorConfig discriminatorConfig = someMapping.getDiscriminator();

    boolean hasDiscriminatorConfig = discriminatorConfig != null;
    entity.setDiscriminatorValue(
        hasDiscriminatorConfig ? discriminatorConfig.getValue() : entity.getClassName());

    String typeName = STRING_TYPE;
    if (hasDiscriminatorConfig) {
      if (discriminatorConfig.getInsertable() != null) {
        entity.setDiscriminatorInsertable(discriminatorConfig.getInsertable());
      }
      Object type = discriminatorConfig.getType();
      if (type != null) {
        if (type instanceof Class) {
          typeName = ((Class) type).getName();
        } else {
          typeName = type.toString();
        }
      }
    }

    if (hasDiscriminatorConfig && discriminatorConfig.getFormula() != null) {
      d.setTypeName(typeName);
      Formula formula = new Formula();
      formula.setFormula(discriminatorConfig.getFormula());
      d.addFormula(formula);
    } else {
      simpleValueColumnBinder.bindSimpleValue(d, typeName, JPA_DEFAULT_DISCRIMINATOR_TYPE, false);

      ColumnConfig cc = !hasDiscriminatorConfig ? null : discriminatorConfig.getColumn();
      if (cc != null) {
        Column c = (Column) d.getColumns().iterator().next();
        if (cc.getName() != null) {
          c.setName(cc.getName());
        }
        columnConfigToColumnBinder.bindColumnConfigToColumn(c, cc, null);
      }
    }
  }
}
