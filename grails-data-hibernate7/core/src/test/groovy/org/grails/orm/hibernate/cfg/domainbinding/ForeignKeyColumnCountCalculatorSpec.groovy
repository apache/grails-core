/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToOneProperty
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.orm.hibernate.cfg.domainbinding.util.ForeignKeyColumnCountCalculator

class ForeignKeyColumnCountCalculatorSpec extends Specification {

    @Unroll
    def 'Test calculateForeignKeyColumnCount with #scenario'() {
        given:
        def calculator = new ForeignKeyColumnCountCalculator()
        def refDomainClass = Mock(GrailsHibernatePersistentEntity)

        // Mock for a simple property
        def simpleProp = Mock(HibernatePersistentProperty)
        refDomainClass.getHibernatePropertyByName('simple') >> simpleProp

        // Mocks for a ToOne association with a simple ID
        def toOneSimpleIdProp = Mock(HibernateToOneProperty)
        def associatedEntitySimpleId = Mock(HibernatePersistentEntity)
        refDomainClass.getHibernatePropertyByName('toOneSimple') >> toOneSimpleIdProp
        toOneSimpleIdProp.getAssociatedEntity() >> associatedEntitySimpleId
        associatedEntitySimpleId.getCompositeIdentity() >> null

        // Mocks for a ToOne association with a composite ID of length 2
        def toOneCompositeIdProp = Mock(HibernateToOneProperty)
        def associatedEntityCompositeId = Mock(HibernatePersistentEntity)
        def compositeId = [Mock(HibernatePersistentProperty), Mock(HibernatePersistentProperty)] as HibernatePersistentProperty[]
        refDomainClass.getHibernatePropertyByName('toOneComposite') >> toOneCompositeIdProp
        toOneCompositeIdProp.getAssociatedEntity() >> associatedEntityCompositeId
        associatedEntityCompositeId.getCompositeIdentity() >> compositeId

        when:
        int columnCount = calculator.calculateForeignKeyColumnCount(refDomainClass, propertyNames as String[])

        then:
        columnCount == expectedCount

        where:
        scenario                                | propertyNames                                | expectedCount
        'a single simple property'              | ['simple']                                   | 1
        'a ToOne with a simple ID'              | ['toOneSimple']                              | 1
        'a ToOne with a composite ID'           | ['toOneComposite']                           | 2
        'a mix of all property types'           | ['simple', 'toOneSimple', 'toOneComposite'] | 4
        'multiple simple properties'            | ['simple', 'simple']                         | 2
        'multiple composite ID properties'      | ['toOneComposite', 'toOneComposite']         | 4
    }
}
