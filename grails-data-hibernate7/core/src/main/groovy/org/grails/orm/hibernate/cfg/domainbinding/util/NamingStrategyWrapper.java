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

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.FOREIGN_KEY_SUFFIX;
import static org.hibernate.boot.model.naming.Identifier.toIdentifier;

import java.util.Optional;
import org.grails.datastore.mapping.reflect.NameUtils;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentProperty;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

/**
 * A wrapper for the Hibernate 6 PhysicalNamingStrategy to adapt it for use within the Grails
 * binding process, using a functional style.
 */
public class NamingStrategyWrapper implements PersistentEntityNamingStrategy {

  private final PhysicalNamingStrategy namingStrategy;
  private final JdbcEnvironment jdbcEnvironment;

  public NamingStrategyWrapper(
      PhysicalNamingStrategy namingStrategy, JdbcEnvironment jdbcEnvironment) {
    if (namingStrategy == null) {
      throw new IllegalArgumentException("PhysicalNamingStrategy argument cannot be null");
    }
    if (jdbcEnvironment == null) {
      throw new IllegalArgumentException("JdbcEnvironment argument cannot be null");
    }
    this.namingStrategy = namingStrategy;
    this.jdbcEnvironment = jdbcEnvironment;
  }

  public JdbcEnvironment getJdbcEnvironment() {
    return jdbcEnvironment;
  }

  @Override
  public String resolveColumnName(String logicalName) {
    return Optional.ofNullable(logicalName)
        .flatMap(
            name ->
                // Safely handle a null return from the strategy by wrapping it in an Optional.
                Optional.ofNullable(
                    namingStrategy.toPhysicalColumnName(
                        toIdentifier(name.replace('.', '_')), jdbcEnvironment)))
        .map(Identifier::getText)
        // Per Hibernate contract, if the strategy returns null, use the original logical name.
        .orElse(logicalName);
  }

  @Override
  public String resolveTableName(String logicalName) {
    return Optional.ofNullable(logicalName)
        .flatMap(
            name ->
                // Safely handle a null return from the strategy.
                Optional.ofNullable(
                    namingStrategy.toPhysicalTableName(
                        toIdentifier(name.replace('.', '_')), jdbcEnvironment)))
        .map(Identifier::getText)
        // Per Hibernate contract, if the strategy returns null, use the original logical name.
        .orElse(logicalName);
  }

  @Override
  public String resolveForeignKeyForPropertyDomainClass(
      GrailsHibernatePersistentProperty property) {
    return Optional.ofNullable(property)
        .map(GrailsHibernatePersistentProperty::getHibernateOwner)
        .map(GrailsHibernatePersistentEntity::getJavaClass)
        .map(Class::getSimpleName)
        .map(NameUtils::decapitalize)
        .map(this::resolveColumnName)
        .filter(name -> !name.isBlank())
        .map(columnName -> columnName + FOREIGN_KEY_SUFFIX)
        .orElse(null);
  }

  @Override
  public String resolveTableName(GrailsHibernatePersistentEntity entity) {
    return resolveTableName(entity.getJavaClass().getSimpleName());
  }
}
