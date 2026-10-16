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

import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.concurrent.atomic.AtomicInteger

import grails.gorm.annotation.Entity
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.mapping.Table
import org.hibernate.usertype.UserType
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.orm.hibernate.HibernateDatastore

/**
 * A {@code type} mapped on a collection property. The domain binder keeps the collection a collection only when the type is
 * neither a class that is not a collection type nor {@code serializable}: those two make the property one column of the owner's
 * table, typed with the user type or serialized, with the index, the unique group and the column settings of any other column. A
 * type name Hibernate knows ({@code text}) makes the binder fail to boot, and on a collection of entities it changes nothing.
 */
class GeneratedDomainClassesCollectionTypeSpec extends Specification {

    static final AtomicInteger BOOTS = new AtomicInteger()

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(List<Class> group, boolean generated) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'                  : "jdbc:h2:mem:cts${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'             : 'create-drop',
                'hibernate.generatedDomainClasses': generated,
        ], group as Class[])
        return datastore
    }

    /** The tables of the boot model as plain data: columns with SQL type and nullability, indexes, unique keys. */
    private Map<String, Map> schema(List<Class> group, boolean generated) {
        HibernateDatastore booted = boot(group, generated)
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

    @Unroll
    void "#label is one column of the owner's table, as the domain binder binds it"() {
        when:
        Map<String, Map> binder = schema(group, false)
        Map<String, Map> generated = schema(group, true)

        then:
        generated == binder
        generated.keySet() == tables as Set
        generated[table].columns[column] == type

        where:
        label                                    | group                | tables               | table                | column  | type
        'a set with a user type'                 | [CtsUserTypeSet]     | ['cts_user_type_set'] | 'cts_user_type_set' | 'tags'  | 'varchar(255)'
        'a list with a user type'                | [CtsUserTypeList]    | ['cts_user_type_list'] | 'cts_user_type_list' | 'items' | 'varchar(255)'
        'a serializable set'                     | [CtsSerializableSet] | ['cts_serializable_set'] | 'cts_serializable_set' | 'tags' | 'varbinary(255)'
        'a serializable list'                    | [CtsSerializableList] | ['cts_serializable_list'] | 'cts_serializable_list' | 'items' | 'varbinary(255)'
        'a serializable map'                     | [CtsSerializableMap] | ['cts_serializable_map'] | 'cts_serializable_map' | 'items' | 'varbinary(255)'
        'a serializable set of enums'            | [CtsSerializableEnums] | ['cts_serializable_enums'] | 'cts_serializable_enums' | 'colors' | 'varbinary(255)'
        'a serializable set with a column config' | [CtsSerializableColumn] | ['cts_serializable_column'] | 'cts_serializable_column' | 'ser_col' | 'varbinary(100)'
        'a serializable set that is not null'    | [CtsSerializableNotNull] | ['cts_serializable_not_null'] | 'cts_serializable_not_null' | 'tags' | 'varbinary(255) not null'
        'a serializable set with an index and a unique group' | [CtsSerializableKeys] | ['cts_serializable_keys'] | 'cts_serializable_keys' | 'tags' | 'varbinary(255)'
        'a set with a user type, an index and a unique group' | [CtsUserTypeKeys] | ['cts_user_type_keys'] | 'cts_user_type_keys' | 'tags' | 'varchar(255)'
    }

    void "the index and the unique group of a collection stored in one column are on the owner's table, where the binder creates them"() {
        when:
        Map<String, Map> generated = schema([CtsSerializableKeys, CtsUserTypeKeys], true)

        then:
        generated['cts_serializable_keys'].indexes == [cts_ser_idx: ['tags']]
        generated['cts_serializable_keys'].uniqueKeys == [['tags', 'x']]
        generated['cts_user_type_keys'].indexes == [cts_ut_idx: ['tags']]
        generated['cts_user_type_keys'].uniqueKeys == [['tags', 'x']]
    }

    void "a collection with a user type is stored with the user type and read back"() {
        given:
        boot([CtsUserTypeSet, CtsUserTypeList], true)

        when:
        Long setId = CtsUserTypeSet.withTransaction { new CtsUserTypeSet(tags: ['a', 'b'] as Set).save(failOnError: true, flush: true).id }
        Long listId = CtsUserTypeList.withTransaction { new CtsUserTypeList(items: ['x', 'y', 'x']).save(failOnError: true, flush: true).id }

        then:
        CtsUserTypeSet.withNewSession { CtsUserTypeSet.get(setId).tags } == ['a', 'b'] as Set
        CtsUserTypeList.withNewSession { CtsUserTypeList.get(listId).items } == ['x', 'y', 'x']
        datastore.connectionSources.defaultConnectionSource.dataSource.connection.withCloseable { java.sql.Connection connection ->
            connection.createStatement().executeQuery('select tags from cts_user_type_set').with { ResultSet rows -> rows.next(); rows.getString(1) }
        } in ['a,b', 'b,a']
    }

    @Unroll
    void "a serializable #label is stored in one column, read back and updated"() {
        given:
        boot(group, true)

        when:
        Long id = type.withTransaction { type.newInstance("${property}": initial).save(failOnError: true, flush: true).id }
        type.withTransaction { type.get(id)."${property}" = changed; type.get(id).save(failOnError: true, flush: true) }

        then:
        type.withNewSession { type.get(id)."${property}" } == changed

        where:
        label     | group                  | type                  | property | initial               | changed
        'set'     | [CtsSerializableSet]   | CtsSerializableSet    | 'tags'   | ['a', 'b'] as Set     | ['a', 'c'] as Set
        'list'    | [CtsSerializableList]  | CtsSerializableList   | 'items'  | ['a', 'b']            | ['b', 'a', 'z']
        'map'     | [CtsSerializableMap]   | CtsSerializableMap    | 'items'  | [one: 'a']            | [two: 'b']
        'enums'   | [CtsSerializableEnums] | CtsSerializableEnums  | 'colors' | [CtsTypeColor.RED] as Set | [CtsTypeColor.GREEN] as Set
    }

    void "a type mapped on a collection of entities changes nothing in both bindings"() {
        when:
        Map<String, Map> binder = schema([CtsEntityTyped, CtsKid], false)
        Map<String, Map> generated = schema([CtsEntityTyped, CtsKid], true)
        Long id = CtsEntityTyped.withTransaction {
            CtsEntityTyped owner = new CtsEntityTyped()
            owner.addToKids(new CtsKid(name: 'k'))
            owner.save(failOnError: true, flush: true).id
        }

        then: "the collection stays a join table"
        generated == binder
        generated.keySet().contains('cts_entity_typed_cts_kid')
        CtsEntityTyped.withNewSession { CtsEntityTyped.get(id).kids*.name } == ['k']
    }

    @Unroll
    void "a #label cannot be bound by the domain binder, and the generated mode says why instead of binding something else"() {
        when:
        boot(group, false)

        then: "the binder fails to boot"
        thrown(Exception)

        when:
        boot(group, true)

        then:
        Exception e = thrown()
        Throwable root = e
        while (root.cause != null && root.cause != root) {
            root = root.cause
        }
        root instanceof UnsupportedOperationException
        root.message.contains(message)

        where:
        label                                                    | group                 | message
        'registered type name on the property of a collection'   | [CtsNamedType]        | 'names neither a class nor serializable'
        'user type class on the property of a collection of entities' | [CtsEntityUserType, CtsKid] | 'binds as one column of the owner'
    }
}

enum CtsTypeColor { RED, GREEN }

class CtsSetType implements UserType<Set> {
    int getSqlType() { Types.VARCHAR }
    Class<Set> returnedClass() { Set }
    boolean equals(Set x, Set y) { x == y }
    int hashCode(Set x) { x.hashCode() }
    Set nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) {
        String text = rs.getString(position)
        text == null ? null : new HashSet(text.split(',').toList())
    }
    void nullSafeSet(PreparedStatement st, Set value, int index, SharedSessionContractImplementor session) {
        if (value == null) {
            st.setNull(index, Types.VARCHAR)
        } else {
            st.setString(index, value.join(','))
        }
    }
    Set deepCopy(Set value) { value == null ? null : new HashSet(value) }
    boolean isMutable() { true }
    Serializable disassemble(Set value) { value == null ? null : new HashSet(value) }
    Set assemble(Serializable cached, Object owner) { cached as Set }
}

class CtsListType implements UserType<List> {
    int getSqlType() { Types.VARCHAR }
    Class<List> returnedClass() { List }
    boolean equals(List x, List y) { x == y }
    int hashCode(List x) { x.hashCode() }
    List nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) {
        String text = rs.getString(position)
        text == null ? null : new ArrayList(text.split(',').toList())
    }
    void nullSafeSet(PreparedStatement st, List value, int index, SharedSessionContractImplementor session) {
        if (value == null) {
            st.setNull(index, Types.VARCHAR)
        } else {
            st.setString(index, value.join(','))
        }
    }
    List deepCopy(List value) { value == null ? null : new ArrayList(value) }
    boolean isMutable() { true }
    Serializable disassemble(List value) { value == null ? null : new ArrayList(value) }
    List assemble(Serializable cached, Object owner) { cached as List }
}

@Entity
class CtsUserTypeSet {
    Set<String> tags
    static hasMany = [tags: String]
    static mapping = { tags type: CtsSetType }
}

@Entity
class CtsUserTypeList {
    List<String> items
    static hasMany = [items: String]
    static mapping = { items type: CtsListType }
}

@Entity
class CtsSerializableSet {
    Set<String> tags
    static hasMany = [tags: String]
    static mapping = { tags type: 'serializable' }
}

@Entity
class CtsSerializableList {
    List<String> items
    static hasMany = [items: String]
    static mapping = { items type: 'serializable' }
}

@Entity
class CtsSerializableMap {
    Map<String, String> items
    static hasMany = [items: String]
    static mapping = { items type: 'serializable' }
}

@Entity
class CtsSerializableEnums {
    Set<CtsTypeColor> colors
    static hasMany = [colors: CtsTypeColor]
    static mapping = { colors type: 'serializable' }
}

@Entity
class CtsSerializableColumn {
    Set<String> tags
    static hasMany = [tags: String]
    static mapping = { tags type: 'serializable', column: 'ser_col', length: 100 }
}

@Entity
class CtsSerializableNotNull {
    Set<String> tags
    static hasMany = [tags: String]
    static mapping = { tags type: 'serializable' }
    static constraints = { tags nullable: false }
}

@Entity
class CtsSerializableKeys {
    String x
    Set<String> tags
    static hasMany = [tags: String]
    static mapping = { tags type: 'serializable', index: 'cts_ser_idx', unique: 'x' }
}

@Entity
class CtsUserTypeKeys {
    String x
    Set<String> tags
    static hasMany = [tags: String]
    static mapping = { tags type: CtsSetType, index: 'cts_ut_idx', unique: 'x' }
}

@Entity
class CtsKid {
    String name
}

@Entity
class CtsEntityTyped {
    Set<CtsKid> kids
    static hasMany = [kids: CtsKid]
    static mapping = { kids type: 'serializable' }
}

@Entity
class CtsNamedType {
    Set<String> tags
    static hasMany = [tags: String]
    static mapping = { tags type: 'text' }
}

@Entity
class CtsEntityUserType {
    Set<CtsKid> kids
    static hasMany = [kids: CtsKid]
    static mapping = { kids type: CtsSetType }
}
