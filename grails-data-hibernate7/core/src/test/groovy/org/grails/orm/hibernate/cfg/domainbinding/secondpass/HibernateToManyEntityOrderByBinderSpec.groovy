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

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyEntityProperty

import org.hibernate.mapping.Bag
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Column
import org.hibernate.mapping.OneToMany
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import spock.lang.Subject

class HibernateToManyEntityOrderByBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    HibernateToManyEntityOrderByBinder binder = new HibernateToManyEntityOrderByBinder()


    void setupSpec() {
        manager.addAllDomainClasses([
            COBOwnerEntity,
            COBAssociatedItem,
            COBUnidirectionalOwner,
            COBBaseItem,
            COBSubItem,
            COBHierarchyOwner,
        ])
    }

    private HibernateToManyEntityProperty propertyFor(Class ownerClass, String name = "items") {
        (getPersistentEntity(ownerClass) as GrailsHibernatePersistentEntity).getPropertyByName(name) as HibernateToManyEntityProperty
    }

    private RootClass rootClassWith(String entityName, String propertyName, String columnName) {
        def mbc = getGrailsDomainBinder().getMetadataBuildingContext()
        def rootClass = new RootClass(mbc)
        rootClass.setEntityName(entityName)
        def table = new Table("test", entityName.toLowerCase())
        def simpleValue = new BasicValue(mbc, table)
        simpleValue.setTypeName("string")
        simpleValue.addColumn(new Column(columnName))
        def prop = new Property()
        prop.setName(propertyName)
        prop.setValue(simpleValue)
        rootClass.addProperty(prop)
        return rootClass
    }

    def "bind sets orderBy when sort is configured on a bidirectional association"() {
        given:
        def property = propertyFor(COBOwnerEntity)
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        collection.setRole("${COBOwnerEntity.name}.items")
        def associatedClass = rootClassWith(COBAssociatedItem.name, "value", "VALUE")
        associatedClass.setTable(new Table("COB_ASSOCIATED_ITEM"))
        property.getHibernateAssociatedEntity().setPersistentClass(associatedClass)
        
        property.getMappedForm().setSort("value")
        property.getMappedForm().setOrder("desc")

        property.setCollection(collection)

        when:
        binder.bind(property)

        then:
        collection.getOrderBy() != null
        collection.getOrderBy().contains("desc")
    }

    def "bind defaults to asc when order is not specified"() {
        given:
        def property = propertyFor(COBOwnerEntity)
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        collection.setRole("${COBOwnerEntity.name}.items")
        def associatedClass = rootClassWith(COBAssociatedItem.name, "value", "VALUE")
        associatedClass.setTable(new Table("COB_ASSOCIATED_ITEM"))
        property.getHibernateAssociatedEntity().setPersistentClass(associatedClass)
        
        property.getMappedForm().setSort("value")

        property.setCollection(collection)

        when:
        binder.bind(property)

        then:
        collection.getOrderBy() != null
        collection.getOrderBy().contains("asc")
    }

    def "bind does not set orderBy when no sort is configured but still binds association"() {
        given:
        def property = propertyFor(COBOwnerEntity)
        def metadataContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def collection = new Bag(metadataContext, null)
        def element = new OneToMany(metadataContext, collection.getOwner())
        collection.setElement(element)
        
        def associatedClass = new RootClass(metadataContext)
        associatedClass.setTable(new Table("COB_ASSOCIATED_ITEM"))
        property.getHibernateAssociatedEntity().setPersistentClass(associatedClass)

        property.setCollection(collection)

        when:
        binder.bind(property)

        then:
        collection.getOrderBy() == null
        element.getAssociatedClass() == associatedClass
    }

    def "bind sets where clause for table-per-hierarchy subclass"() {
        given:
        def property = propertyFor(COBHierarchyOwner)
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        def associatedClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        associatedClass.setTable(new Table("COB_BASE_ITEM"))
        property.getHibernateAssociatedEntity().setPersistentClass(associatedClass)

        property.setCollection(collection)

        when:
        binder.bind(property)

        then:
        collection.getWhere() != null
        collection.getWhere().contains("DTYPE in (")
        collection.getWhere().contains("COBSubItem")
    }
}

@Entity
class COBOwnerEntity {
    Long id
    static hasMany = [items: COBAssociatedItem]
}

@Entity
class COBAssociatedItem {
    Long id
    String value
    COBOwnerEntity owner
    static belongsTo = [owner: COBOwnerEntity]
    static mapping = {
        value column: 'item_value'
    }
}

@Entity
class COBUnidirectionalOwner {
    Long id
    static hasMany = [items: COBAssociatedItem]
}

@Entity
class COBBaseItem {
    Long id
    String value
    static mapping = {
        value column: 'base_value'
    }
}

@Entity
class COBSubItem extends COBBaseItem {
}

@Entity
class COBHierarchyOwner {
    Long id
    static hasMany = [items: COBSubItem]
}
