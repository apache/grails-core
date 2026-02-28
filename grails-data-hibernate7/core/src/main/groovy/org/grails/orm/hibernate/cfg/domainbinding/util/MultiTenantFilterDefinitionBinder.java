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
import java.util.Collections;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.engine.spi.FilterDefinition;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Property;
import org.hibernate.metamodel.mapping.JdbcMapping;

/**
 * Utility class for binding multi-tenant filter definitions to the Hibernate meta model.
 *
 * @since 7.0
 */
public class MultiTenantFilterDefinitionBinder {

  /**
   * Ensures that a global filter definition exists for the given filter name.
   *
   * @param mappings The in-flight metadata collector
   * @param filterName The name of the filter
   * @param property The property to get the type from
   */
  public void ensureGlobalFilterDefinition(
      @Nonnull InFlightMetadataCollector mappings,
      @Nonnull String filterName,
      @Nonnull Property property) {
    if (mappings.getFilterDefinition(filterName) == null
        && property.getValue() instanceof BasicValue basicValue) {
      JdbcMapping jdbcMapping = basicValue.resolve().getJdbcMapping();
      mappings.addFilterDefinition(
          new FilterDefinition(
              filterName,
              null, // No default condition; let classes specify their own
              Collections.singletonMap(filterName, jdbcMapping)));
    }
  }
}
