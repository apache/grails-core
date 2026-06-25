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

package org.grails.orm.hibernate.cfg.domainbinding.binder

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.DiscriminatorConfig
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Formula
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import spock.lang.Subject

class ConfiguredDiscriminatorBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    ConfiguredDiscriminatorBinder binder

    MetadataBuildingContext metadataBuildingContext

    def setup() {
        def domainBinder = getGrailsDomainBinder()
        metadataBuildingContext = domainBinder.getMetadataBuildingContext()
        binder = new ConfiguredDiscriminatorBinder(
                new SimpleValueColumnBinder(),
                new ColumnConfigToColumnBinder()
        )
    }

    private RootClass createRootClass() {
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName(ConfiguredDiscriminatorBinderSpecEntity.name)
        rootClass.setJpaEntityName(ConfiguredDiscriminatorBinderSpecEntity.name)
        rootClass.setClassName(ConfiguredDiscriminatorBinderSpecEntity.name)
        rootClass.setTable(new Table("orm", "CONFIGURED_DISCRIMINATOR_TEST"))
        return rootClass
    }

    private BasicValue createDiscriminator(RootClass rootClass) {
        return new BasicValue(metadataBuildingContext, rootClass.getTable())
    }

    def "test bindConfiguredDiscriminator with value only"() {
        given:
        def rootClass = createRootClass()
        def discriminator = createDiscriminator(rootClass)
        def config = new DiscriminatorConfig(value: "CUSTOM_VALUE")

        when:
        binder.bindConfiguredDiscriminator(rootClass, discriminator, config)

        then:
        rootClass.getDiscriminatorValue() == "CUSTOM_VALUE"
        discriminator.getColumns().iterator().next().getName() == GrailsDomainBinder.JPA_DEFAULT_DISCRIMINATOR_TYPE
    }

    def "test bindConfiguredDiscriminator with custom string type"() {
        given:
        def rootClass = createRootClass()
        def discriminator = createDiscriminator(rootClass)
        def config = new DiscriminatorConfig(value: "TEST", type: "integer")

        when:
        binder.bindConfiguredDiscriminator(rootClass, discriminator, config)

        then:
        rootClass.getDiscriminatorValue() == "TEST"
        discriminator.getTypeName() == "integer"
    }

    def "test bindConfiguredDiscriminator with class type"() {
        given:
        def rootClass = createRootClass()
        def discriminator = createDiscriminator(rootClass)
        def config = new DiscriminatorConfig(value: "TEST", type: String.class)

        when:
        binder.bindConfiguredDiscriminator(rootClass, discriminator, config)

        then:
        rootClass.getDiscriminatorValue() == "TEST"
        discriminator.getTypeName() == "java.lang.String"
    }

    def "test bindConfiguredDiscriminator with insertable false"() {
        given:
        def rootClass = createRootClass()
        def discriminator = createDiscriminator(rootClass)
        def config = new DiscriminatorConfig(value: "TEST", insertable: false)

        when:
        binder.bindConfiguredDiscriminator(rootClass, discriminator, config)

        then:
        rootClass.getDiscriminatorValue() == "TEST"
        !rootClass.isDiscriminatorInsertable()
    }

    def "test bindConfiguredDiscriminator with formula"() {
        given:
        def rootClass = createRootClass()
        def discriminator = createDiscriminator(rootClass)
        def config = new DiscriminatorConfig(value: "TEST", formula: "case when type=1 then 'A' else 'B' end")

        when:
        binder.bindConfiguredDiscriminator(rootClass, discriminator, config)

        then:
        rootClass.getDiscriminatorValue() == "TEST"
        discriminator.getSelectables().iterator().next() instanceof Formula
        ((Formula) discriminator.getSelectables().iterator().next()).getFormula() == "case when type=1 then 'A' else 'B' end"
    }

    def "test bindConfiguredDiscriminator with custom column name"() {
        given:
        def rootClass = createRootClass()
        def discriminator = createDiscriminator(rootClass)
        def columnConfig = new ColumnConfig(name: "MY_DISCRIMINATOR")
        def config = new DiscriminatorConfig(value: "TEST", column: columnConfig)

        when:
        binder.bindConfiguredDiscriminator(rootClass, discriminator, config)

        then:
        rootClass.getDiscriminatorValue() == "TEST"
        discriminator.getColumns().iterator().next().getName() == "MY_DISCRIMINATOR"
    }

    def "test resolveTypeName with null returns string"() {
        expect:
        binder.resolveTypeName(null) == "string"
    }

    def "test resolveTypeName with Class returns class name"() {
        expect:
        binder.resolveTypeName(Integer.class) == "java.lang.Integer"
    }

    def "test resolveTypeName with String returns same value"() {
        expect:
        binder.resolveTypeName("custom") == "custom"
    }
}

@Entity
class ConfiguredDiscriminatorBinderSpecEntity {
    Long id
    String name
}
