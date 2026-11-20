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

import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.util.UniqueNameGenerator
import org.hibernate.mapping.Column
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import org.hibernate.mapping.UniqueKey
import org.hibernate.mapping.Value

class NaturalIdSpec extends HibernateGormDatastoreSpec {

    void "test createUniqueKey with a single property"() {
        given:
        def naturalId = new NaturalId(propertyNames: ["id1"], mutable: true)
        def property = new Property()
        property.name = "id1"
        def value = Mock(Value)
        property.value = value
        def column = new Column("id1")
        def table = new Table("test_table")
        def rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        rootClass.addProperty(property)
        rootClass.table = table
        value.getSelectables() >> [column]
        value.hasAnyUpdatableColumns() >> true

        when:
        def result = naturalId.createUniqueKey(rootClass)

        then:
        result.isPresent()
        def uk = result.get()
        uk.table == table
        uk.columnSpan == 1
        property.isNaturalIdentifier()
        property.isUpdateable()
    }

    void "test createUniqueKey with composite property"() {
        given:
        def naturalId = new NaturalId(propertyNames: ["id1", "id2"], mutable: false)
        def property1 = new Property()
        property1.name = "id1"
        def value1 = Mock(Value)
        property1.value = value1
        def column1 = new Column("id1")
        
        def property2 = new Property()
        property2.name = "id2"
        def value2 = Mock(Value)
        property2.value = value2
        def column2 = new Column("id2")
        
        def table = new Table("test_table")
        def rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        rootClass.addProperty(property1)
        rootClass.addProperty(property2)
        rootClass.table = table
        value1.getSelectables() >> [column1]
        value1.hasAnyUpdatableColumns() >> false
        value2.getSelectables() >> [column2]
        value2.hasAnyUpdatableColumns() >> false

        when:
        def result = naturalId.createUniqueKey(rootClass)

        then:
        result.isPresent()
        def uk = result.get()
        uk.table == table
        uk.columnSpan == 2
        property1.isNaturalIdentifier()
        !property1.isUpdateable()
        property2.isNaturalIdentifier()
        !property2.isUpdateable()
    }

    void "test createUniqueKey with empty property names"() {
        given:
        def naturalId = new NaturalId(propertyNames: [], mutable: false)
        def table = new Table("test_table")
        def rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        rootClass.table = table

        when:
        def result = naturalId.createUniqueKey(rootClass)

        then:
        result.isEmpty()
    }
}
