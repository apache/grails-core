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
package org.grails.orm.hibernate.cfg.domainbinding.util

import groovy.transform.CompileStatic
import org.hibernate.boot.spi.MetadataBuildingContext

import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty

@CompileStatic
class ColumnNameForPropertyAndPathFetcher {

    private static final String UNDERSCORE = '_'
    private final PersistentEntityNamingStrategy namingStrategy
    private final DefaultColumnNameFetcher defaultColumnNameFetcher
    private final BackticksRemover backticksRemover
    private final CompositeUserTypeColumns compositeUserTypeColumns

    /**
     * Creates a fetcher that instantiates a composite user type through its default constructor when it names
     * the columns of a property mapped with one, as Hibernate does outside a CDI container.
     *
     * @param namingStrategy the naming strategy of the default column names
     * @param defaultColumnNameFetcher the fetcher of the default column name of a property
     * @param backticksRemover the remover of the backticks of a quoted name
     * @see #ColumnNameForPropertyAndPathFetcher(PersistentEntityNamingStrategy, DefaultColumnNameFetcher,
     * BackticksRemover, MetadataBuildingContext)
     */
    ColumnNameForPropertyAndPathFetcher(
            PersistentEntityNamingStrategy namingStrategy,
            DefaultColumnNameFetcher defaultColumnNameFetcher,
            BackticksRemover backticksRemover) {
        this(namingStrategy, defaultColumnNameFetcher, backticksRemover, (MetadataBuildingContext) null)
    }

    /**
     * Creates a fetcher that instantiates a composite user type as the binder of its property does when it names
     * the columns of a property mapped with one: as a managed bean when the building options allow extensions in
     * CDI, through its default constructor otherwise.
     *
     * @param namingStrategy the naming strategy of the default column names
     * @param defaultColumnNameFetcher the fetcher of the default column name of a property
     * @param backticksRemover the remover of the backticks of a quoted name
     * @param metadataBuildingContext the context of the binding, or {@code null} to instantiate every composite
     * user type through its default constructor
     * @since 8.0.1
     */
    ColumnNameForPropertyAndPathFetcher(
            PersistentEntityNamingStrategy namingStrategy,
            DefaultColumnNameFetcher defaultColumnNameFetcher,
            BackticksRemover backticksRemover,
            MetadataBuildingContext metadataBuildingContext) {
        this(namingStrategy, defaultColumnNameFetcher, backticksRemover,
                new CompositeUserTypeColumns(namingStrategy, backticksRemover, metadataBuildingContext))
    }

    protected ColumnNameForPropertyAndPathFetcher(
            PersistentEntityNamingStrategy namingStrategy,
            DefaultColumnNameFetcher defaultColumnNameFetcher,
            BackticksRemover backticksRemover,
            CompositeUserTypeColumns compositeUserTypeColumns) {
        this.namingStrategy = namingStrategy
        this.defaultColumnNameFetcher = defaultColumnNameFetcher
        this.backticksRemover = backticksRemover
        this.compositeUserTypeColumns = compositeUserTypeColumns
    }

    String getColumnNameForPropertyAndPath(
            HibernatePersistentProperty grailsProp, String path, ColumnConfig cc) {
        String columnName = grailsProp.getColumnName(cc)
        if (columnName != null) {
            return columnName
        }
        String suffix = defaultColumnNameFetcher.getDefaultColumnName(grailsProp)
        if (path != null && GrailsHibernateUtil.isNotEmpty(path)) {
            return backticksRemover.apply(namingStrategy.resolveColumnName(path)) +
                    UNDERSCORE +
                    backticksRemover.apply(suffix)
        }
        return suffix
    }

    /**
     * The names of all the columns of the property: the one name {@link #getColumnNameForPropertyAndPath} resolves
     * without a column configuration for a property that has one column, or one name per attribute of the
     * embeddable class for a property mapped with a composite user type, which spans one column per attribute and
     * has no single column name.
     *
     * @param grailsProp the property
     * @param path the path of the property within its entity
     * @return the column names, in column order
     * @since 8.0.1
     */
    List<String> getColumnNamesForPropertyAndPath(HibernatePersistentProperty grailsProp, String path) {
        if (grailsProp.compositeUserType != null) {
            return compositeUserTypeColumns.columnNamesOf(grailsProp, path)
        }
        return Collections.singletonList(getColumnNameForPropertyAndPath(grailsProp, path, null))
    }
}
