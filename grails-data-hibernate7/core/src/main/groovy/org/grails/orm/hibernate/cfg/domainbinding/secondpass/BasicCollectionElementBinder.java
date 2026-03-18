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

import java.util.Optional;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Column;

import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder;
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher;

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.UNDERSCORE;

/** Binds the element value for a basic (scalar or enum) collection. */
public class BasicCollectionElementBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final PersistentEntityNamingStrategy namingStrategy;
    private final EnumTypeBinder enumTypeBinder;
    private final SimpleValueColumnBinder simpleValueColumnBinder;
    private final SimpleValueColumnFetcher simpleValueColumnFetcher;
    private final ColumnConfigToColumnBinder columnConfigToColumnBinder;

    /** Creates a new {@link BasicCollectionElementBinder} instance. */
    public BasicCollectionElementBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            EnumTypeBinder enumTypeBinder,
            SimpleValueColumnBinder simpleValueColumnBinder,
            SimpleValueColumnFetcher simpleValueColumnFetcher,
            ColumnConfigToColumnBinder columnConfigToColumnBinder) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.namingStrategy = namingStrategy;
        this.enumTypeBinder = enumTypeBinder;
        this.simpleValueColumnBinder = simpleValueColumnBinder;
        this.simpleValueColumnFetcher = simpleValueColumnFetcher;
        this.columnConfigToColumnBinder = columnConfigToColumnBinder;
    }

    /** Creates and binds a {@link BasicValue} element for the given basic collection property. */
    public BasicValue bind(HibernateToManyProperty property) {
        String columnName = property.joinTableColumName(namingStrategy);
        if (property.isEnum()) {
            return enumTypeBinder.bindEnumTypeForColumn(property, columnName);
        } else {
            final Class<?> referencedType = property.getComponentType();
            String typeName = property.getTypeName(referencedType);
            Collection collection = property.getCollection();
            BasicValue element = simpleValueColumnBinder.bindSimpleValue(
                    metadataBuildingContext, collection.getCollectionTable(), typeName, columnName, true);
            property.getColumnConfigOptional().ifPresent(columnConfig -> {
                Column column = simpleValueColumnFetcher.getColumnForSimpleValue(element);
                final PropertyConfig mappedForm = property.getMappedForm();
                columnConfigToColumnBinder.bindColumnConfigToColumn(column, columnConfig, mappedForm);
            });
            return element;
        }
    }
}
