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

import grails.gorm.annotation.Entity
import org.hibernate.mapping.Table
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.orm.hibernate.HibernateDatastore

/**
 * Many-to-many associations and maps of entities that involve an entity with a composite identifier, and foreign keys to a composite
 * identifier that the mapping gives fewer columns than the identifier has properties. The classic binding of Grails 8 booted them: it
 * gave the join table a key column for each identifier property of the owner and an element column for each one of the target, named
 * the columns the mapping states in order and named the missing ones as it would have named all of them. Native binding describes the
 * same tables and columns, stated here, instead of refusing the mapping.
 */
class GeneratedDomainClassesCompositeCollectionSpec extends Specification {

    static final AtomicInteger BOOTS = new AtomicInteger()

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(List<Class> group) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'     : "jdbc:h2:mem:gcc${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate': 'create-drop',
        ], group as Class[])
        return datastore
    }

    /** The tables of the boot model: columns with type and nullability (a primary key column is not null), primary key and foreign keys. */
    private Map<String, Map> schema(List<Class> group) {
        HibernateDatastore booted = boot(group)
        Map<String, Map> result = new TreeMap<String, Map>()
        for (Table table : booted.metadata.collectTableMappings()) {
            if (table.physicalTable) {
                List<String> key = table.primaryKey?.columns*.name ?: []
                result[table.name] = [
                        columns    : table.columns.collectEntries { org.hibernate.mapping.Column column ->
                            [(column.name): "${column.getSqlType(booted.metadata)}${column.nullable && !key.contains(column.name) ? '' : ' not null'}".toString()]
                        },
                        primaryKey : key.sort(),
                        foreignKeys: table.foreignKeys.values().collect { "${it.columns*.name} -> ${it.referencedTable.name}".toString() }.sort(),
                ]
            }
        }
        return result
    }

    @Unroll
    void "#label: the tables and columns of classic binding are created"() {
        when:
        Map<String, Map> generated = schema(group)

        then:
        generated[table].columns.keySet() == columns as Set

        where:
        label                                            | group                          | table                  | columns
        'a many-to-many owned by a composite identifier' | [GccCompOwner, GccOther]       | 'gcc_comp_owner_others' | ['gcc_other_id', 'gcc_comp_owner_a', 'gcc_comp_owner_b']
        'a many-to-many to a composite identifier'       | [GccSimple, GccCompTarget]     | 'gcc_simple_comps'     | ['gcc_simple_id', 'gcc_comp_target_a', 'gcc_comp_target_b']
        'a many-to-many between composite identifiers'   | [GccLeft, GccRight]            | 'gcc_left_rights'      | ['gcc_left_a', 'gcc_left_b', 'gcc_right_x', 'gcc_right_y']
        'a map of entities on both sides'                | [GccMapLeft, GccMapRight]      | 'gcc_map_left_rights'  | ['gcc_map_left_id', 'rights_idx', 'gcc_map_right_a', 'gcc_map_right_b']
        'a map of entities of a composite owner'         | [GccMapOwner, GccItem]         | 'gcc_map_owner_items'  | ['gcc_item_id', 'gcc_map_owner_a', 'gcc_map_owner_b', 'items_idx']
        'a many-to-many with the key columns mapped'     | [GccMappedLeft, GccMappedRight] | 'gcc_mapped_left_rights' | ['k1', 'k2', 'gcc_mapped_right_x', 'gcc_mapped_right_y']
        'a foreign key mapped with fewer columns than the key' | [GccPartialRef, GccPartialKey] | 'gcc_partial_ref' | ['id', 'version', 'only_one', 'gcc_partial_key_b']
        'a collection mapped with one column of two'     | [GccPartialTags]               | 'gcc_partial_tags_tags' | ['k1', 'gcc_partial_tags_b', 'tags_java_lang_string']
    }

    @Unroll
    void "#label: the rows are stored, read back, changed and deleted"() {
        when:
        boot(group)
        List results = this."${cycle}"()

        then:
        results == expected

        where:
        label                                            | group                          | cycle               | expected
        'a many-to-many owned by a composite identifier' | [GccCompOwner, GccOther]       | 'ownedCycle'        | [['o1', 'o2'], ['o2', 'o3'], ['o2', 'o3']]
        'a many-to-many to a composite identifier'       | [GccSimple, GccCompTarget]     | 'targetCycle'       | [['a1/b1', 'a2/b2'], ['a2/b2', 'a3/b3'], ['a2/b2', 'a3/b3']]
        'a many-to-many between composite identifiers'   | [GccLeft, GccRight]            | 'betweenCycle'      | [['x1/y1', 'x2/y2'], ['x2/y2', 'x3/y3'], ['x2/y2', 'x3/y3']]
        'a map of entities on both sides'                | [GccMapLeft, GccMapRight]      | 'mapBothCycle'      | [[one: 'r1', two: 'r2'], [two: 'r2', three: 'r3']]
        'a map of entities of a composite owner'         | [GccMapOwner, GccItem]         | 'mapOwnerCycle'     | [[one: 'i1', two: 'i2'], [two: 'i2', three: 'i3']]
        'a many-to-many with the key columns mapped'     | [GccMappedLeft, GccMappedRight] | 'mappedCycle'      | [['x1/y1'], ['x2/y2']]
        'a foreign key mapped with fewer columns than the key' | [GccPartialRef, GccPartialKey] | 'partialCycle' | ['a1/b1', 'a2/b2']
        'a collection mapped with one column of two'     | [GccPartialTags]               | 'partialTagsCycle'  | [['t1', 't2'], ['t3']]
    }

    private List ownedCycle() {
        GccCompOwner.withTransaction {
            GccOther one = new GccOther(name: 'o1').save(failOnError: true)
            GccOther two = new GccOther(name: 'o2').save(failOnError: true)
            new GccCompOwner(a: 'a1', b: 'b1', others: [one, two] as Set).save(failOnError: true, flush: true)
        }
        GccCompOwner key = new GccCompOwner(a: 'a1', b: 'b1')
        List first = GccCompOwner.withNewSession { GccCompOwner.get(key).others*.name.sort() }
        GccCompOwner.withTransaction {
            GccCompOwner owner = GccCompOwner.get(key)
            owner.others = [GccOther.findByName('o2'), new GccOther(name: 'o3').save(failOnError: true)] as Set
            owner.save(failOnError: true, flush: true)
        }
        List second = GccCompOwner.withNewSession { GccCompOwner.get(key).others*.name.sort() }
        List fromOther = GccOther.withNewSession { GccOther.findAllByNameInList(['o1', 'o2', 'o3']).findAll { it.owners*.a.contains('a1') }*.name.sort() }
        return [first, second, fromOther]
    }

    private List targetCycle() {
        GccSimple.withTransaction {
            new GccSimple(comps: [new GccCompTarget(a: 'a1', b: 'b1').save(failOnError: true), new GccCompTarget(a: 'a2', b: 'b2').save(failOnError: true)] as Set)
                    .save(failOnError: true, flush: true)
        }
        Long id = GccSimple.withNewSession { GccSimple.list().first().id }
        List first = GccSimple.withNewSession { GccSimple.get(id).comps.collect { "${it.a}/${it.b}".toString() }.sort() }
        GccSimple.withTransaction {
            GccSimple simple = GccSimple.get(id)
            simple.comps = [GccCompTarget.get(new GccCompTarget(a: 'a2', b: 'b2')), new GccCompTarget(a: 'a3', b: 'b3').save(failOnError: true)] as Set
            simple.save(failOnError: true, flush: true)
        }
        List second = GccSimple.withNewSession { GccSimple.get(id).comps.collect { "${it.a}/${it.b}".toString() }.sort() }
        List fromComp = GccCompTarget.withNewSession {
            GccCompTarget.list().findAll { !it.simples.isEmpty() }.collect { "${it.a}/${it.b}".toString() }.sort()
        }
        return [first, second, fromComp]
    }

    private List betweenCycle() {
        GccLeft.withTransaction {
            new GccLeft(a: 'a1', b: 'b1', rights: [new GccRight(x: 'x1', y: 'y1').save(failOnError: true), new GccRight(x: 'x2', y: 'y2').save(failOnError: true)] as Set)
                    .save(failOnError: true, flush: true)
        }
        GccLeft key = new GccLeft(a: 'a1', b: 'b1')
        List first = GccLeft.withNewSession { GccLeft.get(key).rights.collect { "${it.x}/${it.y}".toString() }.sort() }
        GccLeft.withTransaction {
            GccLeft left = GccLeft.get(key)
            left.rights = [GccRight.get(new GccRight(x: 'x2', y: 'y2')), new GccRight(x: 'x3', y: 'y3').save(failOnError: true)] as Set
            left.save(failOnError: true, flush: true)
        }
        List second = GccLeft.withNewSession { GccLeft.get(key).rights.collect { "${it.x}/${it.y}".toString() }.sort() }
        List fromRight = GccRight.withNewSession {
            GccRight.list().findAll { !it.lefts.isEmpty() }.collect { "${it.x}/${it.y}".toString() }.sort()
        }
        return [first, second, fromRight]
    }

    private List mapBothCycle() {
        Long id = GccMapLeft.withTransaction {
            new GccMapLeft(rights: [one: new GccMapRight(a: 'a1', b: 'b1', label: 'r1').save(failOnError: true),
                                    two: new GccMapRight(a: 'a2', b: 'b2', label: 'r2').save(failOnError: true)]).save(failOnError: true, flush: true).id
        }
        Map first = GccMapLeft.withNewSession { new TreeMap(GccMapLeft.get(id).rights.collectEntries { k, v -> [(k): v.label] }) }
        GccMapLeft.withTransaction {
            GccMapLeft left = GccMapLeft.get(id)
            left.rights = [two: GccMapRight.get(new GccMapRight(a: 'a2', b: 'b2')),
                           three: new GccMapRight(a: 'a3', b: 'b3', label: 'r3').save(failOnError: true)]
            left.save(failOnError: true, flush: true)
        }
        Map second = GccMapLeft.withNewSession { new TreeMap(GccMapLeft.get(id).rights.collectEntries { k, v -> [(k): v.label] }) }
        return [first, second]
    }

    private List mapOwnerCycle() {
        GccMapOwner.withTransaction {
            new GccMapOwner(
                    a: 'a1', b: 'b1',
                    items: [one: new GccItem(label: 'i1').save(failOnError: true), two: new GccItem(label: 'i2').save(failOnError: true)])
                    .save(failOnError: true, flush: true)
        }
        GccMapOwner key = new GccMapOwner(a: 'a1', b: 'b1')
        Map first = GccMapOwner.withNewSession { new TreeMap(GccMapOwner.get(key).items.collectEntries { k, v -> [(k): v.label] }) }
        GccMapOwner.withTransaction {
            GccMapOwner owner = GccMapOwner.get(key)
            owner.items = [two: GccItem.findByLabel('i2'), three: new GccItem(label: 'i3').save(failOnError: true)]
            owner.save(failOnError: true, flush: true)
        }
        Map second = GccMapOwner.withNewSession { new TreeMap(GccMapOwner.get(key).items.collectEntries { k, v -> [(k): v.label] }) }
        return [first, second]
    }

    private List mappedCycle() {
        GccMappedLeft.withTransaction {
            new GccMappedLeft(a: 'a1', b: 'b1', rights: [new GccMappedRight(x: 'x1', y: 'y1').save(failOnError: true)] as Set).save(failOnError: true, flush: true)
        }
        GccMappedLeft key = new GccMappedLeft(a: 'a1', b: 'b1')
        List first = GccMappedLeft.withNewSession { GccMappedLeft.get(key).rights.collect { "${it.x}/${it.y}".toString() }.sort() }
        GccMappedLeft.withTransaction {
            GccMappedLeft left = GccMappedLeft.get(key)
            left.rights = [new GccMappedRight(x: 'x2', y: 'y2').save(failOnError: true)] as Set
            left.save(failOnError: true, flush: true)
        }
        return [first, GccMappedLeft.withNewSession { GccMappedLeft.get(key).rights.collect { "${it.x}/${it.y}".toString() }.sort() }]
    }

    private List partialCycle() {
        GccPartialRef.withTransaction {
            GccPartialKey first = new GccPartialKey(a: 'a1', b: 'b1').save(failOnError: true)
            new GccPartialRef(target: first).save(failOnError: true, flush: true)
        }
        Long id = GccPartialRef.withNewSession { GccPartialRef.list().first().id }
        String first = GccPartialRef.withNewSession { GccPartialRef ref = GccPartialRef.get(id); "${ref.target.a}/${ref.target.b}".toString() }
        GccPartialRef.withTransaction {
            GccPartialRef ref = GccPartialRef.get(id)
            ref.target = new GccPartialKey(a: 'a2', b: 'b2').save(failOnError: true)
            ref.save(failOnError: true, flush: true)
        }
        return [first, GccPartialRef.withNewSession { GccPartialRef ref = GccPartialRef.get(id); "${ref.target.a}/${ref.target.b}".toString() }]
    }

    private List partialTagsCycle() {
        GccPartialTags.withTransaction { new GccPartialTags(a: 'a1', b: 'b1', tags: ['t1', 't2'] as Set).save(failOnError: true, flush: true) }
        GccPartialTags key = new GccPartialTags(a: 'a1', b: 'b1')
        List first = GccPartialTags.withNewSession { GccPartialTags.get(key).tags.sort() }
        GccPartialTags.withTransaction {
            GccPartialTags owner = GccPartialTags.get(key)
            owner.tags = ['t3'] as Set
            owner.save(failOnError: true, flush: true)
        }
        return [first, GccPartialTags.withNewSession { GccPartialTags.get(key).tags.sort() }]
    }
}

@Entity
class GccOther {
    String name
    Set<GccCompOwner> owners

    static hasMany = [owners: GccCompOwner]
    static belongsTo = [GccCompOwner]
}

@Entity
class GccCompOwner implements Serializable {
    String a
    String b
    Set<GccOther> others

    static hasMany = [others: GccOther]

    static mapping = {
        id composite: ['a', 'b']
    }
}

@Entity
class GccSimple {
    Set<GccCompTarget> comps

    static hasMany = [comps: GccCompTarget]
}

@Entity
class GccCompTarget implements Serializable {
    String a
    String b
    Set<GccSimple> simples

    static hasMany = [simples: GccSimple]
    static belongsTo = [GccSimple]

    static mapping = {
        id composite: ['a', 'b']
    }
}

@Entity
class GccLeft implements Serializable {
    String a
    String b
    Set<GccRight> rights

    static hasMany = [rights: GccRight]

    static mapping = {
        id composite: ['a', 'b']
    }
}

@Entity
class GccRight implements Serializable {
    String x
    String y
    Set<GccLeft> lefts

    static hasMany = [lefts: GccLeft]
    static belongsTo = [GccLeft]

    static mapping = {
        id composite: ['x', 'y']
    }
}

@Entity
class GccMapLeft {
    Map<String, GccMapRight> rights

    static hasMany = [rights: GccMapRight]
}

@Entity
class GccMapRight implements Serializable {
    String a
    String b
    String label
    Map<String, GccMapLeft> lefts

    static hasMany = [lefts: GccMapLeft]
    static belongsTo = [GccMapLeft]

    static mapping = {
        id composite: ['a', 'b']
    }
}

@Entity
class GccItem {
    String label
}

@Entity
class GccMapOwner implements Serializable {
    String a
    String b
    Map<String, GccItem> items

    static hasMany = [items: GccItem]

    static mapping = {
        id composite: ['a', 'b']
    }
}

@Entity
class GccMappedLeft implements Serializable {
    String a
    String b
    Set<GccMappedRight> rights

    static hasMany = [rights: GccMappedRight]

    static mapping = {
        id composite: ['a', 'b']
        columns {
            rights {
                column name: 'k1'
                column name: 'k2'
            }
        }
    }
}

@Entity
class GccMappedRight implements Serializable {
    String x
    String y
    Set<GccMappedLeft> lefts

    static hasMany = [lefts: GccMappedLeft]
    static belongsTo = [GccMappedLeft]

    static mapping = {
        id composite: ['x', 'y']
    }
}

@Entity
class GccPartialKey implements Serializable {
    String a
    String b

    static mapping = {
        id composite: ['a', 'b']
    }
}

@Entity
class GccPartialRef {
    GccPartialKey target

    static mapping = {
        target column: 'only_one'
    }
}

@Entity
class GccPartialTags implements Serializable {
    String a
    String b
    Set<String> tags

    static hasMany = [tags: String]

    static mapping = {
        id composite: ['a', 'b']
        columns {
            tags {
                column name: 'k1'
            }
        }
    }
}
