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
import org.hibernate.mapping.Table
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.orm.hibernate.HibernateDatastore

/**
 * An {@code index:} or a {@code unique:} group mapped on a collection property, booted through the generated classes and through
 * the domain binder. The binder binds the key column of a collection like any other column, so {@code index:} creates an index
 * over the key column of the collection table (and, for a collection of enums, over the element column too), named as the mapping
 * says; a unique group names columns of the owner's table, which the collection table does not have, so it creates no key in the
 * generated mode (the binder's impossible key is a defect fixed on the 8.0.x line).
 */
class GeneratedDomainClassesCollectionConstraintsSpec extends Specification {

    static final AtomicInteger BOOTS = new AtomicInteger()

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(List<Class> group, boolean generated) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'                  : "jdbc:h2:mem:ccs${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'             : 'create-drop',
                'hibernate.generatedDomainClasses': generated,
        ], group as Class[])
        return datastore
    }

    /** The tables of the boot model as plain data: the columns with their type, the indexes and the unique keys. */
    private Map<String, Map> schema(List<Class> group, boolean generated) {
        HibernateDatastore booted = boot(group, generated)
        Map<String, Map> result = new TreeMap<String, Map>()
        for (Table table : booted.metadata.collectTableMappings()) {
            if (table.physicalTable) {
                result[table.name] = [
                        columns   : table.columns.collectEntries { org.hibernate.mapping.Column column ->
                            // a primary key column is not null in the schema whatever the column says
                            boolean nullable = column.nullable && !(table.primaryKey != null && table.primaryKey.columns*.name.contains(column.name))
                            [(column.name): "${column.getSqlType(booted.metadata)}${nullable ? '' : ' not null'}".toString()]
                        },
                        primaryKey: table.primaryKey?.columns*.name?.sort(),
                        indexes   : table.indexes.values().collectEntries { [(it.name): it.columns*.name] },
                        uniqueKeys: table.uniqueKeys.values().collect { it.columns*.name.sort() }.sort { it.toString() },
                ]
            }
        }
        return result
    }

    /** The names of the indexes H2 itself holds on a table, whatever it generated for keys. */
    private List<String> h2Indexes(String table) {
        List<String> found = []
        datastore.connectionSources.defaultConnectionSource.dataSource.connection.withCloseable { java.sql.Connection connection ->
            connection.createStatement().withCloseable { java.sql.Statement statement ->
                statement.executeQuery(
                        "select INDEX_NAME from INFORMATION_SCHEMA.INDEXES where upper(TABLE_NAME) = '${table.toUpperCase()}' " +
                                "and INDEX_TYPE_NAME = 'INDEX' order by INDEX_NAME".toString()).withCloseable { java.sql.ResultSet rows ->
                    while (rows.next()) {
                        found << rows.getString(1).toLowerCase()
                    }
                }
            }
        }
        return found.findAll { String name -> !name.startsWith('fk') }
    }

    @Unroll
    void "an index mapped on #label creates the indexes the domain binder creates"() {
        when:
        Map<String, Map> binder = schema(group, false)
        Map<String, Map> generated = schema(group, true)

        then: "the indexes and keys of every table are the binder's (the binder leaves an unused column in the table of a map of values)"
        generated.keySet() == binder.keySet()
        generated.collectEntries { String name, Map description -> [(name): [description.indexes, description.uniqueKeys]] } ==
                binder.collectEntries { String name, Map description -> [(name): [description.indexes, description.uniqueKeys]] }
        generated[table].indexes == indexes

        where:
        label                                    | group                | table                  | indexes
        'a set of strings'                       | [CcsSetIdx]          | 'ccs_set_idx_tags'     | [ccs_tags_idx: ['ccs_set_idx_id']]
        'a set, with true'                       | [CcsIndexTrue]       | 'ccs_index_true_tags'  | [ccs_index_true_tags_ccs_index_true_id_idx: ['ccs_index_true_id']]
        'a set, with two names'                  | [CcsIndexMulti]      | 'ccs_index_multi_tags' | [ccs_a: ['ccs_index_multi_id'], ccs_b: ['ccs_index_multi_id']]
        'a set, with false'                      | [CcsIndexFalse]      | 'ccs_index_false_tags' | [:]
        'a set of enums, over the element too'   | [CcsEnumIdx]         | 'ccs_enum_idx_colors'  | [ccs_color_idx: ['ccs_color', 'ccs_enum_idx_id']]
        'a list, which names its index column'   | [CcsListIdx]         | 'ccs_list_idx_items'   | [ccs_items_idx: ['ccs_list_idx_id']]
        'a map'                                  | [CcsMapIdx]          | 'ccs_map_idx_attrs'    | [ccs_attrs_idx: ['ccs_map_idx_id']]
        'a map, with the settings of the index column' | [CcsMapSettings] | 'ccs_map_settings_attrs' | ['[column:ccs_k': ['ccs_map_settings_id'], 'type:string]': ['ccs_map_settings_id']]
        'a list of long, with the settings of the index column' | [CcsListSettings] | 'ccs_list_settings_items' | ['[column:ccs_ix': ['ccs_list_settings_id'], 'type:long]': ['ccs_list_settings_id']]
        'a one-to-many through a join table'     | [CcsKidOwner, CcsKid] | 'ccs_kid_owner_ccs_kid' | [ccs_kids_idx: ['ccs_kid_owner_kids_id']]
        'a many-to-many, on both sides'          | [CcsLeft, CcsRight]  | 'ccs_left_rights'      | [ccs_right_idx: ['ccs_right_id'], ccs_left_idx: ['ccs_left_id']]
        'a collection with a mapped join key'    | [CcsJoinKey]         | 'ccs_tags_table'       | [:]
        'a one-to-many mapped by a foreign key'  | [CcsParent, CcsChild] | 'ccs_child'           | [:]
    }

    void "the indexes are created in the database, over the key column of the collection table"() {
        when:
        boot([CcsSetIdx, CcsEnumIdx], true)

        then:
        h2Indexes('ccs_set_idx_tags') == ['ccs_tags_idx']
        h2Indexes('ccs_enum_idx_colors') == ['ccs_color_idx']
    }

    void "a closure mapped as the index names no index, as its name would be the closure's own, which differs on every boot"() {
        when:
        Map<String, Map> binder = schema([CcsClosureIdx], false)
        Map<String, Map> generated = schema([CcsClosureIdx], true)

        then: "both name the index column after the closure, and only the binder creates an index, named after the closure instance"
        generated['ccs_closure_idx_items'].columns == binder['ccs_closure_idx_items'].columns
        generated['ccs_closure_idx_items'].columns.keySet().contains('ccs_cx')
        generated['ccs_closure_idx_items'].indexes.isEmpty()
        binder['ccs_closure_idx_items'].indexes.size() == 1
        binder['ccs_closure_idx_items'].indexes.keySet().first().contains('closure')
    }

    @Unroll
    void "a unique group mapped on #label creates no key, since the collection table does not hold the columns of the group"() {
        when:
        Map<String, Map> generated = schema(group, true)

        then: "the owner's table has no key either, and the table of the collection holds the keys of the set only"
        generated[owner].uniqueKeys.isEmpty()
        generated[table].uniqueKeys.every { List<String> columns -> columns.every { String column -> column in generated[table].columns.keySet() } }
        !generated[table].uniqueKeys.any { List<String> columns -> 'x' in columns }

        where:
        label                    | group           | owner           | table
        'a set of strings'       | [CcsUqGroup]    | 'ccs_uq_group'  | 'ccs_uq_group_tags'
        'a list'                 | [CcsUqList]     | 'ccs_uq_list'   | 'ccs_uq_list_items'
        'a set of enums'         | [CcsUqEnum]     | 'ccs_uq_enum'   | 'ccs_uq_enum_colors'
    }

    void "a collection with a unique group and an index saves and loads, and the group does not reject a repeated value"() {
        given:
        boot([CcsUqGroup, CcsSetIdx], true)

        when:
        Long id = CcsUqGroup.withTransaction {
            new CcsUqGroup(x: 'a', tags: ['one', 'two'] as Set).save(failOnError: true, flush: true).id
        }
        CcsUqGroup.withTransaction {
            new CcsUqGroup(x: 'a', tags: ['one'] as Set).save(failOnError: true, flush: true)
        }
        Long indexed = CcsSetIdx.withTransaction {
            new CcsSetIdx(tags: ['x', 'y', 'z'] as Set).save(failOnError: true, flush: true).id
        }

        then:
        CcsUqGroup.withNewSession { CcsUqGroup.get(id).tags } == ['one', 'two'] as Set
        CcsUqGroup.withNewSession { CcsUqGroup.count() } == 2
        CcsSetIdx.withNewSession { CcsSetIdx.get(indexed).tags } == ['x', 'y', 'z'] as Set
    }

    void "a list, a map and a one-to-many with an index save, reload and are found through queries"() {
        given:
        boot([CcsListIdx, CcsMapIdx, CcsKidOwner, CcsKid, CcsLeft, CcsRight], true)

        when:
        Long listId = CcsListIdx.withTransaction { new CcsListIdx(items: ['a', 'b', 'c']).save(failOnError: true, flush: true).id }
        Long mapId = CcsMapIdx.withTransaction { new CcsMapIdx(attrs: [k1: 'v1', k2: 'v2']).save(failOnError: true, flush: true).id }
        Long ownerId = CcsKidOwner.withTransaction {
            CcsKidOwner owner = new CcsKidOwner()
            owner.addToKids(new CcsKid(name: 'kid'))
            owner.save(failOnError: true, flush: true).id
        }
        Long leftId = CcsLeft.withTransaction {
            CcsLeft left = new CcsLeft()
            left.addToRights(new CcsRight())
            left.save(failOnError: true, flush: true).id
        }

        then:
        CcsListIdx.withNewSession { CcsListIdx.get(listId).items } == ['a', 'b', 'c']
        CcsMapIdx.withNewSession { CcsMapIdx.get(mapId).attrs } == [k1: 'v1', k2: 'v2']
        CcsKidOwner.withNewSession { CcsKidOwner.get(ownerId).kids*.name } == ['kid']
        CcsLeft.withNewSession { CcsLeft.get(leftId).rights.size() } == 1
        CcsRight.withNewSession { CcsRight.list().first().lefts.size() } == 1
        CcsListIdx.withNewSession { CcsListIdx.where { items.size() == 3 }.count() } == 1
    }
}

enum CcsColor { RED, GREEN }

@Entity
class CcsSetIdx {
    Set<String> tags
    static hasMany = [tags: String]
    static mapping = { tags index: 'ccs_tags_idx' }
}

@Entity
class CcsIndexTrue {
    Set<String> tags
    static hasMany = [tags: String]
    static mapping = { tags index: true }
}

@Entity
class CcsIndexMulti {
    Set<String> tags
    static hasMany = [tags: String]
    static mapping = { tags index: 'ccs_a, ccs_b' }
}

@Entity
class CcsIndexFalse {
    Set<String> tags
    static hasMany = [tags: String]
    static mapping = { tags index: false }
}

@Entity
class CcsEnumIdx {
    Set<CcsColor> colors
    static hasMany = [colors: CcsColor]
    static mapping = { colors index: 'ccs_color_idx' }
}

@Entity
class CcsListIdx {
    List<String> items
    static hasMany = [items: String]
    static mapping = { items index: 'ccs_items_idx' }
}

@Entity
class CcsMapIdx {
    Map<String, String> attrs
    static hasMany = [attrs: String]
    static mapping = { attrs index: 'ccs_attrs_idx' }
}

@Entity
class CcsMapSettings {
    Map<String, String> attrs
    static hasMany = [attrs: String]
    static mapping = { attrs index: [column: 'ccs_k', type: 'string'] }
}

@Entity
class CcsListSettings {
    List<String> items
    static hasMany = [items: String]
    static mapping = { items index: [column: 'ccs_ix', type: 'long'] }
}

@Entity
class CcsClosureIdx {
    List<String> items
    static hasMany = [items: String]
    static mapping = {
        items index: {
            column name: 'ccs_cx'
        }
    }
}

@Entity
class CcsKid {
    String name
}

@Entity
class CcsKidOwner {
    Set<CcsKid> kids
    static hasMany = [kids: CcsKid]
    static mapping = { kids index: 'ccs_kids_idx' }
}

@Entity
class CcsLeft {
    Set<CcsRight> rights
    static hasMany = [rights: CcsRight]
    static mapping = { rights index: 'ccs_left_idx' }
}

@Entity
class CcsRight {
    Set<CcsLeft> lefts
    static hasMany = [lefts: CcsLeft]
    static belongsTo = [CcsLeft]
    static mapping = { lefts index: 'ccs_right_idx' }
}

@Entity
class CcsJoinKey {
    Set<String> tags
    static hasMany = [tags: String]
    static mapping = {
        tags joinTable: [name: 'ccs_tags_table', key: 'owner_fk', column: 'tag_value'], index: 'ccs_jk_idx'
    }
}

@Entity
class CcsParent {
    Set<CcsChild> children
    static hasMany = [children: CcsChild]
    static mapping = { children index: 'ccs_children_idx' }
}

@Entity
class CcsChild {
    CcsParent parent
    static belongsTo = [parent: CcsParent]
}

@Entity
class CcsUqGroup {
    String x
    Set<String> tags
    static hasMany = [tags: String]
    static mapping = { tags unique: 'x' }
}

@Entity
class CcsUqList {
    String x
    List<String> items
    static hasMany = [items: String]
    static mapping = { items unique: 'x' }
}

@Entity
class CcsUqEnum {
    String x
    Set<CcsColor> colors
    static hasMany = [colors: CcsColor]
    static mapping = { colors unique: 'x' }
}
