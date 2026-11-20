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

package org.grails.orm.hibernate.cfg

import org.hibernate.MappingException
import spock.lang.Specification
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty

class CompositeIdentitySpec extends Specification {

    def "test getHibernateProperties with property names"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def prop1 = Mock(HibernatePersistentProperty)
        def prop2 = Mock(HibernatePersistentProperty)
        def compositeIdentity = new HibernateCompositeIdentity(propertyNames: ['prop1', 'prop2'] as String[])

        when:
        def properties = compositeIdentity.getHibernateProperties(domainClass)

        then:
        1 * domainClass.getHibernatePropertyByName("prop1") >> prop1
        1 * domainClass.getHibernatePropertyByName("prop2") >> prop2
        properties.length == 2
        properties[0] == prop1
        properties[1] == prop2
    }

    def "test getHibernateProperties with fallback to domain class"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def prop1 = Mock(HibernatePersistentProperty)
        def compositeIdentity = new HibernateCompositeIdentity()

        when:
        def properties = compositeIdentity.getHibernateProperties(domainClass)

        then:
        1 * domainClass.getCompositeIdentity() >> ([prop1] as HibernatePersistentProperty[])
        0 * domainClass.getHibernatePropertyByName(_)
        properties.length == 1
        properties[0] == prop1
    }

    def "test getHibernateProperties throws exception if no properties found"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def compositeIdentity = new HibernateCompositeIdentity()

        when:
        compositeIdentity.getHibernateProperties(domainClass)

        then:
        1 * domainClass.getCompositeIdentity() >> null
        thrown(MappingException)
    }

    def "test getHibernateProperties throws exception if a property is invalid"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def compositeIdentity = new HibernateCompositeIdentity(propertyNames: ['invalid'] as String[])

        when:
        compositeIdentity.getHibernateProperties(domainClass)

        then:
        1 * domainClass.getHibernatePropertyByName("invalid") >> null
        thrown(MappingException)
    }

    def "test getPropertyNames"() {
        given:
        def propertyNames = ['prop1', 'prop2'] as String[]
        def compositeIdentity = new HibernateCompositeIdentity(propertyNames: propertyNames)

        expect:
        compositeIdentity.getPropertyNames() == propertyNames
    }

    def "naturalId closure configures NaturalId and returns this"() {
        given:
        def compositeIdentity = new HibernateCompositeIdentity(propertyNames: ['firstName', 'lastName'] as String[])

        when:
        def result = compositeIdentity.naturalId { mutable true }

        then:
        result.is(compositeIdentity)
        compositeIdentity.natural != null
        compositeIdentity.natural.mutable
    }

    def "naturalId closure sets propertyNames on NaturalId"() {
        given:
        def compositeIdentity = new HibernateCompositeIdentity(propertyNames: ['code'] as String[])

        when:
        compositeIdentity.naturalId { propertyNames(['code']) }

        then:
        compositeIdentity.natural.propertyNames == ['code']
    }

    def "getHibernateProperties throws exception when domain class returns empty composite array"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def compositeIdentity = new HibernateCompositeIdentity()

        when:
        compositeIdentity.getHibernateProperties(domainClass)

        then:
        1 * domainClass.getCompositeIdentity() >> ([] as HibernatePersistentProperty[])
        thrown(MappingException)
    }
}
