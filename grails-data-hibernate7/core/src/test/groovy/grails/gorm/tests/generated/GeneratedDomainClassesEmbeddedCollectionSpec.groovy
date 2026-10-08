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
import grails.unbootable.EmbeddedCollectionOwnerA
import grails.unbootable.EmbeddedCollectionOwnerB
import grails.unbootable.EmbeddedCollectionOwnerC
import grails.unbootable.EmbeddedCollectionOwnerD
import org.hibernate.mapping.Table
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.orm.hibernate.HibernateDatastore

/**
 * A collection inside an embedded type, bound by the domain binder and by the generated classes. The domain binder names the table
 * of such a collection after the embedded type and not after the owner ({@code ecs_words_words}), and its key column after the
 * embedded type too ({@code ecs_words_id}), whatever the embedded property is called; the key points at the table of the owner. The
 * generated classes state the same names. Two embedded properties that share a type with a collection would share that one table,
 * which the binder cannot make sense of, so the generated mode rejects them by name.
 */
class GeneratedDomainClassesEmbeddedCollectionSpec extends Specification {

    static final AtomicInteger BOOTS = new AtomicInteger()

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(List<Class> group, boolean generated) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'                  : "jdbc:h2:mem:ecs${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'             : 'create-drop',
                'hibernate.generatedDomainClasses': generated,
        ], group as Class[])
        return datastore
    }

    /** The tables of the boot model as plain data: columns with SQL type and nullability, keys, foreign keys, indexes, unique keys. */
    private Map<String, Map> schema(List<Class> group, boolean generated) {
        HibernateDatastore booted = boot(group, generated)
        Map<String, Map> result = new TreeMap<String, Map>()
        for (Table table : booted.metadata.collectTableMappings()) {
            if (table.physicalTable) {
                result[table.name] = [
                        columns    : table.columns.collectEntries { org.hibernate.mapping.Column column ->
                            boolean nullable = column.nullable && !(table.primaryKey != null && table.primaryKey.columns*.name.contains(column.name))
                            [(column.name): "${column.getSqlType(booted.metadata)}${nullable ? '' : ' not null'}${column.unique ? ' unique' : ''}".toString()]
                        },
                        primaryKey : table.primaryKey?.columns*.name?.sort(),
                        foreignKeys: table.foreignKeys.values().collect { "${it.columns*.name} -> ${it.referencedTable.name}".toString() }.sort(),
                        indexes    : table.indexes.values().collectEntries { [(it.name): it.columns*.name] },
                        uniqueKeys : table.uniqueKeys.values().collect { it.columns*.name.sort() }.sort { it.toString() },
                ]
            }
        }
        return result
    }

    @Unroll
    void "#label: the generated mode creates the schema of the domain binder"() {
        when:
        Map<String, Map> binder = schema(group, false)
        Map<String, Map> generated = schema(group, true)

        then:
        generated == binder
        generated.keySet() == tables as Set
        generated[table].columns.keySet() == columns as Set

        where:
        label                                  | group                      | tables                                                      | table                | columns
        'a set of strings'                     | [EcsSetOwner]              | ['ecs_set_owner', 'ecs_words_words']                        | 'ecs_words_words'    | ['ecs_words_id', 'words_java_lang_string']
        'a set beside simple properties'       | [EcsMixedOwner]            | ['ecs_mixed_owner', 'ecs_mixed_words_words']                | 'ecs_mixed_words_words' | ['ecs_mixed_words_id', 'words_java_lang_string']
        'a set of enums'                       | [EcsEnumOwner]             | ['ecs_enum_owner', 'ecs_enums_colors']                      | 'ecs_enums_colors'   | ['ecs_enums_id', 'ecs_color']
        'a set mapped with a join table'       | [EcsNamedOwner]            | ['ecs_named_owner', 'ecs_named_words']                      | 'ecs_named_words'    | ['owner_fk', 'word_col']
        'a set in a nested embedded type'      | [EcsNestedOwner]           | ['ecs_nested_owner', 'ecs_deep_tags']                       | 'ecs_deep_tags'      | ['ecs_deep_id', 'tags_java_lang_string']
        'a set of entities'                    | [EcsEntityOwner, EcsItem]  | ['ecs_entity_owner', 'ecs_item', 'ecs_entities_ecs_item']   | 'ecs_entities_ecs_item' | ['ecs_entities_items_id', 'ecs_item_id']
        'a set of entities in a nested type'   | [EcsDeepEntityOwner, EcsItem] | ['ecs_deep_entity_owner', 'ecs_item', 'ecs_deep_entities_ecs_item'] | 'ecs_deep_entities_ecs_item' | ['ecs_deep_entities_items_id', 'ecs_item_id']
    }

    @Unroll
    void "#label: the schema differs from the domain binder's only as the schema of a collection of the owner itself does"() {
        when:
        Map<String, Map> binder = schema(group, false)
        Map<String, Map> generated = schema(group, true)
        Map<String, Map> plainBinder = schema([plain], false)
        Map<String, Map> plainGenerated = schema([plain], true)

        then: "the same tables, the same columns apart from the one the binder leaves unused, the same keys"
        generated.keySet() == binder.keySet()
        generated[table].columns.keySet() == binder[table].columns.keySet() - unused
        generated[table].primaryKey == binder[table].primaryKey
        generated[table].foreignKeys == binder[table].foreignKeys
        generated[table].indexes == binder[table].indexes
        generated[table].uniqueKeys == binder[table].uniqueKeys

        and: "the types differ exactly where they differ for a collection that is a property of the owner"
        shape(binder[table].columns, generated[table].columns) == shape(plainBinder[plainTable].columns, plainGenerated[plainTable].columns)

        where:
        label               | group          | table              | unused                       | plain        | plainTable
        'a list of strings' | [EcsListOwner] | 'ecs_lists_items'  | []                           | EcsPlainList | 'ecs_plain_list_items'
        'a map of strings'  | [EcsMapOwner]  | 'ecs_maps_by_name' | ['by_name_java_lang_string'] | EcsPlainMap  | 'ecs_plain_map_by_name'
    }

    private static List shape(Map<String, String> binder, Map<String, String> generated) {
        return (binder.keySet() + generated.keySet()).collect { String name -> [binder[name], generated[name]] }
                .findAll { List pair -> pair[0] != pair[1] }.sort { it.toString() }
    }

    @Unroll
    void "#label are stored, read back, changed and deleted with the owner, the same in both modes"() {
        when:
        Map<Boolean, List> results = [false, true].collectEntries { boolean generated ->
            boot(group, generated)
            [(generated): this."${cycle}"()]
        }

        then:
        results[true] == results[false]
        results[true] == expected

        where:
        label                                | group                         | cycle              | expected
        'the words of an embedded set'       | [EcsSetOwner]                 | 'setCycle'         | [['a', 'b'], ['a', 'c'], 0]
        'the elements of an embedded list'   | [EcsListOwner]                | 'listCycle'        | [['x', 'y', 'x'], ['y', 'z'], 0]
        'the entries of an embedded map'     | [EcsMapOwner]                 | 'mapCycle'         | [[one: 'a', two: 'b'], [two: 'b', three: 'c'], 0]
        'the colors of an embedded set'      | [EcsEnumOwner]                | 'enumCycle'        | [['GREEN', 'RED'], ['GREEN'], 0]
        'the items of an embedded set'       | [EcsEntityOwner, EcsItem]     | 'entityCycle'      | [['one', 'two'], ['three', 'two'], 0]
        'the items of a nested embedded set' | [EcsDeepEntityOwner, EcsItem] | 'deepEntityCycle'  | [['one', 'two'], ['one', 'three', 'two'], 0]
        'the tags of a nested embedded set'  | [EcsNestedOwner]              | 'nestedCycle'      | [['t1', 't2'], ['t2', 't3'], 0]
    }

    private List setCycle() {
        Long id = EcsSetOwner.withTransaction { new EcsSetOwner(inner: new EcsWords(words: ['a', 'b'] as Set)).save(failOnError: true, flush: true).id }
        List first = EcsSetOwner.withNewSession { EcsSetOwner.get(id).inner.words.sort() }
        EcsSetOwner.withTransaction { EcsSetOwner owner = EcsSetOwner.get(id); owner.inner.words = ['a', 'c'] as Set; owner.save(failOnError: true, flush: true) }
        List second = EcsSetOwner.withNewSession { EcsSetOwner.get(id).inner.words.sort() }
        EcsSetOwner.withTransaction { EcsSetOwner.get(id).delete(flush: true) }
        return [first, second, countRows('ecs_words_words')]
    }

    private List listCycle() {
        Long id = EcsListOwner.withTransaction { new EcsListOwner(inner: new EcsLists(items: ['x', 'y', 'x'])).save(failOnError: true, flush: true).id }
        List first = EcsListOwner.withNewSession { new ArrayList(EcsListOwner.get(id).inner.items) }
        EcsListOwner.withTransaction { EcsListOwner owner = EcsListOwner.get(id); owner.inner.items = ['y', 'z']; owner.save(failOnError: true, flush: true) }
        List second = EcsListOwner.withNewSession { new ArrayList(EcsListOwner.get(id).inner.items) }
        EcsListOwner.withTransaction { EcsListOwner.get(id).delete(flush: true) }
        return [first, second, countRows('ecs_lists_items')]
    }

    private List mapCycle() {
        Long id = EcsMapOwner.withTransaction { new EcsMapOwner(inner: new EcsMaps(byName: [one: 'a', two: 'b'])).save(failOnError: true, flush: true).id }
        Map first = EcsMapOwner.withNewSession { new TreeMap(EcsMapOwner.get(id).inner.byName) }
        EcsMapOwner.withTransaction { EcsMapOwner owner = EcsMapOwner.get(id); owner.inner.byName = [two: 'b', three: 'c']; owner.save(failOnError: true, flush: true) }
        Map second = EcsMapOwner.withNewSession { new TreeMap(EcsMapOwner.get(id).inner.byName) }
        EcsMapOwner.withTransaction { EcsMapOwner.get(id).delete(flush: true) }
        return [first, second, countRows('ecs_maps_by_name')]
    }

    private List enumCycle() {
        Long id = EcsEnumOwner.withTransaction { new EcsEnumOwner(inner: new EcsEnums(colors: [EcsColor.RED, EcsColor.GREEN] as Set)).save(failOnError: true, flush: true).id }
        List first = EcsEnumOwner.withNewSession { EcsEnumOwner.get(id).inner.colors*.name().sort() }
        EcsEnumOwner.withTransaction { EcsEnumOwner owner = EcsEnumOwner.get(id); owner.inner.colors = [EcsColor.GREEN] as Set; owner.save(failOnError: true, flush: true) }
        List second = EcsEnumOwner.withNewSession { EcsEnumOwner.get(id).inner.colors*.name().sort() }
        EcsEnumOwner.withTransaction { EcsEnumOwner.get(id).delete(flush: true) }
        return [first, second, countRows('ecs_enums_colors')]
    }

    private List entityCycle() {
        Long id = EcsEntityOwner.withTransaction {
            EcsItem one = new EcsItem(name: 'one').save(failOnError: true)
            EcsItem two = new EcsItem(name: 'two').save(failOnError: true)
            new EcsEntityOwner(inner: new EcsEntities(items: [one, two] as Set)).save(failOnError: true, flush: true).id
        }
        List first = EcsEntityOwner.withNewSession { EcsEntityOwner.get(id).inner.items*.name.sort() }
        EcsEntityOwner.withTransaction {
            EcsItem three = new EcsItem(name: 'three').save(failOnError: true)
            EcsEntityOwner owner = EcsEntityOwner.get(id)
            owner.inner.items = [EcsItem.findByName('two'), three] as Set
            owner.save(failOnError: true, flush: true)
        }
        List second = EcsEntityOwner.withNewSession { EcsEntityOwner.get(id).inner.items*.name.sort() }
        EcsEntityOwner.withTransaction { EcsEntityOwner.get(id).delete(flush: true) }
        return [first, second, countRows('ecs_entities_ecs_item')]
    }

    private List deepEntityCycle() {
        Long id = EcsDeepEntityOwner.withTransaction {
            EcsItem one = new EcsItem(name: 'one').save(failOnError: true)
            EcsItem two = new EcsItem(name: 'two').save(failOnError: true)
            new EcsDeepEntityOwner(mid: new EcsMidEntities(label: 'm', deep: new EcsDeepEntities(items: [one, two] as Set))).save(failOnError: true, flush: true).id
        }
        List first = EcsDeepEntityOwner.withNewSession { EcsDeepEntityOwner.get(id).mid.deep.items*.name.sort() }
        EcsDeepEntityOwner.withTransaction {
            EcsDeepEntityOwner owner = EcsDeepEntityOwner.get(id)
            owner.mid.deep.items.add(new EcsItem(name: 'three').save(failOnError: true))
            owner.save(failOnError: true, flush: true)
        }
        List second = EcsDeepEntityOwner.withNewSession { EcsDeepEntityOwner.get(id).mid.deep.items*.name.sort() }
        EcsDeepEntityOwner.withTransaction { EcsDeepEntityOwner.get(id).delete(flush: true) }
        return [first, second, countRows('ecs_deep_entities_ecs_item')]
    }

    private List nestedCycle() {
        Long id = EcsNestedOwner.withTransaction { new EcsNestedOwner(mid: new EcsMid(x: 'm', deep: new EcsDeep(tags: ['t1', 't2'] as Set))).save(failOnError: true, flush: true).id }
        List first = EcsNestedOwner.withNewSession { EcsNestedOwner.get(id).mid.deep.tags.sort() }
        EcsNestedOwner.withTransaction { EcsNestedOwner owner = EcsNestedOwner.get(id); owner.mid.deep.tags.remove('t1'); owner.mid.deep.tags.add('t3'); owner.save(failOnError: true, flush: true) }
        List second = EcsNestedOwner.withNewSession { EcsNestedOwner.get(id).mid.deep.tags.sort() }
        EcsNestedOwner.withTransaction { EcsNestedOwner.get(id).delete(flush: true) }
        return [first, second, countRows('ecs_deep_tags')]
    }

    private int countRows(String table) {
        datastore.connectionSources.defaultConnectionSource.dataSource.connection.withCloseable { java.sql.Connection connection ->
            connection.createStatement().executeQuery("select count(*) from ${table}".toString()).with { java.sql.ResultSet rows -> rows.next(); rows.getInt(1) }
        }
    }

    void "the words of an embedded set are found by a query on the owner in both modes"() {
        when:
        List<List> results = [false, true].collect { boolean generated ->
            boot([EcsSetOwner], generated)
            EcsSetOwner.withTransaction {
                new EcsSetOwner(inner: new EcsWords(words: ['red', 'blue'] as Set)).save(failOnError: true)
                new EcsSetOwner(inner: new EcsWords(words: ['green'] as Set)).save(failOnError: true, flush: true)
            }
            EcsSetOwner.withNewSession {
                [EcsSetOwner.count(), EcsSetOwner.list()*.inner.words.flatten().sort(), EcsSetOwner.executeQuery('select count(o) from EcsSetOwner o where o.id > 0')[0]]
            }
        }

        then:
        results[1] == results[0]
        results[1] == [2, ['blue', 'green', 'red'], 2]
    }

    void "a type with a collection that two embedded properties share is rejected by the generated mode and says why"() {
        when: "the domain binder cannot boot owners that embed the type under the same name"
        boot([EmbeddedCollectionOwnerA, EmbeddedCollectionOwnerB], false)

        then:
        thrown(Exception)

        when:
        boot([EmbeddedCollectionOwnerA, EmbeddedCollectionOwnerB], true)

        then:
        Exception e = thrown()
        rootOf(e) instanceof UnsupportedOperationException
        rootOf(e).message.contains('is reachable through 2 embedded properties')
        rootOf(e).message.contains('EmbeddedCollectionOwnerA.inner')
        rootOf(e).message.contains('EmbeddedCollectionOwnerB.inner')
    }

    void "under different property names the domain binder boots but keeps one table with a key to the first owner, which the generated mode does not copy"() {
        when:
        Map<String, Map> binder = schema([EmbeddedCollectionOwnerC, EmbeddedCollectionOwnerD], false)

        then: "one collection table, a foreign key to one of the owners"
        binder.keySet().count { it.contains('words') } == 1
        binder.values().find { it.columns.containsKey('embedded_collection_holder_id') }.foreignKeys.size() == 1

        when: "the second owner stores a collection"
        EmbeddedCollectionOwnerD.withTransaction {
            new EmbeddedCollectionOwnerD(second: new grails.unbootable.EmbeddedCollectionHolder(words: ['w'] as Set)).save(failOnError: true, flush: true)
        }

        then: "its row violates the foreign key to the other owner"
        thrown(Exception)

        when:
        boot([EmbeddedCollectionOwnerC, EmbeddedCollectionOwnerD], true)

        then:
        Exception e = thrown()
        rootOf(e) instanceof UnsupportedOperationException
        rootOf(e).message.contains('EmbeddedCollectionOwnerC.first')
        rootOf(e).message.contains('EmbeddedCollectionOwnerD.second')
    }

    private static Throwable rootOf(Throwable e) {
        Throwable root = e
        while (root.cause != null && root.cause != root) {
            root = root.cause
        }
        return root
    }
}

enum EcsColor { RED, GREEN }

class EcsWords {
    Set<String> words
    static hasMany = [words: String]
}

@Entity
class EcsSetOwner {
    EcsWords inner
    static embedded = ['inner']
}

class EcsMixedWords {
    String label
    Set<String> words
    static hasMany = [words: String]
}

@Entity
class EcsMixedOwner {
    String name
    EcsMixedWords inner
    static embedded = ['inner']
}

class EcsEnums {
    Set<EcsColor> colors
    static hasMany = [colors: EcsColor]
}

@Entity
class EcsEnumOwner {
    EcsEnums inner
    static embedded = ['inner']
}

class EcsNamed {
    Set<String> words
    static hasMany = [words: String]
    static mapping = {
        words joinTable: [name: 'ecs_named_words', key: 'owner_fk', column: 'word_col']
    }
}

@Entity
class EcsNamedOwner {
    EcsNamed inner
    static embedded = ['inner']
}

class EcsDeep {
    Set<String> tags
    static hasMany = [tags: String]
}

class EcsMid {
    String x
    EcsDeep deep
    static embedded = ['deep']
}

@Entity
class EcsNestedOwner {
    EcsMid mid
    static embedded = ['mid']
}

class EcsLists {
    List<String> items
    static hasMany = [items: String]
}

@Entity
class EcsListOwner {
    EcsLists inner
    static embedded = ['inner']
}

class EcsMaps {
    Map<String, String> byName
    static hasMany = [byName: String]
}

@Entity
class EcsMapOwner {
    EcsMaps inner
    static embedded = ['inner']
}

@Entity
class EcsPlainList {
    List<String> items
    static hasMany = [items: String]
}

@Entity
class EcsPlainMap {
    Map<String, String> byName
    static hasMany = [byName: String]
}

@Entity
class EcsItem {
    String name
}

class EcsEntities {
    Set<EcsItem> items
    static hasMany = [items: EcsItem]
}

@Entity
class EcsEntityOwner {
    EcsEntities inner
    static embedded = ['inner']
}

class EcsDeepEntities {
    Set<EcsItem> items
    static hasMany = [items: EcsItem]
}

class EcsMidEntities {
    String label
    EcsDeepEntities deep
    static embedded = ['deep']
}

@Entity
class EcsDeepEntityOwner {
    EcsMidEntities mid
    static embedded = ['mid']
}
