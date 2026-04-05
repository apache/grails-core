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

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneValuesBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.OneToMany
import org.hibernate.mapping.Table

class CollectionBinderSpec extends HibernateGormDatastoreSpec {

    CollectionBinder binder
    InFlightMetadataCollector mockCollector

    void setup() {
        def gdb = getGrailsDomainBinder()
        def mbc = gdb.getMetadataBuildingContext()
        def ns = gdb.getNamingStrategy()
        def je = gdb.getJdbcEnvironment()
        mockCollector = Mock(InFlightMetadataCollector) {
            getMetadataBuildingOptions() >> mbc.getMetadataCollector().getMetadataBuildingOptions()
            getBootstrapContext() >> mbc.getMetadataCollector().getBootstrapContext()
            getDatabase() >> mbc.getMetadataCollector().getDatabase()
            addTable(_, _, _, _, _, _) >> { schema, catalog, name, sub, isAbstract, context ->
                return new Table("test", name)
            }
        }
        
        def svb = new SimpleValueBinder(mbc, ns, je)
        def svcf = new SimpleValueColumnFetcher()
        def backticksRemover = new BackticksRemover()
        def dcnf = new DefaultColumnNameFetcher(ns, backticksRemover)
        def cnfpapf = new ColumnNameForPropertyAndPathFetcher(ns, dcnf, backticksRemover)
        def etb = new EnumTypeBinder(mbc, cnfpapf, ns)
        def citmto = new CompositeIdentifierToManyToOneBinder(new org.grails.orm.hibernate.cfg.domainbinding.util.ForeignKeyColumnCountCalculator(), ns, dcnf, backticksRemover, svb)
        def mtob = new ManyToOneBinder(mbc, ns, svb, new ManyToOneValuesBinder(), citmto)
        def ch = new CollectionHolder(mbc)
        def tfmc = new org.grails.orm.hibernate.cfg.domainbinding.util.TableForManyCalculator(ns, mockCollector)

        binder = new CollectionBinder(mbc, ns, svb, etb, mtob, citmto, svcf, ch, mockCollector, tfmc)
    }

    def setupSpec() {
        manager.addAllDomainClasses([Person, Pet, CBManyToManyA, CBManyToManyB])
    }

    void "test bindCollection for one-to-many"() {
        given:
        def personEntity = mappingContext.getPersistentEntity(Person.name) as GrailsHibernatePersistentEntity
        def petsProp = personEntity.getPropertyByName("pets")

        when:
        def collection = binder.bindCollection(petsProp, "")

        then:
        1 * mockCollector.addCollectionBinding(_)
        collection.role == "${Person.name}.pets".toString()
        collection.element instanceof OneToMany
        (collection.element as OneToMany).referencedEntityName == Pet.name
    }

    void "test bindCollection for many-to-many uses join table"() {
        given:
        def entityA = mappingContext.getPersistentEntity(CBManyToManyA.name) as GrailsHibernatePersistentEntity
        def othersProp = entityA.getPropertyByName("others")

        when:
        def collection = binder.bindCollection(othersProp, "")

        then:
        1 * mockCollector.addCollectionBinding(_)
        collection.role == "${CBManyToManyA.name}.others".toString()
        !collection.isOneToMany()
        collection.collectionTable != null
        collection.collectionTable.name == "cbmany_to_manya_others"
    }
}

@Entity
class Person {
    Long id
    String name
    static hasMany = [pets: Pet]
}

@Entity
class Pet {
    Long id
    String name
    static belongsTo = [owner: Person]
}

@Entity
class CBManyToManyA {
    Long id
    static hasMany = [others: CBManyToManyB]
}

@Entity
class CBManyToManyB {
    Long id
    static hasMany = [owners: CBManyToManyA]
    static belongsTo = CBManyToManyA
}
