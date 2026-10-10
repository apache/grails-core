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
 * The index column of a list and the key column of a map are typed by the mapping ({@code indexColumn: [type: 'long']}, or the
 * {@code type} of the {@code index:} settings), independently of the declared key class, and the classic binding of Grails 8 gave
 * them that type. The generated classes state it with Hibernate's own annotations, so a database created by Grails 8 keeps its schema
 * (the tables and keys stated here are the ones classic binding created) and the keys of a map are read back as the type the column
 * holds.
 */
class GeneratedDomainClassesCollectionIndexTypeSpec extends Specification {

    static final AtomicInteger BOOTS = new AtomicInteger()

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(List<Class> group) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'     : "jdbc:h2:mem:cit${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate': 'create-drop',
        ], group as Class[])
        return datastore
    }

    /** The collection tables of the boot model as plain data: every column with its SQL type, nullability and length. */
    private Map<String, Map> schema(List<Class> group) {
        HibernateDatastore booted = boot(group)
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
                ]
            }
        }
        return result
    }

    @Unroll
    void "#label is typed as classic binding typed it"() {
        when:
        Map<String, Map> generated = schema(group)

        then: "the column and the key it is part of are the ones classic binding created"
        generated[table].columns[column] == type
        generated[table].primaryKey == primaryKey
        generated.keySet() == tables as Set

        where:
        label                                              | group                  | tables                                                   | table                  | column  | type                   | primaryKey
        'the key of a map of entities, mapped as long'     | [CitLongMap, CitValue] | ['cit_long_map', 'cit_long_map_tags', 'cit_value']        | 'cit_long_map_tags'    | 'tags_idx' | 'long not null'     | ['cit_long_map_tags_id', 'tags_idx']
        'the key of a map of values, mapped as long'       | [CitValueMap]          | ['cit_value_map', 'cit_value_map_attrs']                  | 'cit_value_map_attrs'  | 'k'     | 'long not null'        | ['cit_value_map_id', 'k']
        'the key of a map, mapped as integer and named'    | [CitIntegerMap]        | ['cit_integer_map', 'cit_integer_map_attrs']              | 'cit_integer_map_attrs' | 'k'    | 'integer not null'     | ['cit_integer_map_id', 'k']
        'the key of a map, with a length'                  | [CitLengthMap]         | ['cit_length_map', 'cit_length_map_attrs']                | 'cit_length_map_attrs' | 'k'     | 'varchar(40) not null' | ['cit_length_map_id', 'k']
        'the index of a list, mapped as long'              | [CitLongList]          | ['cit_long_list', 'cit_long_list_items']                  | 'cit_long_list_items'  | 'ix'    | 'bigint not null'      | ['cit_long_list_id', 'ix']
        'the index of a list, mapped as string'            | [CitStringList]        | ['cit_string_list', 'cit_string_list_items']              | 'cit_string_list_items' | 'ix'   | 'varchar(255) not null' | ['cit_string_list_id', 'ix']
        'the index of a list of entities, mapped as long'  | [CitEntityList, CitValue] | ['cit_entity_list', 'cit_entity_list_cit_value', 'cit_value'] | 'cit_entity_list_cit_value' | 'ix' | 'bigint not null' | ['cit_entity_list_values_id', 'ix']
    }

    void "a map keyed by long saves, reloads and is queried with the keys the column holds"() {
        given:
        boot([CitLongMap, CitValue])

        when:
        Long id = CitLongMap.withTransaction {
            CitLongMap owner = new CitLongMap()
            owner.tags = [(1L): new CitValue(name: 'one').save(failOnError: true), (22L): new CitValue(name: 'two').save(failOnError: true)]
            owner.save(failOnError: true, flush: true).id
        }

        then:
        CitLongMap.withNewSession {
            Map<Long, CitValue> loaded = CitLongMap.get(id).tags
            loaded.keySet() == [1L, 22L] as Set && loaded[22L].name == 'two'
        }
    }

    void "the index of a list mapped as long keeps the order of the elements"() {
        given:
        boot([CitLongList, CitStringList])

        when:
        Long longId = CitLongList.withTransaction { new CitLongList(items: ['c', 'a', 'b']).save(failOnError: true, flush: true).id }
        Long stringId = CitStringList.withTransaction { new CitStringList(items: ['z', 'y']).save(failOnError: true, flush: true).id }

        then:
        CitLongList.withNewSession { CitLongList.get(longId).items } == ['c', 'a', 'b']
        CitStringList.withNewSession { CitStringList.get(stringId).items } == ['z', 'y']
    }
}

@Entity
class CitValue {
    String name
}

@Entity
class CitLongMap {
    Map<Long, CitValue> tags
    static hasMany = [tags: CitValue]
    static mapping = {
        tags indexColumn: [type: 'long']
    }
}

@Entity
class CitValueMap {
    Map<String, String> attrs
    static hasMany = [attrs: String]
    static mapping = {
        attrs indexColumn: [type: 'long', name: 'k']
    }
}

@Entity
class CitIntegerMap {
    Map<Integer, String> attrs
    static hasMany = [attrs: String]
    static mapping = {
        attrs indexColumn: [type: 'integer', name: 'k']
    }
}

@Entity
class CitLengthMap {
    Map<String, String> attrs
    static hasMany = [attrs: String]
    static mapping = {
        attrs indexColumn: [name: 'k', length: 40]
    }
}

@Entity
class CitLongList {
    List<String> items
    static hasMany = [items: String]
    static mapping = {
        items indexColumn: [type: 'long', name: 'ix']
    }
}

@Entity
class CitStringList {
    List<String> items
    static hasMany = [items: String]
    static mapping = {
        items indexColumn: [type: 'string', name: 'ix']
    }
}

@Entity
class CitEntityList {
    List<CitValue> values
    static hasMany = [values: CitValue]
    static mapping = {
        values indexColumn: [type: 'long', name: 'ix']
    }
}
