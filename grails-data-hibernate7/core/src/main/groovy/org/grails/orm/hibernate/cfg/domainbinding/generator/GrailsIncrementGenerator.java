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
package org.grails.orm.hibernate.cfg.domainbinding.generator;

import java.util.Properties;
import org.grails.orm.hibernate.cfg.Identity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.id.IncrementGenerator;

/**
 * Custom increment ID generator for Grails that extends Hibernate's IncrementGenerator.
 *
 * <p>⚠️ WARNING: This class contains REFLECTION-BASED HACKS and uses LEGACY HIBERNATE METHODS that
 * will be removed in Hibernate 8. This is a workaround for Hibernate 7 limitations.
 *
 * <p>ISSUES WITH HIBERNATE 7: - IncrementGenerator.configure() does not properly initialize all
 * internal state - physicalTableNames, sql, and previousValueHolder fields are not set correctly -
 * The initialize() method may not work as expected with the default configuration - These issues
 * appear to be fixed in later Hibernate versions but present in early Hibernate 7
 *
 * <p>MIGRATION PATH FOR HIBERNATE 8: 1. Check if IncrementGenerator.configure() properly
 * initializes all fields 2. Remove all reflection-based field access (returnClassField,
 * columnField, etc.) 3. Remove the manual initialize() method calls 4. Remove the
 * previousValueHolder initialization hack 5. Simplify the generate() method to rely on parent class
 * behavior 6. Run full integration tests to verify increment ID generation works correctly
 *
 * @see org.hibernate.id.IncrementGenerator
 */
public class GrailsIncrementGenerator extends IncrementGenerator {

  private boolean initialized = false;
  private String resolvedTableName;
  private String resolvedColumnName;
  private Class<?> resolvedReturnClass;

  public GrailsIncrementGenerator(
      GeneratorCreationContext context,
      Identity mappedId,
      GrailsHibernatePersistentEntity domainClass) {
    Properties params = new Properties();
    if (mappedId != null && mappedId.getProperties() != null) {
      params.putAll(mappedId.getProperties());
    }

    // Fix the blank FROM clause: Resolve table name
    org.grails.orm.hibernate.cfg.Mapping mapping = domainClass.getMappedForm();
    String tableName = (mapping != null) ? mapping.getTableName() : null;
    if (tableName == null || tableName.isEmpty()) {
      tableName =
          domainClass
              .getJavaClass()
              .getSimpleName()
              .replaceAll("([a-z])([A-Z])", "$1_$2")
              .toLowerCase();
    }
    this.resolvedTableName = tableName;
    params.put("table", tableName);

    if (mapping != null && mapping.getTable() != null) {
      if (mapping.getTable().getCatalog() != null) {
        params.put("catalog", mapping.getTable().getCatalog());
      }
      if (mapping.getTable().getSchema() != null) {
        params.put("schema", mapping.getTable().getSchema());
      }
    }

    // Resolve column name
    String columnName = context.getProperty().getName();
    if (columnName == null || columnName.contains(".")) {
      columnName =
          (mappedId != null && mappedId.getName() != null && !mappedId.getName().contains("."))
              ? mappedId.getName()
              : "id";
    }
    this.resolvedColumnName = columnName;
    this.resolvedReturnClass = context.getType().getReturnedClass();
    params.put("column", columnName);

    // Initialize the internal Hibernate state
    this.configure(context.getType(), params, context.getServiceRegistry());

    // ⚠️ HACK: Manually ensure fields are set via reflection
    // Reason: Hibernate 7's IncrementGenerator.configure() does not properly initialize
    // returnClass and column fields. This will be fixed in Hibernate 8.
    // TODO: Remove this reflection hack when upgrading to Hibernate 8
    try {
      Class<?> clazz = org.hibernate.id.IncrementGenerator.class;
      java.lang.reflect.Field returnClassField = clazz.getDeclaredField("returnClass");
      returnClassField.setAccessible(true);
      returnClassField.set(this, resolvedReturnClass);

      java.lang.reflect.Field columnField = clazz.getDeclaredField("column");
      columnField.setAccessible(true);
      columnField.set(this, resolvedColumnName);
    } catch (Exception e) {
      // Silently ignore - fields may be set through other means or parent initialize
    }

    if (context.getDatabase() != null) {
      this.registerExportables(context.getDatabase());
    }
  }

  @Override
  public Object generate(SharedSessionContractImplementor session, Object object) {
    if (!initialized) {
      synchronized (this) {
        if (!initialized) {
          SqlStringGenerationContext context = session.getFactory().getSqlStringGenerationContext();

          try {
            Class<?> clazz = org.hibernate.id.IncrementGenerator.class;

            // ⚠️ HACK: Ensure returnClass is set via reflection
            // Reason: Legacy Hibernate 7 workaround. Use proper API in Hibernate 8.
            java.lang.reflect.Field returnClassField = clazz.getDeclaredField("returnClass");
            returnClassField.setAccessible(true);
            if (returnClassField.get(this) == null) {
              returnClassField.set(this, resolvedReturnClass);
            }

            // ⚠️ HACK: Ensure column is set via reflection
            // Reason: Legacy Hibernate 7 workaround. Use proper API in Hibernate 8.
            java.lang.reflect.Field columnField = clazz.getDeclaredField("column");
            columnField.setAccessible(true);
            if (columnField.get(this) == null) {
              columnField.set(this, resolvedColumnName);
            }

            // ⚠️ HACK: Set physicalTableNames BEFORE calling initialize
            // Reason: IncrementGenerator.initialize() in Hibernate 7 doesn't properly initialize
            // this field from the table name. This hack provides it directly.
            // TODO: Remove when Hibernate 8 is released and tested
            java.lang.reflect.Field field = clazz.getDeclaredField("physicalTableNames");
            field.setAccessible(true);
            if (field.get(this) == null) {
              JdbcEnvironment jdbcEnvironment = session.getJdbcServices().getJdbcEnvironment();
              field.set(
                  this,
                  java.util.List.of(
                      jdbcEnvironment
                          .getQualifiedObjectNameFormatter()
                          .format(
                              new org.hibernate.boot.model.relational.QualifiedTableName(
                                  null,
                                  null,
                                  jdbcEnvironment
                                      .getIdentifierHelper()
                                      .toIdentifier(resolvedTableName)),
                              context.getDialect())));
            }

            try {
              // Call parent initialize() - may fail or partially initialize in Hibernate 7
              this.initialize(context);
            } catch (Exception e) {
              // Silently ignore - we'll manually set required fields below
            }

            // ⚠️ HACK: Ensure sql is set via reflection
            // Reason: IncrementGenerator.initialize() may not set the SQL query properly.
            // This hack ensures we have a valid SQL query for fetching the max ID.
            // TODO: Remove in Hibernate 8 and verify initialize() sets this correctly
            java.lang.reflect.Field sqlField = clazz.getDeclaredField("sql");
            sqlField.setAccessible(true);
            if (sqlField.get(this) == null) {
              sqlField.set(
                  this, "select max(" + resolvedColumnName + ") from " + resolvedTableName);
            }

            // ⚠️ HACK: Initialize previousValueHolder via reflection
            // Reason: This is a legacy Hibernate 7 workaround to ensure the holder is initialized.
            // The initialize() method should handle this, but doesn't in all cases.
            // TODO: Remove this entire block in Hibernate 8 and rely on parent class
            java.lang.reflect.Field holderField = clazz.getDeclaredField("previousValueHolder");
            holderField.setAccessible(true);
            if (holderField.get(this) == null) {
              java.lang.reflect.Method initMethod =
                  clazz.getDeclaredMethod(
                      "initializePreviousValueHolder", SharedSessionContractImplementor.class);
              initMethod.setAccessible(true);
              initMethod.invoke(this, session);
            }
          } catch (Exception e) {
            // Silently ignore - parent generate() may still work with partial initialization
          }
          initialized = true;
        }
      }
    }
    return super.generate(session, object);
  }
}
