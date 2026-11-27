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
 * A custom collection type ({@code UserCollectionType}) as the {@code type} of a {@code hasMany}. On a collection of domain classes both
 * bindings boot with the same schema; the domain binder never uses the class, the generated mode applies it. On a collection of basic
 * values the domain binder does not boot.
 */
class GeneratedDomainClassesCustomCollectionTypeSpec extends Specification {

    static final AtomicInteger BOOTS = new AtomicInteger()

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(List<Class> group, boolean generated) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'                  : "jdbc:h2:mem:tns${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'             : 'create-drop',
                'hibernate.generatedDomainClasses': generated,
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

    void "a custom collection type on a collection of entities is a table in both bindings, and only the generated mode puts the custom type to use"() {
        when:
        Map<Boolean, Map> observed = [false, true].collectEntries { boolean generated ->
            TnSetCollectionType.CREATED.set(0)
            TnSetCollectionType.WRAPS.set(0)
            HibernateDatastore booted = boot([TnCollectionOwner, TnCollectionKid], generated)
            Long id = TnCollectionOwner.withTransaction {
                TnCollectionOwner owner = new TnCollectionOwner(name: 'o')
                owner.addToKids(new TnCollectionKid(name: 'a'))
                owner.addToKids(new TnCollectionKid(name: 'b'))
                owner.save(failOnError: true, flush: true).id
            }
            Map result = TnCollectionOwner.withNewSession {
                [kids: TnCollectionOwner.get(id).kids*.name.sort()]
            }
            result.schema = schema(booted)
            result.customTypeUsed = TnSetCollectionType.CREATED.get() > 0 && TnSetCollectionType.WRAPS.get() > 0
            [(generated): result]
        }

        then: "the schema and the data are the same"
        observed[true].schema == observed[false].schema
        observed[true].kids == ['a', 'b']
        observed[false].kids == ['a', 'b']
        observed[true].schema.keySet().containsAll(['tn_collection_owner', 'tn_collection_kid', 'tn_collection_owner_tn_collection_kid'])

        and: "the domain binder names the custom type to Hibernate in a way Hibernate never uses, so it silently keeps its own set; the generated mode applies the type the mapping names"
        !observed[false].customTypeUsed
        observed[true].customTypeUsed
    }

    void "a custom collection type on a collection of basic values is not accepted by the domain binder, and the generated mode says why"() {
        when:
        boot([TnCollectionBasic], false)

        then:
        thrown(Exception)

        when:
        boot([TnCollectionBasic], true)

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
