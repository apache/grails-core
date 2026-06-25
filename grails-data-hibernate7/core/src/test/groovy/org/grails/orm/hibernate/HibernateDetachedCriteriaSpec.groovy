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
package org.grails.orm.hibernate

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.query.PropertyReference
import spock.lang.Unroll

class HibernateDetachedCriteriaSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(HDCProduct)
    }

    @Unroll
    def "propertyMissing returns PropertyReference for boxed numeric property #propertyName"() {
        when:
        def criteria = new HibernateDetachedCriteria(HDCProduct)
        def result = criteria.propertyMissing(propertyName)

        then:
        result instanceof PropertyReference
        result.propertyName == propertyName

        where:
        propertyName << ['price', 'quantity', 'rating', 'score', 'stock', 'discount']
    }

    @Unroll
    def "propertyMissing returns PropertyReference for primitive numeric property #propertyName"() {
        when:
        def criteria = new HibernateDetachedCriteria(HDCProduct)
        def result = criteria.propertyMissing(propertyName)

        then:
        result instanceof PropertyReference
        result.propertyName == propertyName

        where:
        propertyName << ['primitiveInt', 'primitiveLong', 'primitiveDouble', 'primitiveFloat', 'primitiveShort', 'primitiveByte']
    }

    def "propertyMissing delegates to super for non-numeric property (returns property criterion)"() {
        when:
        def criteria = new HibernateDetachedCriteria(HDCProduct)
        def result = criteria.propertyMissing("name")

        then:
        noExceptionThrown()
        !(result instanceof PropertyReference)
    }

    def "propertyMissing delegates to super for unknown property (throws MissingPropertyException)"() {
        when:
        def criteria = new HibernateDetachedCriteria(HDCProduct)
        criteria.propertyMissing("nonExistent")

        then:
        thrown(MissingPropertyException)
    }
}

@Entity
class HDCProduct {
    Long id

    // Boxed numeric types
    BigDecimal price
    Integer quantity
    Double rating
    Float score
    Long stock
    Short discount

    // Primitive numeric types (these were broken before the fix)
    int primitiveInt
    long primitiveLong
    double primitiveDouble
    float primitiveFloat
    short primitiveShort
    byte primitiveByte

    // Non-numeric
    String name
}
