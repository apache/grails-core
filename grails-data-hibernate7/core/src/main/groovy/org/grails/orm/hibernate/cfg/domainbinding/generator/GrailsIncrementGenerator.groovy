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
package org.grails.orm.hibernate.cfg.domainbinding.generator

import groovy.transform.CompileStatic
import org.hibernate.boot.model.relational.SqlStringGenerationContext
import org.hibernate.boot.model.relational.internal.SqlStringGenerationContextImpl
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.id.IncrementGenerator

import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity

import static org.hibernate.id.PersistentIdentifierGenerator.CATALOG
import static org.hibernate.id.PersistentIdentifierGenerator.SCHEMA

/**
 * Grails-aware increment ID generator. Builds the standard {@link IncrementGenerator} parameters
 * from GORM mapping metadata and delegates entirely to the parent class — no reflection required.
 */
@CompileStatic
class GrailsIncrementGenerator extends IncrementGenerator {

    private static final long serialVersionUID = 1L

    GrailsIncrementGenerator(
            GeneratorCreationContext context,
            HibernateSimpleIdentity mappedId,
            GrailsHibernatePersistentEntity domainClass,
            PersistentEntityNamingStrategy namingStrategy) {

        configure(context, buildParams(context, mappedId, domainClass, namingStrategy))
        initialize(buildSqlContext(context))
    }

    protected Properties buildParams(
            GeneratorCreationContext context,
            HibernateSimpleIdentity mappedId,
            GrailsHibernatePersistentEntity domainClass,
            PersistentEntityNamingStrategy namingStrategy) {

        Properties params = new Properties()
        Properties mappedIdProps = mappedId?.properties
        if (mappedIdProps != null) {
            params.putAll(mappedIdProps)
        }

        params.put(TABLES, domainClass.getTableName(namingStrategy))
        params.put(COLUMN, resolveColumnName(context, mappedId))

        Mapping mappedForm = domainClass.hibernateMappedForm
        org.grails.orm.hibernate.cfg.Table table = mappedForm?.table
        if (table != null) {
            if (table.catalog != null) {
                params.put(CATALOG, table.catalog)
            }
            if (table.schema != null) {
                params.put(SCHEMA, table.schema)
            }
        }

        return params
    }

    protected String resolveColumnName(GeneratorCreationContext context, HibernateSimpleIdentity mappedId) {
        String propertyName = context.property.name
        if (propertyName != null && !propertyName.contains('.')) {
            return propertyName
        }
        String mappedIdName = mappedId?.name
        return (mappedIdName != null && !mappedIdName.contains('.')) ? mappedIdName : 'id'
    }

    protected SqlStringGenerationContext buildSqlContext(GeneratorCreationContext context) {
        def database = context.database
        def physicalName = database.defaultNamespace.physicalName

        return SqlStringGenerationContextImpl.fromExplicit(
                database.jdbcEnvironment,
                database,
                physicalName.catalog()?.canonicalName,
                physicalName.schema()?.canonicalName)
    }

}
