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

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy

import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.generator.Assigned
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.id.enhanced.SequenceStyleGenerator
import org.hibernate.id.uuid.UuidGenerator
import org.hibernate.mapping.Column
import org.hibernate.mapping.Value
import org.hibernate.mapping.Property
import org.hibernate.type.Type
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Unroll

@Testcontainers
@Requires({ isDockerAvailable() })
class GrailsSequenceGeneratorEnumSpec extends HibernateGormDatastoreSpec {

    @Shared PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")

    @Override
    void setupSpec() {
        manager.grailsConfig = [
            'dataSource.url'              : postgres.jdbcUrl,
            'dataSource.driverClassName'  : postgres.driverClassName,
            'dataSource.username'         : postgres.username,
            'dataSource.password'         : postgres.password,
            'dataSource.dbCreate'         : 'create-drop',
            'hibernate.dialect'           : 'org.hibernate.dialect.PostgreSQLDialect',
            'hibernate.hbm2ddl.auto'      : 'create',
        ]
        manager.addAllDomainClasses([GrailsSequenceGeneratorEnumSpecEntity])
    }

    /**
     * Build a GeneratorCreationContext stub backed by the real ServiceRegistry and Database
     * from the running datastore so that sequence, table and other generators that
     * call serviceRegistry.requireService(...) work without NPE.
     * Column is sealed so we use a real instance; Value/Property are interfaces so we mock them.
     */
    private GeneratorCreationContext buildContext() {
        // Use the real PostgreSQL database from the running datastore so DDL type
        // registries (needed by TableGenerator.registerExportables) are correct.
        def db = datastore.metadata.database
        def table = new org.hibernate.mapping.Table("grails_sequence_generator_enum_spec_entity")
        def column = new Column("id")
        def value = Mock(Value) {
            getColumns() >> [column]
            getTable()   >> table
        }
        def property = Mock(Property) {
            getName() >> "id"
            getValue() >> value
        }
        def type = Mock(Type) {
            getReturnedClass() >> UUID
        }
        Mock(GeneratorCreationContext) {
            getServiceRegistry() >> serviceRegistry
            getDatabase()        >> db
            getProperty()        >> property
            getValue()           >> value
            getType()            >> type
        }
    }

    @Unroll
    def "should dispatch #strategyName to #expectedClass"() {
        given:
        def context = buildContext()
        def mappedId = Mock(HibernateSimpleIdentity) {
            // Explicit sequence name avoids the implicit-name path that requires TABLE property
            getProperties() >> { def p = new Properties(); p.put(SequenceStyleGenerator.SEQUENCE_PARAM, "test_seq"); p }
        }
        def domainClass = Mock(GrailsHibernatePersistentEntity) {
            getMappedForm() >> null
            getJavaClass()  >> GrailsSequenceGeneratorEnumSpecEntity
            getTableName(_ as PersistentEntityNamingStrategy) >> "grails_sequence_generator_enum_spec_entity"
        }
        def jdbcEnvironment = serviceRegistry.requireService(JdbcEnvironment)
        def namingStrategy = Mock(PersistentEntityNamingStrategy)

        when:
        def generator = GrailsSequenceGeneratorEnum.getGenerator(strategyName, context, mappedId, domainClass, jdbcEnvironment, namingStrategy)

        then:
        expectedClass.isInstance(generator)

        where:
        strategyName        | expectedClass
        "identity"          | GrailsIdentityGenerator
        "sequence"          | GrailsSequenceStyleGenerator
        "sequence-identity" | GrailsSequenceStyleGenerator
        "increment"         | GrailsIncrementGenerator
        "uuid"              | UuidGenerator
        "uuid2"             | UuidGenerator
        "assigned"          | Assigned
        "table"             | GrailsTableGenerator
        "enhanced-table"    | GrailsTableGenerator
        "hilo"              | GrailsSequenceStyleGenerator
        "native"            | GrailsNativeGenerator
        "unknown"           | GrailsNativeGenerator
    }

    def "fromName should return correct enum"() {
        expect:
        GrailsSequenceGeneratorEnum.fromName("identity").get() == GrailsSequenceGeneratorEnum.IDENTITY
        GrailsSequenceGeneratorEnum.fromName("nonexistent").isEmpty()
    }
}

@Entity
class GrailsSequenceGeneratorEnumSpecEntity implements HibernateEntity<GrailsSequenceGeneratorEnumSpecEntity> {
    String name
}
