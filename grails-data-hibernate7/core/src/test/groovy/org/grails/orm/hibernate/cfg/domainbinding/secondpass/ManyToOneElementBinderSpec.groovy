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
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionForPropertyConfigBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneValuesBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty
import org.hibernate.mapping.Bag
import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.Table
import spock.lang.Subject

class ManyToOneElementBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    ManyToOneElementBinder binder

    void setupSpec() {
        manager.addAllDomainClasses([
            MTMEOwner,
            MTMEItem,
            MTMEBase,
            MTMESubtype,
        ])
    }

    void setup() {
        def gdb = getGrailsDomainBinder()
        def mbc = gdb.getMetadataBuildingContext()
        def ns = gdb.getNamingStrategy()
        def je = gdb.getJdbcEnvironment()
        def svb = new SimpleValueBinder(mbc, ns, je)
        def citmto = new CompositeIdentifierToManyToOneBinder(mbc, ns, je)
        def mtob = new ManyToOneBinder(mbc, ns, svb, new ManyToOneValuesBinder(), citmto)
        binder = new ManyToOneElementBinder(mtob, new CollectionForPropertyConfigBinder())
    }

    private HibernateManyToManyProperty propertyFor(Class ownerClass, String name = "items") {
        (getPersistentEntity(ownerClass) as GrailsHibernatePersistentEntity).getPropertyByName(name) as HibernateManyToManyProperty
    }

    def "bind sets ManyToOne element referencing the inverse owner for a standard bidirectional many-to-many"() {
        given:
        def property = propertyFor(MTMEOwner)
        def mbc = getGrailsDomainBinder().getMetadataBuildingContext()
        def collection = new Bag(mbc, null)
        collection.setCollectionTable(new Table("test", "mtme_owner_mtme_item"))

        property.setCollection(collection)

        when:
        binder.bind(property)

        then:
        collection.getElement() instanceof ManyToOne
        (collection.getElement() as ManyToOne).getReferencedEntityName() == MTMEItem.name
    }
}

@Entity
class MTMEOwner {
    Long id
    static hasMany = [items: MTMEItem]
}

@Entity
class MTMEItem {
    Long id
    String description
    static hasMany = [owners: MTMEOwner]
}

@Entity
class MTMEBase {
    Long id
    static hasMany = [subtypes: MTMESubtype]
}

@Entity
class MTMESubtype extends MTMEBase {
    static hasMany = [related: MTMEBase]
}
