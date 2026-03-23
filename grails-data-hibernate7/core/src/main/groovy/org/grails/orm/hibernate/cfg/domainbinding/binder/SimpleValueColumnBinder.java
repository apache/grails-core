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

import org.hibernate.MappingException;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Table;

public class SimpleValueColumnBinder {

    /** Public constructor. */
    public SimpleValueColumnBinder() {}

    /**
     * Creates a {@link BasicValue}, binds it, and returns it.
     *
     * @param metadataBuildingContext The metadata building context
     * @param table The table the value belongs to
     * @param type The type of the property
     * @param columnName The column name
     * @param nullable Whether it is nullable
     */
    public BasicValue bindSimpleValue(
            MetadataBuildingContext metadataBuildingContext,
            Table table,
            String type,
            String columnName,
            boolean nullable) {
        BasicValue basicValue = new BasicValue(metadataBuildingContext, table);
        bindSimpleValue(basicValue, type, columnName, nullable);
        return basicValue;
    }

    /**
     * Binds a value for the specified parameters to the meta model.
     *
     * @param simpleValue The simple value instance
     * @param type The type of the property
     * @param columnName The property name
     * @param nullable Whether it is nullable
     */
    public void bindSimpleValue(SimpleValue simpleValue, String type, String columnName, boolean nullable) {
        Optional.ofNullable(simpleValue.getTable())
                .ifPresentOrElse(
                        table -> {
                            var column = new Column();
                            column.setNullable(nullable);
                            column.setValue(simpleValue);
                            column.setName(columnName);
                            table.addColumn(column);
                            simpleValue.addColumn(column);
                            simpleValue.setTypeName(type);
                        },
                        () -> {
                            throw new MappingException("SimpleValue must have a table");
                        });
    }
}
