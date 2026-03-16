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
        def compositeIdentity = new CompositeIdentity(propertyNames: ['prop1', 'prop2'] as String[])

        when:
        def properties = compositeIdentity.getHibernateProperties(domainClass)

        then:
        1 * domainClass.getPropertyByName("prop1") >> prop1
        1 * domainClass.getPropertyByName("prop2") >> prop2
        properties.length == 2
        properties[0] == prop1
        properties[1] == prop2
    }

    def "test getHibernateProperties with fallback to domain class"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def prop1 = Mock(HibernatePersistentProperty)
        def compositeIdentity = new CompositeIdentity()

        when:
        def properties = compositeIdentity.getHibernateProperties(domainClass)

        then:
        1 * domainClass.getCompositeIdentity() >> ([prop1] as HibernatePersistentProperty[])
        0 * domainClass.getPropertyByName(_)
        properties.length == 1
        properties[0] == prop1
    }

    def "test getHibernateProperties throws exception if no properties found"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def compositeIdentity = new CompositeIdentity()

        when:
        compositeIdentity.getHibernateProperties(domainClass)

        then:
        1 * domainClass.getCompositeIdentity() >> null
        thrown(MappingException)
    }

    def "test getHibernateProperties throws exception if a property is invalid"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def compositeIdentity = new CompositeIdentity(propertyNames: ['invalid'] as String[])

        when:
        compositeIdentity.getHibernateProperties(domainClass)

        then:
        1 * domainClass.getPropertyByName("invalid") >> null
        thrown(MappingException)
    }
}
