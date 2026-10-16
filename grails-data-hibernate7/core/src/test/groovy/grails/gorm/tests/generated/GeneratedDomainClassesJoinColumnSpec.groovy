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
 * Column settings on a foreign key: a to-one association, the key of a collection, the element of a many-to-many. The domain binder puts
 * the {@code defaultValue}, {@code comment}, {@code read} and {@code write} of the column config on the foreign key column and ignores
 * {@code length}, {@code precision} and {@code scale} (the type of the column is the one of the column it references). The generated
 * mode accepts the mapping and creates the same columns, instead of refusing it.
 */
class GeneratedDomainClassesJoinColumnSpec extends Specification {

    static final AtomicInteger BOOTS = new AtomicInteger()

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(List<Class> group, boolean generated) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'                  : "jdbc:h2:mem:gdj${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'             : 'create-drop',
                'hibernate.generatedDomainClasses': generated,
        ], group as Class[])
        return datastore
    }

    /** The columns of the boot model with the type, nullability, comment, default and read and write expressions of each, and the foreign keys. */
    private Map<String, Map> schema(List<Class> group, boolean generated) {
        HibernateDatastore booted = boot(group, generated)
        Map<String, Map> result = new TreeMap<String, Map>()
        for (Table table : booted.metadata.collectTableMappings()) {
            if (table.physicalTable) {
                result[table.name] = [
                        columns    : table.columns.collectEntries { org.hibernate.mapping.Column column ->
                            [(column.name): [
                                    type    : "${column.getSqlType(booted.metadata)}${column.nullable ? '' : ' not null'}".toString(),
                                    comment : column.comment,
                                    default : column.defaultValue,
                                    read    : column.customRead,
                                    write   : column.customWrite,
                            ]]
                        },
                        primaryKey : table.primaryKey?.columns*.name?.sort(),
                        foreignKeys: table.foreignKeys.values().collect { "${it.columns*.name} -> ${it.referencedTable.name}".toString() }.sort(),
                ]
            }
        }
        return result
    }

    @Unroll
    void "#label: the generated mode creates the foreign key column of the domain binder, with its comment, default and expressions"() {
        when:
        Map<String, Map> binder = schema(group, false)
        Map<String, Map> generated = schema(group, true)

        then:
        generated == binder
        generated[table].columns[column].subMap(['comment', 'default', 'read', 'write']) == expected

        where:
        label                               | group                          | table                | column               | expected
        'a many-to-one'                     | [GdjRef, GdjTarget]            | 'gdj_ref'            | 'target_id'          | [comment: 'the target', default: '1', read: 'target_id', write: '?']
        'a many-to-one to a string id'      | [GdjStrRef, GdjStrTarget]      | 'gdj_str_ref'        | 'target_id'          | [comment: 'to a string', default: null, read: null, write: null]
        'a unidirectional collection of entities' | [GdjUniOwner, GdjTarget] | 'gdj_uni_owner_gdj_target' | 'gdj_uni_owner_items_id' | [comment: 'the key', default: '0', read: 'gdj_uni_owner_items_id', write: '?']
        'a collection of basic values'      | [GdjBasicOwner]                | 'gdj_basic_owner_tags' | 'gdj_basic_owner_id' | [comment: 'the tags key', default: '0', read: 'gdj_basic_owner_id', write: '?']
        'a many-to-many'                    | [GdjManyA, GdjManyB]           | 'gdj_manya_bs'       | 'gdj_manya_id'      | [comment: 'the key of A', default: null, read: null, write: null]
        'a bidirectional one-to-many'       | [GdjParent, GdjKid]            | 'gdj_kid'            | 'parent_id'          | [comment: 'the parent', default: null, read: null, write: null]
        'a many-to-one inside an embedded type' | [GdjEmbOwner, GdjTarget]   | 'gdj_emb_owner'      | 'holder_ref_id'      | [comment: 'in the holder', default: null, read: null, write: null]
        'a part of a composite identifier'  | [GdjCompPart, GdjTarget]       | 'gdj_comp_part'      | 'target_id'          | [comment: 'the part', default: null, read: null, write: null]
        'a foreign key to a composite identifier (nothing to state)' | [GdjCompRef, GdjCompKey] | 'gdj_comp_ref'       | 'k1'                 | [comment: null, default: null, read: null, write: null]
    }

    @Unroll
    void "#label: the types of the foreign key columns ignore length, precision and scale in both modes"() {
        when:
        Map<String, Map> binder = schema(group, false)
        Map<String, Map> generated = schema(group, true)

        then:
        generated == binder
        generated[table].columns[column].type == type

        where:
        label                                          | group                      | table                 | column       | type
        'a many-to-one to a long id'                   | [GdjRef, GdjTarget]        | 'gdj_ref'             | 'target_id'  | 'bigint'
        'a many-to-one to a string id'                 | [GdjStrRef, GdjStrTarget]  | 'gdj_str_ref'         | 'target_id'  | 'varchar(255)'
        'a collection of numbers with a precision'     | [GdjBasicOwner]            | 'gdj_basic_owner_amounts' | 'amounts_java_math_big_decimal' | 'numeric(38,2)'
    }

    @Unroll
    void "#label: the associations are stored and read back in both modes"() {
        when:
        Map<Boolean, List> results = [false, true].collectEntries { boolean generated ->
            boot(group, generated)
            [(generated): this."${cycle}"()]
        }

        then:
        results[true] == results[false]
        results[true] == expected

        where:
        label                       | group                          | cycle          | expected
        'a many-to-one'             | [GdjRef, GdjTarget]            | 'refCycle'     | ['t1', 't2']
        'a unidirectional collection' | [GdjUniOwner, GdjTarget]     | 'uniCycle'     | [['t1', 't2'], ['t3']]
        'a collection of basic values' | [GdjBasicOwner]             | 'basicCycle'   | [['a', 'b'], ['c']]
        'a many-to-many'            | [GdjManyA, GdjManyB]           | 'manyCycle'    | [['b1', 'b2'], ['b3']]
        'a bidirectional one-to-many' | [GdjParent, GdjKid]          | 'kidsCycle'    | [['k1', 'k2'], ['k1', 'k2', 'k3']]
    }

    private List refCycle() {
        Long id = GdjRef.withTransaction { new GdjRef(target: new GdjTarget(name: 't1').save(failOnError: true)).save(failOnError: true, flush: true).id }
        String first = GdjRef.withNewSession { GdjRef.get(id).target.name }
        GdjRef.withTransaction { GdjRef ref = GdjRef.get(id); ref.target = new GdjTarget(name: 't2').save(failOnError: true); ref.save(failOnError: true, flush: true) }
        return [first, GdjRef.withNewSession { GdjRef.get(id).target.name }]
    }

    private List uniCycle() {
        Long id = GdjUniOwner.withTransaction {
            new GdjUniOwner(items: [new GdjTarget(name: 't1').save(failOnError: true), new GdjTarget(name: 't2').save(failOnError: true)] as Set).save(failOnError: true, flush: true).id
        }
        List first = GdjUniOwner.withNewSession { GdjUniOwner.get(id).items*.name.sort() }
        GdjUniOwner.withTransaction {
            GdjUniOwner owner = GdjUniOwner.get(id)
            owner.items = [new GdjTarget(name: 't3').save(failOnError: true)] as Set
            owner.save(failOnError: true, flush: true)
        }
        return [first, GdjUniOwner.withNewSession { GdjUniOwner.get(id).items*.name.sort() }]
    }

    private List basicCycle() {
        Long id = GdjBasicOwner.withTransaction { new GdjBasicOwner(tags: ['a', 'b'] as Set).save(failOnError: true, flush: true).id }
        List first = GdjBasicOwner.withNewSession { GdjBasicOwner.get(id).tags.sort() }
        GdjBasicOwner.withTransaction { GdjBasicOwner owner = GdjBasicOwner.get(id); owner.tags = ['c'] as Set; owner.save(failOnError: true, flush: true) }
        return [first, GdjBasicOwner.withNewSession { GdjBasicOwner.get(id).tags.sort() }]
    }

    private List manyCycle() {
        Long id = GdjManyA.withTransaction {
            new GdjManyA(bs: [new GdjManyB(name: 'b1').save(failOnError: true), new GdjManyB(name: 'b2').save(failOnError: true)] as Set).save(failOnError: true, flush: true).id
        }
        List first = GdjManyA.withNewSession { GdjManyA.get(id).bs*.name.sort() }
        GdjManyA.withTransaction {
            GdjManyA owner = GdjManyA.get(id)
            owner.bs = [new GdjManyB(name: 'b3').save(failOnError: true)] as Set
            owner.save(failOnError: true, flush: true)
        }
        return [first, GdjManyA.withNewSession { GdjManyA.get(id).bs*.name.sort() }]
    }

    private List kidsCycle() {
        Long id = GdjParent.withTransaction {
            GdjParent parent = new GdjParent()
            parent.addToKids(new GdjKid(name: 'k1'))
            parent.addToKids(new GdjKid(name: 'k2'))
            parent.save(failOnError: true, flush: true).id
        }
        List first = GdjParent.withNewSession { GdjParent.get(id).kids*.name.sort() }
        GdjParent.withTransaction {
            GdjParent parent = GdjParent.get(id)
            parent.addToKids(new GdjKid(name: 'k3'))
            parent.save(failOnError: true, flush: true)
        }
        return [first, GdjParent.withNewSession { GdjParent.get(id).kids*.name.sort() }]
    }
}

@Entity
class GdjTarget {
    String name
}

@Entity
class GdjRef {
    GdjTarget target

    static mapping = {
        target length: 20, precision: 10, scale: 2, defaultValue: '1', comment: 'the target', read: 'target_id', write: '?'
    }
}

@Entity
class GdjStrTarget {
    String code
    String name

    static mapping = {
        id name: 'code', generator: 'assigned'
    }
}

@Entity
class GdjStrRef {
    GdjStrTarget target

    static mapping = {
        target length: 20, comment: 'to a string'
    }
}

@Entity
class GdjUniOwner {
    Set<GdjTarget> items

    static hasMany = [items: GdjTarget]

    static mapping = {
        items length: 20, comment: 'the key', defaultValue: '0', read: 'gdj_uni_owner_items_id', write: '?'
    }
}

@Entity
class GdjBasicOwner {
    Set<String> tags
    Set<BigDecimal> amounts

    static hasMany = [tags: String, amounts: BigDecimal]

    static mapping = {
        tags length: 20, comment: 'the tags key', defaultValue: '0', read: 'gdj_basic_owner_id', write: '?'
        amounts precision: 10, scale: 2
    }
}

@Entity
class GdjManyA {
    Set<GdjManyB> bs

    static hasMany = [bs: GdjManyB]

    static mapping = {
        bs comment: 'the key of A'
    }
}

@Entity
class GdjManyB {
    String name
    Set<GdjManyA> owners

    static hasMany = [owners: GdjManyA]
    static belongsTo = [GdjManyA]
}

@Entity
class GdjParent {
    Set<GdjKid> kids

    static hasMany = [kids: GdjKid]

    static mapping = {
        kids comment: 'ignored: the key is the column of the kid'
    }
}

@Entity
class GdjKid {
    String name
    GdjParent parent

    static belongsTo = [parent: GdjParent]

    static mapping = {
        parent comment: 'the parent'
    }
}

class GdjHolder {
    GdjTarget ref

    static mapping = {
        ref comment: 'in the holder'
    }
}

@Entity
class GdjEmbOwner {
    GdjHolder holder

    static embedded = ['holder']
}

@Entity
class GdjCompPart implements Serializable {
    GdjTarget target
    String code

    static mapping = {
        id composite: ['target', 'code']
        target comment: 'the part'
    }
}

@Entity
class GdjCompKey implements Serializable {
    String a
    String b

    static mapping = {
        id composite: ['a', 'b']
    }
}

@Entity
class GdjCompRef {
    GdjCompKey key

    static mapping = {
        columns {
            key {
                column name: 'k1'
                column name: 'k2'
            }
        }
    }
}
