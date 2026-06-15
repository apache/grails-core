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

import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity
import org.hibernate.boot.model.relational.Database
import org.hibernate.boot.model.relational.SqlStringGenerationContext
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.mapping.Property
import org.hibernate.id.enhanced.TableGenerator
import spock.lang.Specification

class GrailsTableGeneratorSpec extends HibernateGormDatastoreSpec {

    static class TestGrailsTableGenerator extends GrailsTableGenerator {
        Properties capturedProps
        Database capturedDatabase
        SqlStringGenerationContext capturedSqlContext

        TestGrailsTableGenerator(GeneratorCreationContext context, HibernateSimpleIdentity mappedId, JdbcEnvironment jdbcEnvironment) {
            super(context, mappedId, jdbcEnvironment)
        }

        @Override
        void configure(GeneratorCreationContext context, Properties params) {
            this.capturedProps = params
        }

        @Override
        void registerExportables(Database database) {
            this.capturedDatabase = database
        }

        @Override
        void initialize(SqlStringGenerationContext context) {
            this.capturedSqlContext = context
        }
    }

    def "test constructor logic"() {
        given:
        def binder = getGrailsDomainBinder()
        def context = Mock(GeneratorCreationContext)
        def property = new Property()
        property.setName("id")
        def database = binder.getMetadataBuildingContext().getMetadataCollector().getDatabase()
        def jdbcEnvironment = binder.getJdbcEnvironment()
        def mappedId = Mock(HibernateSimpleIdentity)
        def props = new Properties()

        context.getProperty() >> property
        context.getDatabase() >> database
        mappedId.getProperties() >> props
        mappedId.getName() >> "myEntity"

        when:
        def generator = new TestGrailsTableGenerator(context, mappedId, jdbcEnvironment)

        then:
        generator.capturedProps.getProperty(TableGenerator.SEGMENT_VALUE_PARAM) == "myEntity.id"
        generator.capturedProps.getProperty(TableGenerator.INCREMENT_PARAM) == "50"
        generator.capturedProps.getProperty(TableGenerator.OPT_PARAM) == "pooled-lo"
        generator.capturedDatabase == database
        generator.capturedSqlContext != null
    }

    def "test constructor with null mappedId"() {
        given:
        def binder = getGrailsDomainBinder()
        def context = Mock(GeneratorCreationContext)
        def property = new Property()
        property.setName("id")
        def database = binder.getMetadataBuildingContext().getMetadataCollector().getDatabase()
        def jdbcEnvironment = binder.getJdbcEnvironment()

        context.getProperty() >> property
        context.getDatabase() >> database

        when:
        def generator = new TestGrailsTableGenerator(context, null, jdbcEnvironment)

        then:
        generator.capturedProps.getProperty(TableGenerator.SEGMENT_VALUE_PARAM) == "default.id"
    }

    def "test constructor with existing parameters"() {
        given:
        def binder = getGrailsDomainBinder()
        def context = Mock(GeneratorCreationContext)
        def property = new Property()
        property.setName("id")
        def database = binder.getMetadataBuildingContext().getMetadataCollector().getDatabase()
        def jdbcEnvironment = binder.getJdbcEnvironment()
        def mappedId = Mock(HibernateSimpleIdentity)
        def props = new Properties()
        props.put(TableGenerator.SEGMENT_VALUE_PARAM, "custom_segment")
        props.put(TableGenerator.INCREMENT_PARAM, "100")
        props.put(TableGenerator.OPT_PARAM, "none")

        context.getProperty() >> property
        context.getDatabase() >> database
        mappedId.getProperties() >> props

        when:
        def generator = new TestGrailsTableGenerator(context, mappedId, jdbcEnvironment)

        then:
        generator.capturedProps.getProperty(TableGenerator.SEGMENT_VALUE_PARAM) == "custom_segment"
        generator.capturedProps.getProperty(TableGenerator.INCREMENT_PARAM) == "100"
        generator.capturedProps.getProperty(TableGenerator.OPT_PARAM) == "none"
    }
}
