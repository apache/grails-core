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

package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.GrailsPropertyResolver
import org.hibernate.mapping.Collection
import org.hibernate.mapping.Column
import org.hibernate.mapping.DependantValue
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Table
import org.hibernate.mapping.Bag
import spock.lang.Subject

class BidirectionalOneToManyLinkerSpec extends HibernateGormDatastoreSpec {

    @Subject
    BidirectionalOneToManyLinker linker = new BidirectionalOneToManyLinker(new GrailsPropertyResolver())

    void "test link bidirectional one to many"() {
        given:
        def metadataContext = getGrailsDomainBinder().getMetadataBuildingContext()
        RootClass rootClass = new RootClass(metadataContext)
        rootClass.setEntityName("TestEntity")
        
        Table table = new Table("test_table")
        rootClass.setTable(table)
        
        Property otherSideProperty = new Property()
        otherSideProperty.setName("owner")
        
        BasicValue value = new BasicValue(metadataContext, table)
        Column column = new Column("owner_id")
        column.setLength(10)
        column.setSqlType("bigint")
        value.addColumn(column)
        otherSideProperty.setValue(value)
        rootClass.addProperty(otherSideProperty)
        
        Collection collection = new Bag(metadataContext, rootClass)
        Table collectionTable = new Table("collection_table")
        DependantValue key = new DependantValue(metadataContext, collectionTable, null)
        
        HibernateToManyProperty otherSide = Mock(HibernateToManyProperty)
        otherSide.getName() >> "owner"
        otherSide.isNullable() >> true

        when:
        linker.link(collection, rootClass, key, otherSide)

        then:
        collection.isInverse()
        key.getColumnSpan() == 1
        key.getColumns().first().getName() == "owner_id"
        key.getColumns().first().getLength() == 10
        key.getColumns().first().getSqlType() == "bigint"
        key.getColumns().first().isNullable()
    }
}
