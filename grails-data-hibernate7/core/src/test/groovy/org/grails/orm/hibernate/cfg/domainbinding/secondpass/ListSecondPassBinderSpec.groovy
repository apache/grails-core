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
import grails.persistence.Entity
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.domainbinding.binder.*
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.*
import org.grails.orm.hibernate.cfg.domainbinding.util.*
import org.hibernate.MappingException
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.mapping.*

class ListSecondPassBinderSpec extends HibernateGormDatastoreSpec {

    protected java.util.Map getBinders(GrailsDomainBinder binder, InFlightMetadataCollector collector = getCollector()) {
        MetadataBuildingContext mbc = binder.getMetadataBuildingContext()
        PersistentEntityNamingStrategy ns = binder.getNamingStrategy()
        JdbcEnvironment je = binder.getJdbcEnvironment()
        BackticksRemover br = new BackticksRemover()
        DefaultColumnNameFetcher dcnf = new DefaultColumnNameFetcher(ns, br)
        ColumnNameForPropertyAndPathFetcher cnfpapf = new ColumnNameForPropertyAndPathFetcher(ns, dcnf, br)
        CollectionHolder ch = new CollectionHolder(mbc)
        SimpleValueBinder svb = new SimpleValueBinder(mbc, ns, je)
        EnumTypeBinder etb = new EnumTypeBinder(mbc, cnfpapf, ns)
        SimpleValueColumnFetcher svcf = new SimpleValueColumnFetcher()
        CompositeIdentifierToManyToOneBinder citmto = new CompositeIdentifierToManyToOneBinder(
                new ForeignKeyColumnCountCalculator(), ns, dcnf, br, svb)
        OneToOneBinder otob = new OneToOneBinder(mbc, svb)
        ManyToOneBinder mtob = new ManyToOneBinder(mbc, ns, svb, new ManyToOneValuesBinder(), citmto)
        ForeignKeyOneToOneBinder fkotob = new ForeignKeyOneToOneBinder(mtob, svcf)

        TableForManyCalculator tfmc = new TableForManyCalculator(ns, collector)
        CollectionBinder cb = new CollectionBinder(mbc, ns, svb, etb, mtob, citmto, svcf, ch, collector, tfmc)
        PropertyFromValueCreator pfvc = new PropertyFromValueCreator()
        ComponentUpdater cu = new ComponentUpdater(pfvc)
        ComponentBinder comb = new ComponentBinder(mbc, binder.getMappingCacheHolder(), cu)

        GrailsPropertyBinder pb = new GrailsPropertyBinder(etb, comb, cb, svb, otob, mtob, fkotob)
        CompositeIdBinder cib = new CompositeIdBinder(mbc, cu, pb)
        PropertyBinder pbh = new PropertyBinder()
        SimpleIdBinder sib = new SimpleIdBinder(mbc, new BasicValueCreator(mbc, je, ns), svb, pbh)
        IdentityBinder ib = new IdentityBinder(sib, cib)
        VersionBinder vb = new VersionBinder(mbc, svb, pbh, BasicValue::new)

        ClassBinder clb = new ClassBinder(collector)
        ClassPropertiesBinder clpb = new ClassPropertiesBinder(pb, pfvc)
        MultiTenantFilterBinder mtfb = new MultiTenantFilterBinder(new GrailsPropertyResolver(), new MultiTenantFilterDefinitionBinder(), collector, dcnf)
        JoinedSubClassBinder jscb = new JoinedSubClassBinder(mbc, ns, new org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder(), cnfpapf, clb, collector)
        UnionSubclassBinder uscb = new UnionSubclassBinder(mbc, ns, clb, collector)
        SingleTableSubclassBinder stscb = new SingleTableSubclassBinder(clb, mbc)

        SubclassMappingBinder scmb = new SubclassMappingBinder(jscb, uscb, stscb, clpb)
        SubClassBinder scb = new SubClassBinder(scmb, mtfb, "dataSource")
        RootPersistentClassCommonValuesBinder rpccvb = new RootPersistentClassCommonValuesBinder(mbc, ns, ib, vb, clb, clpb, collector)
        DiscriminatorPropertyBinder dpb = new DiscriminatorPropertyBinder(mbc, binder.getMappingCacheHolder(), new ConfiguredDiscriminatorBinder(new org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder(), new ColumnConfigToColumnBinder()), new DefaultDiscriminatorBinder(new org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder()))
        RootBinder rb = new RootBinder("default", mtfb, scb, rpccvb, dpb, collector, binder.getMappingCacheHolder())

        return [
            propertyBinder: pb,
            collectionBinder: cb,
            identityBinder: ib,
            versionBinder: vb,
            classBinder: clb,
            classPropertiesBinder: clpb,
            rootBinder: rb
        ]
    }

    void setupSpec() {
        // Empty to avoid global failures
    }

    protected HibernatePersistentProperty propertyFor(Class domainClass, String propertyName) {
        PersistentEntity entity = createPersistentEntity(domainClass)
        return (HibernatePersistentProperty) entity.getPropertyByName(propertyName)
    }

    protected RootClass createMockPersistentClass(Class domainClass, InFlightMetadataCollector collector, java.util.List<String> properties = []) {
        def binder = getGrailsDomainBinder()
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(domainClass.name)
        rootClass.setJpaEntityName(domainClass.simpleName)
        rootClass.setTable(collector.addTable(null, null, domainClass.simpleName.toUpperCase(), null, false, binder.getMetadataBuildingContext(), false))
        
        properties.each { propName ->
            def p = new Property()
            p.setName(propName)
            p.setValue(new BasicValue(binder.getMetadataBuildingContext(), rootClass.getTable()))
            rootClass.addProperty(p)
        }
        
        collector.addEntityBinding(rootClass)
        
        def entity = (GrailsHibernatePersistentEntity) getMappingContext().getPersistentEntity(domainClass.name) ?: createPersistentEntity(domainClass)
        entity.setPersistentClass(rootClass)
        
        return rootClass
    }

    def "bindListSecondPass applies index customization"() {
        given:
        def binder = getGrailsDomainBinder()
        def collector = getCollector()
        def listBinder = getBinders(binder, collector).collectionBinder.listSecondPassBinder
        def property = propertyFor(LSBCustomIndex, "items") as HibernateToManyProperty

        def rootClass = createMockPersistentClass(LSBCustomIndex, collector)
        
        def list = new org.hibernate.mapping.List(binder.getMetadataBuildingContext(), rootClass)
        list.setRole("${LSBCustomIndex.name}.items".toString())
        list.setCollectionTable(rootClass.getTable())
        list.setElement(new BasicValue(binder.getMetadataBuildingContext(), list.getCollectionTable()))
        property.setCollection(list)

        when:
        listBinder.bindListSecondPass(property)

        then:
        list.index != null
        list.index.getColumn(0).name == "my_index_col"
        (list.index as BasicValue).typeName == "long"
    }

    def "bindListSecondPass throws exception for many-to-many non-owning side"() {
        given:
        def binder = getGrailsDomainBinder()
        def collector = getCollector()
        def listBinder = getBinders(binder, collector).collectionBinder.listSecondPassBinder
        
        def property = propertyFor(LSBManyToManyB, "owners") as HibernateManyToManyProperty
        def ownerRoot = createMockPersistentClass(LSBManyToManyB, collector, ["owners"])

        def list = new org.hibernate.mapping.List(binder.getMetadataBuildingContext(), ownerRoot)
        list.setRole("${LSBManyToManyB.name}.owners".toString())
        property.setCollection(list)

        when:
        listBinder.bindListSecondPass(property)

        then:
        def e = thrown(MappingException)
        e.message.contains("has no associated class") || e.message.contains("List collection types only supported on the owning side")
    }

    def "bindListSecondPass handles many-to-many specific flags"() {
        given:
        def binder = getGrailsDomainBinder()
        def collector = getCollector()
        def listBinder = getBinders(binder, collector).collectionBinder.listSecondPassBinder
        
        def property = propertyFor(LSBManyToManyA, "others") as HibernateManyToManyProperty
        def ownerRoot = createMockPersistentClass(LSBManyToManyA, collector, ["others"])
        def otherRoot = createMockPersistentClass(LSBManyToManyB, collector, ["owners"])

        def list = new org.hibernate.mapping.List(binder.getMetadataBuildingContext(), ownerRoot)
        list.setRole("${LSBManyToManyA.name}.others".toString())
        list.setCollectionTable(collector.addTable(null, null, "JOIN_TABLE", null, false, binder.getMetadataBuildingContext(), false))
        list.setKey(new DependantValue(binder.getMetadataBuildingContext(), list.getCollectionTable(), null))
        list.setElement(new ManyToOne(binder.getMetadataBuildingContext(), list.getCollectionTable()))
        ((ManyToOne)list.getElement()).setReferencedEntityName(LSBManyToManyB.name)
        property.setCollection(list)

        when:
        listBinder.bindListSecondPass(property)

        then:
        def backref = otherRoot.getProperties().find { it.name == "_" + "LSBManyToManyA" + "_" + "others" + "Backref" }
        backref instanceof Backref
        !backref.isInsertable()

        def indexBackref = otherRoot.getProperties().find { it.name == "_" + "others" + "IndexBackref" }
        indexBackref instanceof IndexBackref
        !indexBackref.isInsertable()
    }

    def "bindListSecondPass handles circular associations"() {
        given:
        def binder = getGrailsDomainBinder()
        def collector = getCollector()
        def listBinder = getBinders(binder, collector).collectionBinder.listSecondPassBinder
        def property = propertyFor(LSBCircular, "children") as HibernateToManyProperty

        def rootClass = createMockPersistentClass(LSBCircular, collector, ["parent", "children"])

        def list = new org.hibernate.mapping.List(binder.getMetadataBuildingContext(), rootClass)
        list.setRole("${LSBCircular.name}.children".toString())
        list.setCollectionTable(rootClass.getTable())
        def key = new DependantValue(binder.getMetadataBuildingContext(), list.getCollectionTable(), null)
        key.setNullable(false)
        list.setKey(key)
        list.setElement(new ManyToOne(binder.getMetadataBuildingContext(), list.getCollectionTable()))
        ((ManyToOne)list.getElement()).setReferencedEntityName(LSBCircular.name)
        property.setCollection(list)

        when:
        listBinder.bindListSecondPass(property)

        then:
        property.isCircular()
        // For circular, we don't force nullable false
        list.getKey().isNullable()
    }

    def "bindListSecondPass handles composite identity"() {
        given:
        def binder = getGrailsDomainBinder()
        def collector = getCollector()
        def listBinder = getBinders(binder, collector).collectionBinder.listSecondPassBinder
        
        def property = propertyFor(LSBCompositeIdOwner, "items") as HibernateToManyProperty
        def ownerRoot = createMockPersistentClass(LSBCompositeIdOwner, collector, ["items"])
        def itemRoot = createMockPersistentClass(LSBCompositeIdItem, collector, ["owner", "name"])

        def list = new org.hibernate.mapping.List(binder.getMetadataBuildingContext(), ownerRoot)
        list.setRole("${LSBCompositeIdOwner.name}.items".toString())
        list.setCollectionTable(itemRoot.getTable())
        list.setKey(new DependantValue(binder.getMetadataBuildingContext(), list.getCollectionTable(), null))
        list.setElement(new ManyToOne(binder.getMetadataBuildingContext(), list.getCollectionTable()))
        ((ManyToOne)list.getElement()).setReferencedEntityName(LSBCompositeIdItem.name)
        property.setCollection(list)

        expect:
        property.getHibernateInverseSide().isCompositeIdProperty()

        when:
        listBinder.bindListSecondPass(property)

        then:
        // No Backref should be created for composite ID inverse
        !itemRoot.getProperties().find { it.name.endsWith("Backref") && it instanceof Backref }
        
        // IndexBackref should still be created
        itemRoot.getProperties().find { it.name == "_" + "items" + "IndexBackref" } instanceof IndexBackref
    }
}

@Entity
class LSBCustomIndex {
    Long id
    java.util.List<String> items
    static hasMany = [items: String]
    static mapping = {
        items index: [column: "my_index_col", type: "long"]
    }
}

@Entity
class LSBCircular {
    Long id
    LSBCircular parent
    java.util.List<LSBCircular> children
    static hasMany = [children: LSBCircular]
    static belongsTo = [parent: LSBCircular]
}

@Entity
class LSBAuthor {
    Long id
    java.util.List<LSBBook> books
    static hasMany = [books: LSBBook]
}

@Entity
class LSBBook {
    Long id
    LSBAuthor author
    static belongsTo = [author: LSBAuthor]
}

@Entity
class LSBManyToManyA {
    Long id
    java.util.List<LSBManyToManyB> others
    static hasMany = [others: LSBManyToManyB]
}

@Entity
class LSBManyToManyB {
    Long id
    java.util.List<LSBManyToManyA> owners
    static hasMany = [owners: LSBManyToManyA]
    static belongsTo = LSBManyToManyA
}

@Entity
class LSBCompositeIdOwner {
    Long id
    java.util.List<LSBCompositeIdItem> items
    static hasMany = [items: LSBCompositeIdItem]
}

@Entity
class LSBCompositeIdItem implements Serializable {
    LSBCompositeIdOwner owner
    String name
    static belongsTo = [owner: LSBCompositeIdOwner]
    static mapping = {
        id composite: ['owner', 'name']
    }
}
