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

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty
import org.hibernate.MappingException
import org.hibernate.mapping.Column
import org.hibernate.mapping.ManyToOne
import spock.lang.Unroll

import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ForeignKeyOneToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneValuesBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher

class ForeignKeyOneToOneBinderSpec extends HibernateGormDatastoreSpec {

    @Unroll
    def "bind sets alternate unique key and column uniqueness for #scenario"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def simpleValueBinder = Mock(SimpleValueBinder)
        def manyToOneValuesBinder = Mock(ManyToOneValuesBinder)
        def compositeBinder = Mock(CompositeIdentifierToManyToOneBinder)
        def columnFetcher = Mock(SimpleValueColumnFetcher)

        def manyToOneBinder = new ManyToOneBinder(
                getGrailsDomainBinder().getMetadataBuildingContext(),
                namingStrategy, simpleValueBinder, manyToOneValuesBinder, compositeBinder)
        def binder = new ForeignKeyOneToOneBinder(manyToOneBinder, columnFetcher)

        def property = Mock(TestFKOneToOne)
        def mapping = new Mapping()
        def refDomainClass = Mock(GrailsHibernatePersistentEntity) {
            getMappedForm() >> mapping
            getHibernateCompositeIdentity() >> Optional.empty()
        }
        def propertyConfig = Mock(PropertyConfig)
        def column = new Column('test')
        def inverseSide = Mock(TestFKOneToOne)

        property.getHibernateAssociatedEntity() >> refDomainClass
        mapping.setIdentity(null)
        property.getMappedForm() >> propertyConfig
        property.getHibernateMappedForm() >> propertyConfig
        columnFetcher.getColumnForSimpleValue(_ as ManyToOne) >> column

        propertyConfig.isUnique() >> isUnique
        propertyConfig.isUniqueWithinGroup() >> isUniqueWithinGroup
        property.isBidirectional() >> isBidirectional
        property.getHibernateInverseSide() >> inverseSide
        inverseSide.isValidHibernateOneToOne() >> isInverseHasOne

        when:
        def result = binder.bind(property, "/test")

        then:
        result.isAlternateUniqueKey()
        if (expectedUniqueValue != null) {
            assert column.isUnique() == expectedUniqueValue
        } else {
            assert !column.isUnique()
        }

        where:
        scenario                               | isUnique | isUniqueWithinGroup | isBidirectional | isInverseHasOne | expectedUniqueValue
        "simple unique=true"                   | true     | false               | false           | false           | true
        "simple unique=false"                  | false    | false               | false           | false           | false
        "uniqueWithinGroup and bidirectional"  | false    | true                | true            | true            | true
        "uniqueWithinGroup and unidirectional" | false    | true                | false           | false           | null
        "uniqueWithinGroup and not hasOne"     | false    | true                | true            | false           | null
    }

    def "bind throws MappingException when column is not found"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def simpleValueBinder = Mock(SimpleValueBinder)
        def manyToOneValuesBinder = Mock(ManyToOneValuesBinder)
        def compositeBinder = Mock(CompositeIdentifierToManyToOneBinder)
        def columnFetcher = Mock(SimpleValueColumnFetcher)

        def manyToOneBinder = new ManyToOneBinder(
                getGrailsDomainBinder().getMetadataBuildingContext(),
                namingStrategy, simpleValueBinder, manyToOneValuesBinder, compositeBinder)
        def binder = new ForeignKeyOneToOneBinder(manyToOneBinder, columnFetcher)

        def property = Mock(TestFKOneToOne)
        def mapping = new Mapping()
        def refDomainClass = Mock(GrailsHibernatePersistentEntity) {
            getMappedForm() >> mapping
            getHibernateCompositeIdentity() >> Optional.empty()
        }
        def propertyConfig = new PropertyConfig()

        property.getHibernateAssociatedEntity() >> refDomainClass
        mapping.setIdentity(null)
        property.getMappedForm() >> propertyConfig
        property.getHibernateMappedForm() >> propertyConfig
        columnFetcher.getColumnForSimpleValue(_ as ManyToOne) >> null

        when:
        binder.bind(property, "/test")

        then:
        thrown(MappingException)
    }
}

abstract class TestFKOneToOne extends HibernateOneToOneProperty {
    TestFKOneToOne(PersistentEntity owner, MappingContext context, java.beans.PropertyDescriptor descriptor) {
        super(owner, context, descriptor)
    }
}
