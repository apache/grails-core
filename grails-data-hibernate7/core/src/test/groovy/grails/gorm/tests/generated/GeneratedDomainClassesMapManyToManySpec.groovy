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
import org.grails.orm.hibernate.cfg.domainbinding.binder.MapMapLeft
import org.grails.orm.hibernate.cfg.domainbinding.binder.MapMapRight
import org.grails.orm.hibernate.cfg.domainbinding.binder.MapSetLeft
import org.grails.orm.hibernate.cfg.domainbinding.binder.MapSetRight

/**
 * A {@code Map} on a many-to-many. As with the classic binding of Grails 8, a map side is not inverse whether or not the mapping says
 * it owns the relationship, so the map side writes the join table (key column, element column and the column of the key of the map,
 * which is part of the primary key), a {@code Set} on the other side is inverse and reads the same table, and two maps are two
 * independent tables (a map on a many-to-many stores through its own side only). The generated classes state exactly that; the tables
 * and the behaviour stated here are the ones classic binding gave.
 */
class GeneratedDomainClassesMapManyToManySpec extends Specification {

    static final AtomicInteger BOOTS = new AtomicInteger()

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(List<Class> group) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'     : "jdbc:h2:mem:mmm${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate': 'create-drop',
        ], group as Class[])
        return datastore
    }

    /** The tables of the boot model as plain data, and which collection of which role is inverse. */
    private Map<String, Map> schema(List<Class> group) {
        HibernateDatastore booted = boot(group)
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
        result['collections'] = booted.metadata.collectionBindings.collectEntries { org.hibernate.mapping.Collection collection ->
            [(collection.role): "${collection.class.simpleName} inverse=${collection.inverse} table=${collection.collectionTable.name} key=${collection.key.selectables*.text}".toString()]
        }.sort()
        return result
    }

    @Unroll
    void "#label: the schema of classic binding is created"() {
        when:
        Map<String, Map> generated = schema(group)

        then:
        generated.keySet() - 'collections' == tables as Set
        generated[joinTable].columns.keySet() == columns as Set
        generated[joinTable].primaryKey == primaryKey.sort(false)

        where:
        label                                          | group                       | tables                                                                        | joinTable                | columns                                                         | primaryKey
        'a map beside a set'                           | [MapSetLeft, MapSetRight]   | ['map_set_left', 'map_set_right', 'map_set_left_rights']                      | 'map_set_left_rights'    | ['map_set_left_id', 'map_set_right_id', 'rights_idx']           | ['map_set_left_id', 'rights_idx']
        'a map beside a map'                           | [MapMapLeft, MapMapRight]   | ['map_map_left', 'map_map_right', 'map_map_left_rights', 'map_map_right_lefts'] | 'map_map_right_lefts'  | ['map_map_left_id', 'map_map_right_id', 'lefts_idx']            | ['map_map_right_id', 'lefts_idx']
        'a map beside a set that belongs to the map'   | [MmmOwnerLeft, MmmOwnedRight] | ['mmm_owner_left', 'mmm_owned_right', 'mmm_owner_left_rights']              | 'mmm_owner_left_rights'  | ['mmm_owner_left_id', 'mmm_owned_right_id', 'rights_idx']       | ['mmm_owner_left_id', 'rights_idx']
        'a map beside a set that owns the relationship' | [MmmOwnedLeft, MmmOwnerRight] | ['mmm_owned_left', 'mmm_owner_right', 'mmm_owned_left_rights', 'mmm_owner_right_lefts'] | 'mmm_owned_left_rights' | ['mmm_owned_left_id', 'mmm_owner_right_id', 'rights_idx'] | ['mmm_owned_left_id', 'rights_idx']
        'a map with a join table, key, column and index names' | [MmmNamedLeft, MmmNamedRight] | ['mmm_named_left', 'mmm_named_right', 'mmm_named_join']              | 'mmm_named_join'         | ['l_fk', 'r_fk', 'k_col']                                      | ['l_fk', 'k_col']
    }

    void "the map side writes the join table and the set side is inverse"() {
        when:
        Map<String, Map> generated = schema([MapSetLeft, MapSetRight])

        then:
        generated.collections['org.grails.orm.hibernate.cfg.domainbinding.binder.MapSetLeft.rights'].contains('inverse=false')
        generated.collections['org.grails.orm.hibernate.cfg.domainbinding.binder.MapSetRight.lefts'].contains('inverse=true')
        generated.collections['org.grails.orm.hibernate.cfg.domainbinding.binder.MapSetRight.lefts'].contains('table=map_set_left_rights')
    }

    @Unroll
    void "#label: the rows are stored through the map, read back, changed and deleted"() {
        when:
        boot(group)
        Map results = cycle(left, right, joinTable)

        then:
        results.stored == [a: 'r1', b: 'r2']
        results.changed == [b: 'r2', c: 'r1']
        results.rowsStored == 2
        results.rowsChanged == 2
        results.rowsDeleted == 0
        results.inverse == inverse

        where:
        label                           | group                         | left          | right          | joinTable               | inverse
        'a map beside a set'            | [MapSetLeft, MapSetRight]     | MapSetLeft    | MapSetRight    | 'map_set_left_rights'   | ['left']
        'a map beside a map'            | [MapMapLeft, MapMapRight]     | MapMapLeft    | MapMapRight    | 'map_map_left_rights'   | []
        'a map beside a set it owns'    | [MmmOwnerLeft, MmmOwnedRight] | MmmOwnerLeft  | MmmOwnedRight  | 'mmm_owner_left_rights' | ['left']
        'a map beside an owning set'    | [MmmOwnedLeft, MmmOwnerRight] | MmmOwnedLeft  | MmmOwnerRight  | 'mmm_owned_left_rights' | []
        'a map with a join table named' | [MmmNamedLeft, MmmNamedRight] | MmmNamedLeft  | MmmNamedRight  | 'mmm_named_join'        | ['left']
    }

    void "the element column of a map is not null whatever the order the classes are bound in"() {
        when: "the column is shared by the element of the map and the key of the set that reads it (classic binding set its nullability twice, and the order decided)"
        Map<String, String> generated = [[MmmOwnerLeft, MmmOwnedRight], [MmmOwnedRight, MmmOwnerLeft]].collectEntries { List<Class> group ->
            [(group*.simpleName.join(',')): schema(group)['mmm_owner_left_rights'].columns['mmm_owned_right_id']]
        }

        then:
        generated.values().toSet() == ['bigint not null'].toSet()
    }

    private Map cycle(Class left, Class right, String joinTable) {
        Long leftId
        Long firstId
        left.withTransaction {
            def first = right.newInstance(name: 'r1').save(failOnError: true)
            def second = right.newInstance(name: 'r2').save(failOnError: true)
            firstId = first.id
            leftId = left.newInstance(name: 'left', rights: [a: first, b: second]).save(failOnError: true, flush: true).id
        }
        Map stored = left.withNewSession { new TreeMap(left.get(leftId).rights.collectEntries { String key, value -> [key, value.name] }) }
        List inverse = right.withNewSession {
            def side = right.get(firstId).lefts
            side instanceof Map ? side.values()*.name.sort() : side*.name.sort()
        }
        int rowsStored = countRows(joinTable)
        left.withTransaction {
            def row = left.get(leftId)
            def first = right.get(firstId)
            row.rights.remove('a')
            row.rights.put('c', first)
            row.save(failOnError: true, flush: true)
        }
        Map changed = left.withNewSession { new TreeMap(left.get(leftId).rights.collectEntries { String key, value -> [key, value.name] }) }
        int rowsChanged = countRows(joinTable)
        left.withTransaction { left.get(leftId).delete(flush: true) }
        return [stored: stored, inverse: inverse, changed: changed, rowsStored: rowsStored, rowsChanged: rowsChanged, rowsDeleted: countRows(joinTable)]
    }

    private int countRows(String table) {
        datastore.connectionSources.defaultConnectionSource.dataSource.connection.withCloseable { java.sql.Connection connection ->
            connection.createStatement().executeQuery("select count(*) from ${table}".toString()).with { java.sql.ResultSet rows -> rows.next(); rows.getInt(1) }
        }
    }

    void "a query joins through the map and through the set that reads it"() {
        given:
        boot([MapSetLeft, MapSetRight])

        when:
        MapSetLeft.withTransaction {
            MapSetRight red = new MapSetRight(name: 'red').save(failOnError: true)
            MapSetRight blue = new MapSetRight(name: 'blue').save(failOnError: true)
            new MapSetLeft(name: 'one', rights: [x: red, y: blue]).save(failOnError: true)
            new MapSetLeft(name: 'two', rights: [x: red]).save(failOnError: true, flush: true)
        }
        List results = MapSetLeft.withNewSession {
            [MapSetLeft.executeQuery('select l.name from MapSetLeft l join l.rights r where r.name = :n order by l.name', [n: 'red']),
             MapSetLeft.executeQuery('select l.name from MapSetLeft l join l.rights r where r.name = :n', [n: 'blue']),
             MapSetRight.executeQuery('select l.name from MapSetRight r join r.lefts l where r.name = :n order by l.name', [n: 'red']),
             MapSetLeft.executeQuery('select count(l) from MapSetLeft l where size(l.rights) = 2')[0]]
        }

        then:
        results == [['one', 'two'], ['one'], ['one', 'two'], 1]
    }

    void "the second map of two maps does not see the rows stored through the first"() {
        given:
        boot([MapMapLeft, MapMapRight])

        when:
        MapMapLeft.withTransaction {
            MapMapRight right = new MapMapRight(name: 'right').save(failOnError: true)
            new MapMapLeft(name: 'left', rights: [first: right]).save(failOnError: true, flush: true)
        }
        List results = MapMapLeft.withNewSession { [MapMapLeft.findByName('left').rights.keySet() as List, MapMapRight.findByName('right').lefts.keySet() as List] }

        then:
        results == [['first'], []]
    }
}

@Entity
class MmmOwnerLeft {
    String name
    Map<String, MmmOwnedRight> rights
    static hasMany = [rights: MmmOwnedRight]
}

@Entity
class MmmOwnedRight {
    String name
    Set<MmmOwnerLeft> lefts
    static hasMany = [lefts: MmmOwnerLeft]
    static belongsTo = MmmOwnerLeft
}

@Entity
class MmmOwnedLeft {
    String name
    Map<String, MmmOwnerRight> rights
    static hasMany = [rights: MmmOwnerRight]
    static belongsTo = MmmOwnerRight
}

@Entity
class MmmOwnerRight {
    String name
    Set<MmmOwnedLeft> lefts
    static hasMany = [lefts: MmmOwnedLeft]
}

@Entity
class MmmNamedLeft {
    String name
    Map<String, MmmNamedRight> rights
    static hasMany = [rights: MmmNamedRight]
    static mapping = {
        rights joinTable: [name: 'mmm_named_join', key: 'l_fk', column: 'r_fk'], indexColumn: [name: 'k_col']
    }
}

@Entity
class MmmNamedRight {
    String name
    Set<MmmNamedLeft> lefts
    static hasMany = [lefts: MmmNamedLeft]
}
