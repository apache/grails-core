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
import java.util.List;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;

import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class CreateKeyForProps {

    private final ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher;
    private final UniqueKeyForColumnsCreator uniqueKeyForColumnsCreator;
    private final InFlightMetadataCollector metadataCollector;

    public CreateKeyForProps(ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher) {
        this(columnNameForPropertyAndPathFetcher, new UniqueKeyForColumnsCreator(), null);
    }

    public CreateKeyForProps(
            ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher,
            InFlightMetadataCollector metadataCollector) {
        this(columnNameForPropertyAndPathFetcher, new UniqueKeyForColumnsCreator(), metadataCollector);
    }

    protected CreateKeyForProps(
            ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher,
            UniqueKeyForColumnsCreator uniqueKeyForColumnsCreator) {
        this(columnNameForPropertyAndPathFetcher, uniqueKeyForColumnsCreator, null);
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
            createKeyWhenReady(() -> createKeyForGroup(grailsProp, path, table,
                    List.of(new Column(columnName)), mappedForm));
        }
    }

    /**
     * Creates the unique key of a property that spans several columns, such as one mapped with a composite user
     * type, when the property is unique: one key across all of its columns and, when it is unique within a group,
     * the columns of the other properties of the group. None of the columns is unique on its own, since two
     * different values of the property can share the value of one of them, so a property that is unique on its
     * own needs the key as well. The key holds the bound columns themselves, so it keeps their quoting and case.
     *
     * @param grailsProp the property
     * @param path the path of the property within its entity
     * @param table the table that holds the columns
     * @param columns the bound columns of the property
     */
    public void createKeyForColumns(
            HibernatePersistentProperty grailsProp, String path, Table table, List<Column> columns) {
        PropertyConfig mappedForm = grailsProp.getMappedForm();
        if (!mappedForm.isUnique() || columns.isEmpty()) {
            return;
        }
        createKeyWhenReady(() -> createKeyForGroup(grailsProp, path, table, columns, mappedForm));
    }

    private void createKeyWhenReady(Runnable createKey) {
        if (metadataCollector == null || metadataCollector.isInSecondPass()) {
            createKey.run();
        } else {
            metadataCollector.addSecondPass(persistentClasses -> createKey.run());
        }
    }

    private void createKeyForGroup(
            HibernatePersistentProperty grailsProp, String path, Table table, List<Column> columns,
            PropertyConfig mappedForm) {
        List<Column> keyList = new ArrayList<>(columns);
        if (mappedForm.isUniqueWithinGroup()) {
            addGroupColumns(grailsProp, mappedForm, path, keyList);
        }
        uniqueKeyForColumnsCreator.createUniqueKeyForColumns(table, keyList);
    }

    /**
     * Adds the columns of the other properties of the uniqueness group of the property. A property of the group
     * that spans several columns, such as one mapped with a composite user type, contributes all of them.
     */
    private void addGroupColumns(
            HibernatePersistentProperty grailsProp, PropertyConfig mappedForm, String path, List<Column> keyList) {
        GrailsHibernatePersistentEntity owner = grailsProp.getHibernateOwner();
        for (String propertyName : mappedForm.getUniquenessGroup()) {
            HibernatePersistentProperty otherProp = owner.getHibernatePropertyByName(propertyName);
            if (otherProp == null) {
                throw new MappingException(
                        owner.getJavaClass().getName() + " references an unknown property " + propertyName);
            }
            for (String otherColumnName :
                    columnNameForPropertyAndPathFetcher.getColumnNamesForPropertyAndPath(otherProp, path)) {
                keyList.add(new Column(otherColumnName));
            }
        }
    }
}
