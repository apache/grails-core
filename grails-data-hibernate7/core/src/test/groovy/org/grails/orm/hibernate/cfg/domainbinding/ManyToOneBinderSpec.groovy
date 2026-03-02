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
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToOneProperty
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.Mapping
import java.util.Optional
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty
import org.hibernate.mapping.ManyToOne
import spock.lang.Unroll

import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneValuesBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher

class ManyToOneBinderSpec extends HibernateGormDatastoreSpec {

    @Unroll
    def "Test bindManyToOne orchestration for #scenario"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def simpleValueBinder = Mock(SimpleValueBinder)
        def manyToOneValuesBinder = Mock(ManyToOneValuesBinder)
        def compositeBinder = Mock(CompositeIdentifierToManyToOneBinder)

        def binder = new ManyToOneBinder(getGrailsDomainBinder().getMetadataBuildingContext(), namingStrategy, simpleValueBinder, manyToOneValuesBinder, compositeBinder)

        def association = Mock(HibernateManyToOneProperty)
        def path = "/test"
        def mapping = new Mapping()
        mapping.setIdentity(hasCompositeId ? new CompositeIdentity() : null)
        def refDomainClass = Mock(GrailsHibernatePersistentEntity) {
            getMappedForm() >> mapping
            getHibernateCompositeIdentity() >> Optional.ofNullable(mapping.hasCompositeIdentifier() ? (CompositeIdentity) mapping.getIdentity() : null)
        }
        def propertyConfig = new PropertyConfig()

        association.getHibernateAssociatedEntity() >> refDomainClass
        association.getMappedForm() >> propertyConfig

        when:
        def result = binder.bindManyToOne(association, null, path)

        then:
        result instanceof ManyToOne
        1 * manyToOneValuesBinder.bindManyToOneValues(association, _ as ManyToOne)
        compositeBinderCalls * compositeBinder.bindCompositeIdentifierToManyToOne(association as HibernatePersistentProperty, _ as ManyToOne, _, refDomainClass, path)
        simpleValueBinderCalls * simpleValueBinder.bindSimpleValue(association as HibernatePersistentProperty, null, _ as ManyToOne, path)

        where:
        scenario                 | hasCompositeId | compositeBinderCalls | simpleValueBinderCalls
        "a composite identifier" | true           | 1                    | 0
        "a simple identifier"    | false          | 0                    | 1
    }

    def "Test circular many-to-many binding"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def simpleValueBinder = Mock(SimpleValueBinder)
        def manyToOneValuesBinder = Mock(ManyToOneValuesBinder)
        def compositeBinder = Mock(CompositeIdentifierToManyToOneBinder)

        def binder = new ManyToOneBinder(getGrailsDomainBinder().getMetadataBuildingContext(), namingStrategy, simpleValueBinder, manyToOneValuesBinder, compositeBinder)

        def property = Mock(HibernateManyToManyProperty)
        def mapping = new Mapping()
        mapping.setColumns(new HashMap<String, PropertyConfig>())
        def ownerEntity = Mock(GrailsHibernatePersistentEntity) {
            getMappedForm() >> mapping
            getHibernateCompositeIdentity() >> Optional.empty()
        }
        def propertyConfig = new PropertyConfig()

        property.isCircular() >> true
        property.getOwner() >> ownerEntity
        property.getHibernateOwner() >> ownerEntity
        property.getName() >> "myCircularProp"
        property.getMappedForm() >> propertyConfig
        namingStrategy.resolveColumnName("myCircularProp") >> "my_circular_prop"

        when:
        def result = binder.bindManyToOne(property, null, "/test")

        then:
        result instanceof ManyToOne
        1 * manyToOneValuesBinder.bindManyToOneValues(property, _ as ManyToOne)
        1 * simpleValueBinder.bindSimpleValue(property as HibernatePersistentProperty, null, _ as ManyToOne, "/test")
        def resultConfig = mapping.getColumns().get("myCircularProp")
        resultConfig != null
        resultConfig.getJoinTable().getKey().getName() == "my_circular_prop_id"
    }

    @Unroll
    def "Test one-to-one binding with uniqueWithinGroup constraint for #scenario"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def simpleValueBinder = Mock(SimpleValueBinder)
        def manyToOneValuesBinder = Mock(ManyToOneValuesBinder)
        def compositeBinder = Mock(CompositeIdentifierToManyToOneBinder)
        def columnFetcher = Mock(SimpleValueColumnFetcher)

        def binder = new ManyToOneBinder(getGrailsDomainBinder().getMetadataBuildingContext(), namingStrategy, simpleValueBinder, manyToOneValuesBinder, compositeBinder, columnFetcher)

        def property = Mock(HibernateOneToOneProperty)
        def mapping = new Mapping()
        def refDomainClass = Mock(GrailsHibernatePersistentEntity) {
            getMappedForm() >> mapping
        }
        def propertyConfig = Mock(PropertyConfig)
        def column = new Column('test')
        def inverseSide = Mock(TestOneToOneInverse)

        property.getHibernateAssociatedEntity() >> refDomainClass
        mapping.setIdentity(null)
        property.getMappedForm() >> propertyConfig
        columnFetcher.getColumnForSimpleValue(_ as ManyToOne) >> column

        propertyConfig.isUnique() >> isUnique
        propertyConfig.isUniqueWithinGroup() >> isUniqueWithinGroup
        property.isBidirectional() >> isBidirectional
        property.getHibernateInverseSide() >> inverseSide
        inverseSide.isValidHibernateOneToOne() >> isInverseHasOne

        when:
        def result = binder.bindManyToOne(property, null, "/test")

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

    def "Test one-to-one binding throws exception when column is not found"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def simpleValueBinder = Mock(SimpleValueBinder)
        def manyToOneValuesBinder = Mock(ManyToOneValuesBinder)
        def compositeBinder = Mock(CompositeIdentifierToManyToOneBinder)
        def columnFetcher = Mock(SimpleValueColumnFetcher)

        def binder = new ManyToOneBinder(getGrailsDomainBinder().getMetadataBuildingContext(), namingStrategy, simpleValueBinder, manyToOneValuesBinder, compositeBinder, columnFetcher)

        def property = Mock(HibernateOneToOneProperty)
        def mapping = new Mapping()
        def refDomainClass = Mock(GrailsHibernatePersistentEntity) {
            getMappedForm() >> mapping
        }
        def propertyConfig = new PropertyConfig()

        property.getHibernateAssociatedEntity() >> refDomainClass
        mapping.setIdentity(null)
        property.getMappedForm() >> propertyConfig
        columnFetcher.getColumnForSimpleValue(_ as ManyToOne) >> null

        when:
        binder.bindManyToOne(property, null, "/test")

        then:
        thrown(MappingException)
    }
}

abstract class TestOneToOneInverse extends HibernateOneToOneProperty {
    TestOneToOneInverse(org.grails.datastore.mapping.model.PersistentEntity owner, org.grails.datastore.mapping.model.MappingContext context, java.beans.PropertyDescriptor descriptor) {
        super(owner, context, descriptor)
    }
}
