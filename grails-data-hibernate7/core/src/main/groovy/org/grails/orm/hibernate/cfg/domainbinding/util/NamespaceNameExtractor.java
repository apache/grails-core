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

import jakarta.annotation.Nonnull;
import java.util.Optional;
import java.util.function.Function;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.relational.Database;
import org.hibernate.boot.model.relational.Namespace;
import org.hibernate.boot.spi.InFlightMetadataCollector;

public class NamespaceNameExtractor {

  public static String getCatalogName(@Nonnull InFlightMetadataCollector mappings) {
    return getNamespaceName(mappings, Namespace.Name::getCatalog);
  }

  public static String getSchemaName(@Nonnull InFlightMetadataCollector mappings) {
    return getNamespaceName(mappings, Namespace.Name::getSchema);
  }

  private static String getNamespaceName(
      @Nonnull InFlightMetadataCollector mappings, Function<Namespace.Name, Identifier> function) {
    return Optional.ofNullable(mappings.getDatabase())
        .map(Database::getDefaultNamespace)
        .map(Namespace::getName)
        .map(function)
        .map(Identifier::getCanonicalName)
        .orElse(null);
  }
}
