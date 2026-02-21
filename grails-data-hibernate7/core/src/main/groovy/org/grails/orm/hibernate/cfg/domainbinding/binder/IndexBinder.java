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

import static java.lang.String.format;
import static java.util.Optional.*;

import jakarta.annotation.Nonnull;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;

public class IndexBinder {
  public void bindIndex(
      @Nonnull String columnName, @Nonnull Column column, ColumnConfig cc, @Nonnull Table table) {
    ofNullable(cc)
        .map(ColumnConfig::getIndex)
        .flatMap(
            indexObj -> {
              if (indexObj instanceof Boolean b) {
                return b ? of(format("%s_%s_idx", table.getName(), columnName)) : empty();
              }
              return of(indexObj.toString());
            })
        .map(def -> def.split(","))
        .ifPresent(
            indices -> {
              for (String index : indices) {
                table.getOrCreateIndex(index.trim()).addColumn(column);
              }
            });
  }
}
