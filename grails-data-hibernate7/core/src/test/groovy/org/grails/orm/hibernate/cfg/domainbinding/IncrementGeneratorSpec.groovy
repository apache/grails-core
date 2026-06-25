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

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.Table
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsIncrementGenerator
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.id.IncrementGenerator
import org.hibernate.mapping.Property

class IncrementGeneratorSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(EntityWithIncrement)
    }

    @Rollback
    void "test increment generator"() {
        when:
        def entity1 = new EntityWithIncrement(name: "test1").save(flush: true)
        def entity2 = new EntityWithIncrement(name: "test2").save(flush: true)

        then:
        entity1.id != null
        entity2.id != null
        entity2.id > entity1.id
    }

    /**
     * Retrieve the live GrailsIncrementGenerator instance created by the datastore
     * during buildSessionFactory so we can call its protected methods directly.
     */
    private GrailsIncrementGenerator liveGenerator() {
        def persister = datastore.sessionFactory.getRuntimeMetamodels()
                .getMappingMetamodel()
                .findEntityDescriptor(EntityWithIncrement)
        persister.identifierGenerator as GrailsIncrementGenerator
    }

    void "resolveColumnName returns propertyName when it contains no dot"() {
        given:
        def gen = liveGenerator()
        def context = Mock(GeneratorCreationContext)
        context.getProperty() >> Mock(Property) { getName() >> "myId" }

        expect:
        gen.resolveColumnName(context, null) == "myId"
    }

    void "resolveColumnName falls back to mappedId name when propertyName contains a dot"() {
        given:
        def gen = liveGenerator()
        def context = Mock(GeneratorCreationContext)
        context.getProperty() >> Mock(Property) { getName() >> "composite.id" }

        def mappedId = new HibernateSimpleIdentity()
        mappedId.setName("pk")

        expect:
        gen.resolveColumnName(context, mappedId) == "pk"
    }

    void "resolveColumnName defaults to 'id' when both propertyName and mappedId name contain a dot"() {
        given:
        def gen = liveGenerator()
        def context = Mock(GeneratorCreationContext)
        context.getProperty() >> Mock(Property) { getName() >> "a.b" }

        def mappedId = new HibernateSimpleIdentity()
        mappedId.setName("x.y")

        expect:
        gen.resolveColumnName(context, mappedId) == "id"
    }

    void "resolveColumnName defaults to 'id' when propertyName has dot and mappedId is null"() {
        given:
        def gen = liveGenerator()
        def context = Mock(GeneratorCreationContext)
        context.getProperty() >> Mock(Property) { getName() >> "a.b" }

        expect:
        gen.resolveColumnName(context, null) == "id"
    }

    void "buildParams includes catalog and schema from mapping table config"() {
        given:
        def gen = liveGenerator()
        def context = Mock(GeneratorCreationContext)
        context.getProperty() >> Mock(Property) { getName() >> "id" }

        def tableConfig = new Table()
        tableConfig.catalog = "myCatalog"
        tableConfig.schema = "mySchema"

        def mapping = new Mapping()
        mapping.table = tableConfig

        def domainClass = Mock(GrailsHibernatePersistentEntity)
        domainClass.getTableName(_ as PersistentEntityNamingStrategy) >> "my_table"
        domainClass.getHibernateMappedForm() >> mapping

        when:
        def params = gen.buildParams(context, null, domainClass, Mock(PersistentEntityNamingStrategy))

        then:
        params.getProperty('catalog') == 'myCatalog'
        params.getProperty('schema') == 'mySchema'
        params.getProperty(IncrementGenerator.TABLES) == "my_table"
    }
}

@Entity
class EntityWithIncrement {
    Long id
    String name
    static mapping = {
        id generator: 'increment'
    }
}

