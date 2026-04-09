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

import jakarta.annotation.Nonnull;

import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.OracleDialect;
import org.hibernate.mapping.Column;

import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.PropertyConfig;

public class ColumnConfigToColumnBinder {

    private final Dialect dialect;

    public ColumnConfigToColumnBinder() {
        this(new H2Dialect());
    }

    public ColumnConfigToColumnBinder(Dialect dialect) {
        this.dialect = dialect;
    }

    public void bindColumnConfigToColumn(@Nonnull Column column, ColumnConfig columnConfig, PropertyConfig mappedForm) {
        Optional.ofNullable(columnConfig).ifPresent(config -> {
            Optional.of(config.getLength()).filter(l -> l != -1).ifPresent(column::setLength);

            int precision = getPrecision(config);

            column.setPrecision(precision);

            Optional.of(config.getScale()).filter(s -> s != -1).ifPresent(column::setScale);

            Optional.ofNullable(config.getSqlType()).filter(s -> !s.isEmpty()).ifPresent(column::setSqlType);

            Optional.ofNullable(mappedForm)
                    .filter(mf -> !mf.isUniqueWithinGroup())
                    .ifPresent(mf -> column.setUnique(config.isUnique()));
        });
    }

    private int getPrecision(ColumnConfig config) {
        int precision = config.getPrecision();
        if (precision == -1) {
            // Apply dialect-specific defaults for Double/Float types if precision is not set
            if (dialect instanceof OracleDialect) {
                // Oracle defaults to 126 bits or 64 depending on version/type
                precision = 126;
            } else {
                // Most other databases (H2, PostgreSQL, MySQL) use 53 bits for Double
                // Hibernate 7 interprets this precision as decimal digits for some dialects
                // and converts to bits. 15 decimal digits maps to ~50-53 bits.
                precision = 15;
            }
        }
        return precision;
    }
}
