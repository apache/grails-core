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

import org.hibernate.mapping.Column;
import org.jspecify.annotations.NonNull;

import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.PropertyConfig;

/**
 * Applies {@link org.grails.orm.hibernate.cfg.ColumnConfig} settings to a Hibernate {@link org.hibernate.mapping.Column}.
 * Only explicitly configured values (i.e. not {@code -1}) are applied — dialect-specific precision
 * defaults for numeric types are handled by {@link NumericColumnConstraintsBinder}.
 *
 * @since 8.0
 */
public class ColumnConfigToColumnBinder {

    public void bindColumnConfigToColumn(@NonNull Column column, ColumnConfig columnConfig, PropertyConfig mappedForm) {
        Optional.ofNullable(columnConfig).ifPresent(config -> {
            Optional.of(config.getLength()).filter(l -> l != -1).ifPresent(column::setLength);
            Optional.of(config.getPrecision()).filter(p -> p != -1).ifPresent(column::setPrecision);
            Optional.of(config.getScale()).filter(s -> s != -1).ifPresent(column::setScale);
            Optional.ofNullable(config.getSqlType()).filter(s -> !s.isEmpty()).ifPresent(column::setSqlType);
            Optional.ofNullable(mappedForm)
                    .filter(mf -> !mf.isUniqueWithinGroup())
                    .ifPresent(mf -> column.setUnique(config.isUnique()));
        });
    }
}
