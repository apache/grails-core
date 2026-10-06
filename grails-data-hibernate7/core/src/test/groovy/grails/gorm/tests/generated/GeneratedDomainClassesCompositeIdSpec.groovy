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
package grails.gorm.tests.generated

import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.hibernate.Hibernate
import org.hibernate.proxy.HibernateProxy
import org.hibernate.persister.entity.EntityPersister

/**
 * An entity with a composite identifier (<code>id composite: [...]</code>) booted through the generated-domain-class path. GORM's
 * identifier of such an entity is the entity instance itself, which Hibernate's annotation binder gets from an
 * <code>@IdClass</code> (a non-aggregated identifier with no identifier property); the binding re-points the identifier at the
 * real class. The specs exercise it as an application does: save, get by an instance that carries the key, finders, where
 * queries, criteria, HQL, update, delete, an association inside the key, a foreign key and a collection pointing at the
 * composite entity, and a subclass.
 */
class GeneratedDomainClassesCompositeIdSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        registerGeneratedDomainClasses(
                GdcCidItem, GdcCidSpecial, GdcCidOwner, GdcCidPart, GdcCidRef, GdcCidBox, GdcCidThing, GdcCidSequenced)
    }

    private GdcCidItem savedItem(String region = 'eu', String code = 'a', String label = 'first') {
        return new GdcCidItem(region: region, code: code, label: label).save(flush: true)
    }

    def "a part of the identifier that maps a generator is generated, the others are assigned"() {
        when:
        GdcCidSequenced first = new GdcCidSequenced(region: 'eu', label: 'one').save(flush: true)
        GdcCidSequenced second = new GdcCidSequenced(region: 'eu', label: 'two').save(flush: true)
        sessionFactory.currentSession.clear()
        List sequences = sessionFactory.currentSession.createNativeQuery(
                "select sequence_name from information_schema.sequences where sequence_name = 'GDC_CID_SEQ'", String).list()

        then:
        first.partNumber != null
        second.partNumber == first.partNumber + 1
        GdcCidSequenced.count() == 2
        GdcCidSequenced.get(new GdcCidSequenced(partNumber: first.partNumber, region: 'eu')).label == 'one'
        sequences == ['GDC_CID_SEQ']
    }

    def "the entity is its own identifier and has no identifier property"() {
        when:
        EntityPersister persister = sessionFactory.mappingMetamodel.getEntityDescriptor(GdcCidItem)

        then:
        persister.entityName == GdcCidItem.name
        persister.mappedClass == GdcCidItem
        persister.identifierPropertyName == null
        persister.identifierMapping.virtualIdEmbeddable.mappedJavaType.javaTypeClass == GdcCidItem
    }

    def "the JPA metamodel has the entity typed with the real class, although the identifier is typed with it too"() {
        expect:
        sessionFactory.metamodel.entity(GdcCidItem).javaType == GdcCidItem
        sessionFactory.metamodel.entity(GdcCidSpecial).javaType == GdcCidSpecial
        sessionFactory.metamodel.entity(GdcCidPart).javaType == GdcCidPart
    }

    def "the primary key has the identifier columns, not null, in the order the binder gives them"() {
        when:
        List key = sessionFactory.currentSession.createNativeQuery(
                '''select k.column_name from information_schema.table_constraints t
                   join information_schema.key_column_usage k on k.constraint_name = t.constraint_name
                   where t.constraint_type = 'PRIMARY KEY' and t.table_name = 'GDC_CID_ITEM' order by k.ordinal_position''', String).list()
        List nullable = sessionFactory.currentSession.createNativeQuery(
                '''select is_nullable from information_schema.columns
                   where table_name = 'GDC_CID_ITEM' and column_name in ('CODE', 'REGION')''', String).list()

        then:
        key == ['CODE', 'REGION']
        nullable == ['NO', 'NO']
    }

    def "a real instance is saved, and loaded by an instance that carries the key"() {
        given:
        savedItem()
        session.clear()

        when:
        GdcCidItem loaded = GdcCidItem.get(new GdcCidItem(region: 'eu', code: 'a'))

        then:
        loaded.getClass() == GdcCidItem
        loaded.label == 'first'
        GdcCidItem.get(new GdcCidItem(region: 'us', code: 'a')) == null
    }

    def "loading twice by the key gives the instance the session already holds"() {
        given:
        GdcCidItem saved = savedItem()

        expect:
        GdcCidItem.get(new GdcCidItem(region: 'eu', code: 'a')).is(saved)
    }

    def "update, delete and count work on an entity with a composite identifier"() {
        given:
        GdcCidItem item = savedItem()

        when:
        item.label = 'changed'
        item.save(flush: true)
        session.clear()

        then:
        GdcCidItem.get(new GdcCidItem(region: 'eu', code: 'a')).label == 'changed'
        GdcCidItem.get(new GdcCidItem(region: 'eu', code: 'a')).version == 1
        GdcCidItem.count() == 1

        when:
        GdcCidItem.get(new GdcCidItem(region: 'eu', code: 'a')).delete(flush: true)

        then:
        GdcCidItem.count() == 0
    }

    def "finders, where queries, criteria and HQL return real instances"() {
        given:
        savedItem('eu', 'a', 'one')
        savedItem('eu', 'b', 'two')
        savedItem('us', 'a', 'three')
        session.clear()

        when:
        List byCode = GdcCidItem.where { code == 'a' }.list()

        then:
        byCode*.label.sort() == ['one', 'three']
        GdcCidItem.findAllByRegion('eu')*.label.sort() == ['one', 'two']
        GdcCidItem.findByRegionAndCode('us', 'a').label == 'three'
        GdcCidItem.createCriteria().list { eq('region', 'eu') }*.label.sort() == ['one', 'two']
        GdcCidItem.executeQuery('from GdcCidItem where region = :r and code = :c', [r: 'us', c: 'a'])*.label == ['three']
        GdcCidItem.executeQuery('select count(*) from GdcCidItem')[0] == 3
        GdcCidItem.list().every { it.getClass() == GdcCidItem }
    }

    def "the identifier helpers behave as they do through the domain binder"() {
        given:
        savedItem()
        session.clear()

        when:
        GdcCidItem loaded = GdcCidItem.get(new GdcCidItem(region: 'eu', code: 'a'))

        then: "Hibernate's identifier is an instance of the entity class that carries the key; GORM's ident() has no value for a composite identifier, as with the binder"
        sessionFactory.persistenceUnitUtil.getIdentifier(loaded).getClass() == GdcCidItem
        sessionFactory.persistenceUnitUtil.getIdentifier(loaded).code == 'a'
        sessionFactory.persistenceUnitUtil.getIdentifier(loaded).region == 'eu'
        loaded.ident() == null
        sessionFactory.currentSession.getReference(GdcCidItem, new GdcCidItem(region: 'eu', code: 'a')).label == 'first'
        GdcCidItem.proxy(new GdcCidItem(region: 'eu', code: 'a')).label == 'first'
        GdcCidItem.lock(new GdcCidItem(region: 'eu', code: 'a')).label == 'first'
        loaded.refresh().label == 'first'
        !loaded.isDirty()
    }

    def "a subclass is polymorphic and shares the key"() {
        given:
        new GdcCidSpecial(region: 'eu', code: 's', label: 'special', extra: 'x').save(flush: true)
        session.clear()

        when:
        GdcCidItem loaded = GdcCidItem.get(new GdcCidItem(region: 'eu', code: 's'))

        then:
        loaded.getClass() == GdcCidSpecial
        ((GdcCidSpecial) loaded).extra == 'x'
        GdcCidItem.list()*.label == ['special']
        GdcCidSpecial.count() == 1
    }

    def "an association inside the key is part of the primary key and the identifier"() {
        given:
        GdcCidOwner owner = new GdcCidOwner(name: 'o').save(flush: true)
        new GdcCidPart(owner: owner, seq: 's1', label: 'one').save(flush: true)
        new GdcCidPart(owner: owner, seq: 's2', label: 'two').save(flush: true)
        session.clear()

        when:
        GdcCidPart loaded = GdcCidPart.get(new GdcCidPart(owner: GdcCidOwner.proxy(owner.id), seq: 's2'))

        then:
        loaded.label == 'two'
        loaded.owner.name == 'o'
        GdcCidPart.findAllBySeq('s1')*.label == ['one']
        GdcCidPart.count() == 2
    }

    def "a foreign key to an entity with a composite identifier is stored with one column for each identifier property"() {
        given:
        GdcCidItem item = savedItem()
        new GdcCidRef(name: 'r', item: item).save(flush: true)
        session.clear()

        when:
        GdcCidRef ref = GdcCidRef.findByName('r')
        List columns = sessionFactory.currentSession
                .createNativeQuery("select column_name from information_schema.columns where table_name = 'GDC_CID_REF'", String)
                .list()*.toUpperCase()

        then:
        ref.item instanceof HibernateProxy
        !Hibernate.isInitialized(ref.item)
        ref.item.label == 'first'
        GdcCidRef.findByItem(GdcCidItem.get(new GdcCidItem(region: 'eu', code: 'a'))).name == 'r'
        columns.containsAll(['GDC_CID_ITEM_CODE', 'GDC_CID_ITEM_REGION'])
    }

    def "a collection of an entity with a composite identifier cascades and loads lazily"() {
        given:
        GdcCidBox box = new GdcCidBox(code: 'c', region: 'r')
        box.addToThings(new GdcCidThing(name: 't1'))
        box.addToThings(new GdcCidThing(name: 't2'))
        box.save(flush: true)
        session.clear()

        when:
        GdcCidBox loaded = GdcCidBox.get(new GdcCidBox(code: 'c', region: 'r'))

        then:
        !Hibernate.isInitialized(loaded.things)
        loaded.things*.name.sort() == ['t1', 't2']
        GdcCidThing.findByName('t1').box.code == 'c'
    }

    def "a proxy of an entity with a composite identifier is loaded by the instance that carries the key"() {
        given:
        savedItem()
        session.clear()

        when:
        GdcCidItem proxy = GdcCidItem.load(new GdcCidItem(region: 'eu', code: 'a'))

        then:
        proxy instanceof HibernateProxy
        !Hibernate.isInitialized(proxy)
        proxy.label == 'first'
        Hibernate.isInitialized(proxy)
    }
}

@Entity
class GdcCidItem implements Serializable {
    String code
    String region
    String label
    static mapping = {
        id composite: ['region', 'code']
    }
}

@Entity
class GdcCidSpecial extends GdcCidItem {
    String extra
}

@Entity
class GdcCidOwner {
    String name
}

@Entity
class GdcCidPart implements Serializable {
    GdcCidOwner owner
    String seq
    String label
    static mapping = {
        id composite: ['owner', 'seq']
    }
}

@Entity
class GdcCidRef {
    String name
    GdcCidItem item
}

@Entity
class GdcCidBox implements Serializable {
    String code
    String region
    static hasMany = [things: GdcCidThing]
    static mapping = {
        id composite: ['code', 'region']
    }
}

@Entity
class GdcCidThing {
    String name
    static belongsTo = [box: GdcCidBox]
}

@Entity
class GdcCidSequenced implements Serializable {
    Integer partNumber
    String region
    String label
    static mapping = {
        partNumber generator: 'sequence', params: [sequence_name: 'GDC_CID_SEQ']
        id composite: ['partNumber', 'region']
    }
}
