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
package org.grails.orm.hibernate.cfg.domainbinding.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;

import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class CreateKeyForProps {

    private final ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher;
    private final UniqueKeyForColumnsCreator uniqueKeyForColumnsCreator;
    private final InFlightMetadataCollector metadataCollector;

    public CreateKeyForProps(
            ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher,
            InFlightMetadataCollector metadataCollector) {
        this(columnNameForPropertyAndPathFetcher, new UniqueKeyForColumnsCreator(), metadataCollector);
    }

    protected CreateKeyForProps(
            ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher,
            UniqueKeyForColumnsCreator uniqueKeyForColumnsCreator,
            InFlightMetadataCollector metadataCollector) {
        this.columnNameForPropertyAndPathFetcher = columnNameForPropertyAndPathFetcher;
        this.uniqueKeyForColumnsCreator = uniqueKeyForColumnsCreator;
        this.metadataCollector = metadataCollector;
    }

    public void createKeyForProps(HibernatePersistentProperty grailsProp, String path, Table table, String columnName) {
        if (HibernateToManyProperty.isBoundAsCollection(grailsProp)) {
            // The column of a bound collection lives in the collection table, which does not hold the columns
            // of the other properties of the group, so a unique group has no key to create there. A to-many
            // mapped with a user type or as serializable is a plain column of the owner table, so it keeps its
            // key. The condition is the one GrailsPropertyBinder routes by.
            return;
        }
        PropertyConfig mappedForm = grailsProp.getMappedForm();

        if (mappedForm.isUnique() && mappedForm.isUniqueWithinGroup()) {
            if (metadataCollector.isInSecondPass()) {
                createKeyForGroup(grailsProp, path, table, columnName, mappedForm);
            } else {
                metadataCollector.addSecondPass(persistentClasses ->
                        createKeyForGroup(grailsProp, path, table, columnName, mappedForm));
            }
        }
    }

    private void createKeyForGroup(
            HibernatePersistentProperty grailsProp, String path, Table table, String columnName, PropertyConfig mappedForm) {
        List<Column> keyList = new ArrayList<>();
        keyList.add(new Column(columnName));
        List<String> propertyNames = mappedForm.getUniquenessGroup();
        GrailsHibernatePersistentEntity owner = grailsProp.getHibernateOwner();
        for (String propertyName : propertyNames) {
            HibernatePersistentProperty otherProp = owner.getHibernatePropertyByName(propertyName);
            if (otherProp == null) {
                throw new MappingException(
                        owner.getJavaClass().getName() + " references an unknown property " + propertyName);
            }
            List<ColumnConfig> columns = otherProp.getHibernateMappedForm().getColumns();
            if (columns.isEmpty()) {
                columns = Collections.singletonList(null);
            }
            for (ColumnConfig column : columns) {
                String otherColumnName =
                        columnNameForPropertyAndPathFetcher.getColumnNameForPropertyAndPath(otherProp, path, column);
                keyList.add(new Column(otherColumnName));
            }
        }

        uniqueKeyForColumnsCreator.createUniqueKeyForColumns(table, keyList);
    }
}
