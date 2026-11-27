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

import java.util.concurrent.atomic.AtomicInteger

import grails.gorm.annotation.Entity
import org.hibernate.collection.spi.PersistentCollection
import org.hibernate.collection.spi.PersistentSet
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.mapping.Table
import org.hibernate.metamodel.CollectionClassification
import org.hibernate.persister.collection.CollectionPersister
import org.hibernate.usertype.UserCollectionType
import spock.lang.AutoCleanup
import spock.lang.Specification

import org.grails.orm.hibernate.HibernateDatastore

/**
 * A custom collection type ({@code UserCollectionType}) as the {@code type} of a {@code hasMany}. On a collection of domain classes the
 * schema is the one the classic binding of Grails 8 created, and the type is applied (classic binding named it to Hibernate in a way
 * Hibernate never used). On a collection of basic values the mapping is refused by name, as classic binding did not boot it.
 */
class GeneratedDomainClassesCustomCollectionTypeSpec extends Specification {

    static final AtomicInteger BOOTS = new AtomicInteger()

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(List<Class> group) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'     : "jdbc:h2:mem:tns${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate': 'create-drop',
        ], group as Class[])
        return datastore
    }

    /** The tables of the boot model as plain data: columns with SQL type and nullability, indexes, unique keys. */
    private Map<String, Map> schema(HibernateDatastore booted) {
        Map<String, Map> result = new TreeMap<String, Map>()
        for (Table table : booted.metadata.collectTableMappings()) {
            if (table.physicalTable) {
                result[table.name] = [
                        columns   : table.columns.collectEntries { org.hibernate.mapping.Column column ->
                            boolean nullable = column.nullable && !(table.primaryKey != null && table.primaryKey.columns*.name.contains(column.name))
                            [(column.name): "${column.getSqlType(booted.metadata)}${nullable ? '' : ' not null'}${column.unique ? ' unique' : ''}".toString()]
                        },
                        primaryKey: table.primaryKey?.columns*.name?.sort(),
                        indexes   : table.indexes.values().collectEntries { [(it.name): it.columns*.name] },
                        uniqueKeys: table.uniqueKeys.values().collect { it.columns*.name.sort() }.sort { it.toString() },
                ]
            }
        }
        return result
    }

    void "a custom collection type on a collection of entities is a join table, and the custom type is put to use"() {
        when:
        TnSetCollectionType.CREATED.set(0)
        TnSetCollectionType.WRAPS.set(0)
        HibernateDatastore booted = boot([TnCollectionOwner, TnCollectionKid])
        Long id = TnCollectionOwner.withTransaction {
            TnCollectionOwner owner = new TnCollectionOwner(name: 'o')
            owner.addToKids(new TnCollectionKid(name: 'a'))
            owner.addToKids(new TnCollectionKid(name: 'b'))
            owner.save(failOnError: true, flush: true).id
        }
        List<String> kids = TnCollectionOwner.withNewSession { TnCollectionOwner.get(id).kids*.name.sort() }

        then: "the schema is the one classic binding created, and the data is read back"
        schema(booted).keySet() == ['tn_collection_owner', 'tn_collection_kid', 'tn_collection_owner_tn_collection_kid'] as Set
        kids == ['a', 'b']

        and: "the type the mapping names is applied (classic binding named it to Hibernate in a way Hibernate never used)"
        TnSetCollectionType.CREATED.get() > 0
        TnSetCollectionType.WRAPS.get() > 0
    }

    void "a custom collection type on a collection of basic values is refused by name, as classic binding did not boot it"() {
        when:
        boot([TnCollectionBasic])

        then:
        Exception e = thrown()
        Throwable root = e
        while (root.cause != null && root.cause != root) {
            root = root.cause
        }
        root instanceof UnsupportedOperationException
        root.message.contains('a custom collection type')
    }
}

class TnSetCollectionType implements UserCollectionType {

    static final AtomicInteger WRAPS = new AtomicInteger()
    static final AtomicInteger CREATED = new AtomicInteger()
    static final AtomicInteger INSTANTIATED = new AtomicInteger()

    TnSetCollectionType() {
        CREATED.incrementAndGet()
    }

    CollectionClassification getClassification() { CollectionClassification.SET }

    Class<?> getCollectionClass() { Set }

    PersistentCollection<?> instantiate(SharedSessionContractImplementor session, CollectionPersister persister) {
        INSTANTIATED.incrementAndGet()
        new PersistentSet(session)
    }

    PersistentCollection<?> wrap(SharedSessionContractImplementor session, Object collection) {
        WRAPS.incrementAndGet()
        new PersistentSet(session, (Set) collection)
    }

    Iterator<?> getElementsIterator(Object collection) { ((Set) collection).iterator() }

    boolean contains(Object collection, Object entity) { ((Set) collection).contains(entity) }

    Object indexOf(Object collection, Object entity) { null }

    Object replaceElements(Object original, Object target, CollectionPersister persister, Object owner, Map copyCache,
                           SharedSessionContractImplementor session) {
        Set targetSet = (Set) target
        targetSet.clear()
        targetSet.addAll((Set) original)
        targetSet
    }

    Object instantiate(int anticipatedSize) { new HashSet() }
}

@Entity
class TnCollectionKid {
    String name
}

@Entity
class TnCollectionOwner {
    String name
    Set<TnCollectionKid> kids
    static hasMany = [kids: TnCollectionKid]
    static mapping = { kids type: TnSetCollectionType }
}

@Entity
class TnCollectionBasic {
    Set<String> tags
    static hasMany = [tags: String]
    static mapping = { tags type: TnSetCollectionType }
}
