/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package grails.gorm.tests.generated

import java.util.concurrent.atomic.AtomicInteger

import grails.unbootable.EmbeddedCollectionHolder
import grails.unbootable.EmbeddedCollectionItem
import grails.unbootable.EmbeddedCollectionOwnerA
import grails.unbootable.EmbeddedCollectionOwnerB
import grails.unbootable.EmbeddedCollectionOwnerC
import grails.unbootable.EmbeddedCollectionOwnerD
import grails.unbootable.EmbeddedCollectionOwnerTwice
import grails.unbootable.EmbeddedItemsHolder
import grails.unbootable.EmbeddedItemsOwnerA
import grails.unbootable.EmbeddedItemsOwnerB
import grails.unbootable.EmbeddedMiddle
import grails.unbootable.EmbeddedNestedOwnerA
import grails.unbootable.EmbeddedNestedOwnerB
import org.hibernate.mapping.Table
import spock.lang.AutoCleanup
import spock.lang.Specification

import org.grails.orm.hibernate.HibernateDatastore

/**
 * A collection inside an embedded type that more than one embedded property reaches. The classic binding of Grails 8 named its table
 * and its key column after the embedded type, so all the owners shared one table whose key was a foreign key to the owner that was
 * bound first (the rows of any other owner violated it), and it failed to boot when the properties were named alike. Native binding
 * gives every owner a table of its own: the first owner, in the order of the entity name and then the property path, keeps the names
 * classic binding used, the others get names qualified with their owner and the path of their embedded property.
 */
class GeneratedDomainClassesSharedEmbeddedCollectionSpec extends Specification {

    static final AtomicInteger BOOTS = new AtomicInteger()

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(List<Class> group) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'     : "jdbc:h2:mem:gse${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate': 'create-drop',
        ], group as Class[])
        return datastore
    }

    /** The tables of the boot model with their columns and the table each foreign key points at. */
    private Map<String, Map> schema(List<Class> group) {
        HibernateDatastore booted = boot(group)
        Map<String, Map> result = new TreeMap<String, Map>()
        for (Table table : booted.metadata.collectTableMappings()) {
            if (table.physicalTable) {
                result[table.name] = [
                        columns    : table.columns*.name.sort(),
                        foreignKeys: table.foreignKeys.values().collect { "${it.columns*.name} -> ${it.referencedTable.name}".toString() }.sort(),
                ]
            }
        }
        return result
    }

    private int countRows(String table) {
        datastore.connectionSources.defaultConnectionSource.dataSource.connection.withCloseable { java.sql.Connection connection ->
            connection.createStatement().executeQuery("select count(*) from ${table}".toString()).with { java.sql.ResultSet rows -> rows.next(); rows.getInt(1) }
        }
    }

    void "the first owner keeps the table and the key column classic binding used, the other one gets names qualified with its owner and its property"() {
        when:
        Map<String, Map> generated = schema([EmbeddedCollectionOwnerC, EmbeddedCollectionOwnerD])

        then:
        generated.keySet() == ['embedded_collection_holder_words', 'embedded_collection_ownerc', 'embedded_collection_ownerd',
                               'embedded_collection_ownerd_second_words'] as Set
        generated['embedded_collection_holder_words'].columns == ['embedded_collection_holder_id', 'words_java_lang_string']
        generated['embedded_collection_holder_words'].foreignKeys == ['[embedded_collection_holder_id] -> embedded_collection_ownerc']
        generated['embedded_collection_ownerd_second_words'].columns == ['embedded_collection_ownerd_second_id', 'words_java_lang_string']
        generated['embedded_collection_ownerd_second_words'].foreignKeys == ['[embedded_collection_ownerd_second_id] -> embedded_collection_ownerd']
    }

    void "the owners are told apart by the name of the entity whatever the order they are given in"() {
        expect:
        schema([EmbeddedCollectionOwnerD, EmbeddedCollectionOwnerC]) == schema([EmbeddedCollectionOwnerC, EmbeddedCollectionOwnerD])
    }

    void "a type that only one owner embeds keeps exactly the schema classic binding created"() {
        expect:
        schema([EmbeddedCollectionOwnerC]) == [
                embedded_collection_holder_words: [columns: ['embedded_collection_holder_id', 'words_java_lang_string'],
                                                   foreignKeys: ['[embedded_collection_holder_id] -> embedded_collection_ownerc']],
                embedded_collection_ownerc      : [columns: ['id', 'version'], foreignKeys: []],
        ]
    }

    void "each owner stores, reads back and deletes its own collection"() {
        given:
        boot([EmbeddedCollectionOwnerC, EmbeddedCollectionOwnerD])

        when:
        Long c = EmbeddedCollectionOwnerC.withTransaction {
            new EmbeddedCollectionOwnerC(first: new EmbeddedCollectionHolder(words: ['a', 'b'] as Set)).save(failOnError: true, flush: true).id
        }
        Long d = EmbeddedCollectionOwnerD.withTransaction {
            new EmbeddedCollectionOwnerD(second: new EmbeddedCollectionHolder(words: ['x'] as Set)).save(failOnError: true, flush: true).id
        }

        then:
        EmbeddedCollectionOwnerC.withNewSession { EmbeddedCollectionOwnerC.get(c).first.words.sort() } == ['a', 'b']
        EmbeddedCollectionOwnerD.withNewSession { EmbeddedCollectionOwnerD.get(d).second.words.sort() } == ['x']
        countRows('embedded_collection_holder_words') == 2
        countRows('embedded_collection_ownerd_second_words') == 1

        when:
        EmbeddedCollectionOwnerD.withTransaction {
            EmbeddedCollectionOwnerD owner = EmbeddedCollectionOwnerD.get(d)
            owner.second.words = ['y', 'z'] as Set
            owner.save(failOnError: true, flush: true)
        }

        then:
        EmbeddedCollectionOwnerD.withNewSession { EmbeddedCollectionOwnerD.get(d).second.words.sort() } == ['y', 'z']
        EmbeddedCollectionOwnerC.withNewSession { EmbeddedCollectionOwnerC.get(c).first.words.sort() } == ['a', 'b']

        when:
        EmbeddedCollectionOwnerD.withTransaction { EmbeddedCollectionOwnerD.get(d).delete(flush: true) }

        then:
        countRows('embedded_collection_ownerd_second_words') == 0
        countRows('embedded_collection_holder_words') == 2
    }

    void "owners that embed the type under the same name boot with qualified names for all but the first, and store their own collections"() {
        when:
        Map<String, Map> generated = schema([EmbeddedCollectionOwnerA, EmbeddedCollectionOwnerB])

        then:
        generated.keySet() == ['embedded_collection_holder_words', 'embedded_collection_ownera', 'embedded_collection_ownerb',
                               'embedded_collection_ownerb_inner_words'] as Set
        generated['embedded_collection_holder_words'].foreignKeys == ['[embedded_collection_holder_id] -> embedded_collection_ownera']
        generated['embedded_collection_ownerb_inner_words'].foreignKeys == ['[embedded_collection_ownerb_inner_id] -> embedded_collection_ownerb']

        when:
        Long a = EmbeddedCollectionOwnerA.withTransaction {
            new EmbeddedCollectionOwnerA(inner: new EmbeddedCollectionHolder(words: ['a1'] as Set)).save(failOnError: true, flush: true).id
        }
        Long b = EmbeddedCollectionOwnerB.withTransaction {
            new EmbeddedCollectionOwnerB(inner: new EmbeddedCollectionHolder(words: ['b1', 'b2'] as Set)).save(failOnError: true, flush: true).id
        }

        then:
        EmbeddedCollectionOwnerA.withNewSession { EmbeddedCollectionOwnerA.get(a).inner.words.sort() } == ['a1']
        EmbeddedCollectionOwnerB.withNewSession { EmbeddedCollectionOwnerB.get(b).inner.words.sort() } == ['b1', 'b2']
    }

    void "one owner that embeds the type twice has a table for each property (classic binding mixed the elements of both in one table)"() {
        when:
        Map<String, Map> generated = schema([EmbeddedCollectionOwnerTwice])
        Long id = EmbeddedCollectionOwnerTwice.withTransaction {
            new EmbeddedCollectionOwnerTwice(
                    home: new EmbeddedCollectionHolder(words: ['h'] as Set),
                    work: new EmbeddedCollectionHolder(words: ['w'] as Set)).save(failOnError: true, flush: true).id
        }

        then:
        generated.keySet() == ['embedded_collection_holder_words', 'embedded_collection_owner_twice', 'embedded_collection_owner_twice_work_words'] as Set
        EmbeddedCollectionOwnerTwice.withNewSession {
            EmbeddedCollectionOwnerTwice owner = EmbeddedCollectionOwnerTwice.get(id)
            [owner.home.words.sort(), owner.work.words.sort()]
        } == [['h'], ['w']]
    }

    void "a collection of entities and a list inside an embedded type get a table of their own for each owner"() {
        when:
        Map<String, Map> generated = schema([EmbeddedItemsOwnerA, EmbeddedItemsOwnerB, EmbeddedCollectionItem])

        then:
        generated.keySet() == [
                'embedded_collection_item', 'embedded_items_holder_embedded_collection_item', 'embedded_items_holder_order',
                'embedded_items_ownera', 'embedded_items_ownerb', 'embedded_items_ownerb_theirs_items',
                'embedded_items_ownerb_theirs_order'] as Set
        generated['embedded_items_ownerb_theirs_items'].foreignKeys.size() == 2
        generated['embedded_items_ownerb_theirs_items'].foreignKeys.contains('[embedded_items_ownerb_theirs_id] -> embedded_items_ownerb')

        when:
        Long a = EmbeddedItemsOwnerA.withTransaction {
            EmbeddedCollectionItem one = new EmbeddedCollectionItem(name: 'one').save(failOnError: true)
            new EmbeddedItemsOwnerA(mine: new EmbeddedItemsHolder(items: [one] as Set, order: ['x', 'y'])).save(failOnError: true, flush: true).id
        }
        Long b = EmbeddedItemsOwnerB.withTransaction {
            EmbeddedCollectionItem two = new EmbeddedCollectionItem(name: 'two').save(failOnError: true)
            new EmbeddedItemsOwnerB(theirs: new EmbeddedItemsHolder(items: [two] as Set, order: ['z'])).save(failOnError: true, flush: true).id
        }

        then:
        EmbeddedItemsOwnerA.withNewSession { EmbeddedItemsHolder h = EmbeddedItemsOwnerA.get(a).mine; [h.items*.name, new ArrayList(h.order)] } == [['one'], ['x', 'y']]
        EmbeddedItemsOwnerB.withNewSession { EmbeddedItemsHolder h = EmbeddedItemsOwnerB.get(b).theirs; [h.items*.name, new ArrayList(h.order)] } == [['two'], ['z']]
    }

    void "a collection in a nested embedded type and one in a direct embedded property each get a table of their own"() {
        when:
        Map<String, Map> generated = schema([EmbeddedNestedOwnerA, EmbeddedNestedOwnerB])

        then:
        generated.keySet() == [
                'embedded_collection_holder_words', 'embedded_nested_ownera', 'embedded_nested_ownerb',
                'embedded_nested_ownerb_direct_words', 'embedded_nested_ownerb_mid_deep_words'] as Set

        when:
        Long a = EmbeddedNestedOwnerA.withTransaction {
            new EmbeddedNestedOwnerA(mid: new EmbeddedMiddle(label: 'a', deep: new EmbeddedCollectionHolder(words: ['a'] as Set))).save(failOnError: true, flush: true).id
        }
        Long b = EmbeddedNestedOwnerB.withTransaction {
            new EmbeddedNestedOwnerB(
                    mid: new EmbeddedMiddle(label: 'b', deep: new EmbeddedCollectionHolder(words: ['b-deep'] as Set)),
                    direct: new EmbeddedCollectionHolder(words: ['b-direct'] as Set)).save(failOnError: true, flush: true).id
        }

        then:
        EmbeddedNestedOwnerA.withNewSession { EmbeddedNestedOwnerA.get(a).mid.deep.words.sort() } == ['a']
        EmbeddedNestedOwnerB.withNewSession {
            EmbeddedNestedOwnerB owner = EmbeddedNestedOwnerB.get(b)
            [owner.mid.deep.words.sort(), owner.direct.words.sort()]
        } == [['b-deep'], ['b-direct']]
    }
}
