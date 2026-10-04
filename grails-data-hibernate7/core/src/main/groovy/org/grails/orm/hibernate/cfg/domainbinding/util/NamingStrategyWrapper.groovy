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
import org.hibernate.boot.model.naming.Identifier
import org.hibernate.boot.model.naming.PhysicalNamingStrategy
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment

import org.grails.datastore.mapping.reflect.NameUtils
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.FOREIGN_KEY_SUFFIX
import static org.hibernate.boot.model.naming.Identifier.toIdentifier

/**
 * A wrapper for the Hibernate 6 PhysicalNamingStrategy to adapt it for use within the Grails
 * binding process, using a functional style.
 */
@CompileStatic
class NamingStrategyWrapper implements PersistentEntityNamingStrategy {

    private final PhysicalNamingStrategy namingStrategy
    private final JdbcEnvironment jdbcEnvironment

    NamingStrategyWrapper(PhysicalNamingStrategy namingStrategy, JdbcEnvironment jdbcEnvironment) {
        if (namingStrategy == null) {
            throw new IllegalArgumentException('PhysicalNamingStrategy argument cannot be null')
        }
        if (jdbcEnvironment == null) {
            throw new IllegalArgumentException('JdbcEnvironment argument cannot be null')
        }
        this.namingStrategy = namingStrategy
        this.jdbcEnvironment = jdbcEnvironment
    }

    JdbcEnvironment getJdbcEnvironment() {
        return jdbcEnvironment
    }

    @Override
    String resolveColumnName(String logicalName) {
        if (logicalName == null) {
            return logicalName
        }
        // Safely handle a null return from the strategy.
        Identifier identifier = namingStrategy.toPhysicalColumnName(
                toIdentifier(logicalName.replace('.' as char, '_' as char)), jdbcEnvironment)
        // Per Hibernate contract, if the strategy returns null, use the original logical name.
        return identifier != null ? identifier.text : logicalName
    }

    @Override
    String resolveTableName(String logicalName) {
        if (logicalName == null) {
            return logicalName
        }
        // Safely handle a null return from the strategy.
        Identifier identifier = namingStrategy.toPhysicalTableName(
                toIdentifier(logicalName.replace('.' as char, '_' as char)), jdbcEnvironment)
        // Per Hibernate contract, if the strategy returns null, use the original logical name.
        return identifier != null ? identifier.text : logicalName
    }

    @Override
    String resolveForeignKeyForPropertyDomainClass(HibernatePersistentProperty property) {
        if (property == null) {
            return null
        }
        GrailsHibernatePersistentEntity owner = property.hibernateOwner
        if (owner == null) {
            return null
        }
        Class<?> javaClass = owner.javaClass
        if (javaClass == null) {
            return null
        }
        String decapitalized = NameUtils.decapitalize(javaClass.simpleName)
        if (decapitalized == null) {
            return null
        }
        String columnName = resolveColumnName(decapitalized)
        if (columnName == null || columnName.isBlank()) {
            return null
        }
        return columnName + FOREIGN_KEY_SUFFIX
    }

    @Override
    String resolveTableName(GrailsHibernatePersistentEntity entity) {
        return resolveTableName(entity.javaClass.simpleName)
    }

}
