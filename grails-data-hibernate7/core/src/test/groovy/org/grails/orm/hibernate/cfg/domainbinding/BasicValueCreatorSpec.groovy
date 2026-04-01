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

package org.grails.orm.hibernate.cfg.domainbinding

import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
import org.grails.orm.hibernate.cfg.Identity
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.generator.Generator
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import spock.lang.Unroll

import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceWrapper
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceGeneratorEnum
import org.grails.orm.hibernate.cfg.domainbinding.util.BasicValueCreator

class BasicValueCreatorSpec extends HibernateGormDatastoreSpec {

    MetadataBuildingContext metadataBuildingContext
    BasicValueCreator creator
    BasicValue basicValue
    RootClass entity
    Table table
    GrailsSequenceWrapper grailsSequenceWrapper
    JdbcEnvironment jdbcEnvironment
    PersistentEntityNamingStrategy namingStrategy

    def setup() {
        metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        jdbcEnvironment = Mock(JdbcEnvironment)
        namingStrategy = Mock(PersistentEntityNamingStrategy)
        grailsSequenceWrapper = Mock(GrailsSequenceWrapper)
        entity = new RootClass(metadataBuildingContext)
        table = new Table("test_table")
        entity.setTable(table)
        // Use a real BasicValue to test that the generator creator lambda is correctly set and executable
        basicValue = new BasicValue(metadataBuildingContext, table)
        creator = new BasicValueCreator(metadataBuildingContext, jdbcEnvironment, namingStrategy, grailsSequenceWrapper)
    }

    @Unroll
    def "should create BasicValue using factory for #generatorName (useSequence: #useSequence)"() {
        given:
        Identity mappedId = new Identity()
        mappedId.setGenerator(generatorName)
        def mapping = Mock(org.grails.orm.hibernate.cfg.Mapping)
        mapping.isTablePerConcreteClass() >> useSequence
        def domainClass = Mock(HibernatePersistentEntity)
        domainClass.getHibernateMappedForm() >> mapping
        def mockGenerator = Mock(Generator)
        def context = Mock(GeneratorCreationContext)

        when:
        BasicValue id = creator.bindBasicValue(table, mappedId, domainClass)
        def generatorCreator = id.getCustomIdGeneratorCreator()
        Generator generator = generatorCreator.createGenerator(context)

        then:
        1 * grailsSequenceWrapper.getGenerator(generatorName, _, mappedId, domainClass, jdbcEnvironment, namingStrategy) >> mockGenerator
        generator == mockGenerator

        where:
        generatorName                                            | useSequence
        GrailsSequenceGeneratorEnum.IDENTITY.toString()          | false
        GrailsSequenceGeneratorEnum.SEQUENCE.toString()          | true
        GrailsSequenceGeneratorEnum.SEQUENCE_IDENTITY.toString() | true
        GrailsSequenceGeneratorEnum.INCREMENT.toString()         | false
        GrailsSequenceGeneratorEnum.UUID.toString()              | false
        GrailsSequenceGeneratorEnum.UUID2.toString()             | false
        GrailsSequenceGeneratorEnum.ASSIGNED.toString()          | false
        GrailsSequenceGeneratorEnum.TABLE.toString()             | false
        GrailsSequenceGeneratorEnum.ENHANCED_TABLE.toString()    | false
        GrailsSequenceGeneratorEnum.HILO.toString()              | false
    }

    def "should default to native generator when mappedId is null"() {
        given:
        def mockGenerator = Mock(Generator)
        def domainClass = Mock(HibernatePersistentEntity)
        domainClass.getHibernateMappedForm() >> null
        def context = Mock(GeneratorCreationContext)

        when:
        BasicValue id = creator.bindBasicValue(table, null, domainClass)
        def generatorCreator = id.getCustomIdGeneratorCreator()
        Generator generator = generatorCreator.createGenerator(context)

        then:
        1 * grailsSequenceWrapper.getGenerator(GrailsSequenceGeneratorEnum.NATIVE.toString(), _, null, domainClass, jdbcEnvironment, namingStrategy) >> mockGenerator
        generator == mockGenerator
    }

    def "should default to sequence-identity when mappedId is null and useSequence is true"() {
        given:
        def mockGenerator = Mock(Generator)
        def mapping = Mock(org.grails.orm.hibernate.cfg.Mapping)
        mapping.isTablePerConcreteClass() >> true
        def domainClass = Mock(HibernatePersistentEntity)
        domainClass.getHibernateMappedForm() >> mapping
        def context = Mock(GeneratorCreationContext)

        when:
        BasicValue id = creator.bindBasicValue(table, null, domainClass)
        def generatorCreator = id.getCustomIdGeneratorCreator()
        Generator generator = generatorCreator.createGenerator(context)

        then:
        1 * grailsSequenceWrapper.getGenerator(GrailsSequenceGeneratorEnum.SEQUENCE_IDENTITY.toString(), _, null, domainClass, jdbcEnvironment, namingStrategy) >> mockGenerator
        generator == mockGenerator
    }

    def "should use sequence-identity when generator is native and useSequence is true"() {
        given:
        Identity mappedId = new Identity()
        mappedId.setGenerator(GrailsSequenceGeneratorEnum.NATIVE.toString())
        def mockGenerator = Mock(Generator)
        def mapping = Mock(org.grails.orm.hibernate.cfg.Mapping)
        mapping.isTablePerConcreteClass() >> true
        def domainClass = Mock(HibernatePersistentEntity)
        domainClass.getHibernateMappedForm() >> mapping
        def context = Mock(GeneratorCreationContext)

        when:
        BasicValue id = creator.bindBasicValue(table, mappedId, domainClass)
        def generatorCreator = id.getCustomIdGeneratorCreator()
        Generator generator = generatorCreator.createGenerator(context)

        then:
        1 * grailsSequenceWrapper.getGenerator(GrailsSequenceGeneratorEnum.SEQUENCE_IDENTITY.toString(), _, mappedId, domainClass, jdbcEnvironment, namingStrategy) >> mockGenerator
        generator == mockGenerator
    }

    def "should pass mappedId to factory"() {
        given:
        Identity mappedId = new Identity()
        mappedId.setGenerator("custom")
        def domainClass = Mock(HibernatePersistentEntity)
        domainClass.getHibernateMappedForm() >> null
        def context = Mock(GeneratorCreationContext)

        when:
        BasicValue id = creator.bindBasicValue(table, mappedId, domainClass)
        def generatorCreator = id.getCustomIdGeneratorCreator()
        generatorCreator.createGenerator(context)

        then:
        1 * grailsSequenceWrapper.getGenerator("custom", _, mappedId, domainClass, jdbcEnvironment, namingStrategy) >> Mock(Generator)
    }
}
