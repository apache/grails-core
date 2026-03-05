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

import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.JoinedSubclass;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher;

/**
 * Binds a joined sub-class mapping using table-per-subclass
 *
 * @since 7.0
 */
public class JoinedSubClassBinder {

    private static final Logger LOG = LoggerFactory.getLogger(JoinedSubClassBinder.class);
    private static final String EMPTY_PATH = "";

  private final MetadataBuildingContext metadataBuildingContext;
  private final PersistentEntityNamingStrategy namingStrategy;
  private final SimpleValueColumnBinder simpleValueColumnBinder;
  private final ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher;
  private final ClassBinder classBinder;
  private final InFlightMetadataCollector mappings;

  public JoinedSubClassBinder(
      MetadataBuildingContext metadataBuildingContext,
      PersistentEntityNamingStrategy namingStrategy,
      SimpleValueColumnBinder simpleValueColumnBinder,
      ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher,
      ClassBinder classBinder,
      InFlightMetadataCollector mappings) {
    this.metadataBuildingContext = metadataBuildingContext;
    this.namingStrategy = namingStrategy;
    this.simpleValueColumnBinder = simpleValueColumnBinder;
    this.columnNameForPropertyAndPathFetcher = columnNameForPropertyAndPathFetcher;
    this.classBinder = classBinder;
    this.mappings = mappings;
  }

  /**
   * Binds a joined sub-class mapping using table-per-subclass
   *
   * @param sub The Grails sub class
   * @param joinedSubclass The Hibernate Subclass object
   */
  public void bindJoinedSubClass(
      GrailsHibernatePersistentEntity sub,
      JoinedSubclass joinedSubclass) {
    classBinder.bindClass(sub, joinedSubclass);

    String schemaName = sub.getSchema(mappings);
    String catalogName = sub.getCatalog(mappings);

    Table mytable =
        mappings.addTable(
            schemaName,
            catalogName,
            getJoinedSubClassTableName(sub, joinedSubclass),
            null,
            false,
            metadataBuildingContext);

    joinedSubclass.setTable(mytable);
    if (LOG.isInfoEnabled()) {
      LOG.info(
          "Mapping joined-subclass: "
              + joinedSubclass.getEntityName()
              + " -> "
              + joinedSubclass.getTable().getName());
    }

    /**
     * Binds a joined sub-class mapping using table-per-subclass
     *
     * @param sub The Grails sub class
     * @param parent The Hibernate Parent PersistentClass object
     * @return The created JoinedSubclass
     */
    public JoinedSubclass bindJoinedSubClass(GrailsHibernatePersistentEntity sub, PersistentClass parent) {
        JoinedSubclass joinedSubclass = new JoinedSubclass(parent, metadataBuildingContext);
        classBinder.bindClass(sub, joinedSubclass);

        String schemaName = sub.getSchema(mappings);
        String catalogName = sub.getCatalog(mappings);

  private String getJoinedSubClassTableName(
      GrailsHibernatePersistentEntity sub,
      PersistentClass model) {

        joinedSubclass.setTable(mytable);
        if (LOG.isInfoEnabled()) {
            LOG.info("Mapping joined-subclass: " +
                    joinedSubclass.getEntityName() +
                    " -> " +
                    joinedSubclass.getTable().getName());
        }

        SimpleValue key = new DependantValue(metadataBuildingContext, mytable, joinedSubclass.getIdentifier());
        joinedSubclass.setKey(key);
        var identifier = sub.getIdentity();
        String columnName =
                columnNameForPropertyAndPathFetcher.getColumnNameForPropertyAndPath(identifier, EMPTY_PATH, null);
        simpleValueColumnBinder.bindSimpleValue(key, identifier.getType().getName(), columnName, false);

    mappings.addTableNameBinding(
        schemaName, catalogName, logicalTableName, physicalTableName, null);
    return physicalTableName;
  }
}
