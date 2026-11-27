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

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.util.concurrent.atomic.AtomicInteger

import grails.gorm.annotation.Entity
import org.hibernate.mapping.Table
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.orm.hibernate.HibernateDatastore

/**
 * {@code type: 'serializable'} on a property that is not a collection. The registered type is for {@code java.io.Serializable}, so the
 * domain binder stores the Java serialization of a {@code String} or any other value in a binary column. The generated mode boots with
 * the same schema and stores the same bytes.
 */
class GeneratedDomainClassesSerializableTypeSpec extends Specification {

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

    /** The rows of a table as stored, binary values as lists of bytes. */
    private List<List> rows(String sql) {
        return datastore.connectionSources.defaultConnectionSource.dataSource.connection.withCloseable { Connection connection ->
            ResultSet result = connection.createStatement().executeQuery(sql)
            ResultSetMetaData meta = result.metaData
            List<List> found = []
            while (result.next()) {
                found << (1..meta.columnCount).collect { int i ->
                    Object value = result.getObject(i)
                    value instanceof byte[] ? ((byte[]) value).toList() : value
                }
            }
            found
        }
    }

    /** What a property does when it is saved, read back by a new session and stored: the value, or why it could not be read. */
    private Map roundTrip(HibernateDatastore booted, Class type, Map<String, Object> values, String property, String select) {
        Long id = type.withTransaction { type.newInstance(values).save(failOnError: true, flush: true).id }
        Map observed = [stored: rows(select)]
        try {
            Object value = type.withNewSession { type.get(id)."${property}" }
            observed.reloaded = value instanceof byte[] ? ((byte[]) value).toList() : value
        } catch (Throwable e) {
            Throwable root = e
            while (root.cause != null && root.cause != root) {
                root = root.cause
            }
            observed.reloaded = "cannot be read: ${root.class.simpleName}".toString()
        }
        return observed
    }

    @Unroll
    void "#label has the column type the domain binder gives it"() {
        when:
        Map<String, Map> binder = schema(boot(group, false))
        Map<String, Map> generated = schema(boot(group, true))

        then:
        generated == binder
        generated[table].columns[column] == type

        where:
        label                                                | group                  | table                 | column          | type
        'a byte array typed serializable'                    | [TnSerializableBytes]  | 'tn_serializable_bytes' | 'payload'     | 'varbinary(255)'
        'a string typed serializable'                        | [TnSerializableString] | 'tn_serializable_string' | 'tag'        | 'varbinary(255)'
    }

    void "a string typed serializable is stored as the Java serialization of the string, as the domain binder stores it"() {
        when:
        Map binder = roundTrip(boot([TnSerializableString], false), TnSerializableString, [tag: 'hello'], 'tag', 'select tag from tn_serializable_string')
        Map generated = roundTrip(boot([TnSerializableString], true), TnSerializableString, [tag: 'hello'], 'tag', 'select tag from tn_serializable_string')

        then:
        generated == binder
        generated.reloaded == 'hello'
        generated.stored == [[[-84, -19, 0, 5, 116, 0, 5, 104, 101, 108, 108, 111]]]
    }

    void "a byte array typed serializable behaves as it does in the domain binder"() {
        when:
        Map binder = roundTrip(boot([TnSerializableBytes], false), TnSerializableBytes, [payload: 'hello'.bytes], 'payload', 'select payload from tn_serializable_bytes')
        Map generated = roundTrip(boot([TnSerializableBytes], true), TnSerializableBytes, [payload: 'hello'.bytes], 'payload', 'select payload from tn_serializable_bytes')

        then:
        generated == binder
    }

    void "a serialized byte array written by the Java serialization is read back by both bindings"() {
        given:
        byte[] serialized = new ByteArrayOutputStream().with { bytes ->
            new ObjectOutputStream(bytes).with { it.writeObject('hello'.bytes); it.flush() }
            bytes.toByteArray()
        }

        when:
        Map<Boolean, Object> read = [false, true].collectEntries { boolean generated ->
            boot([TnSerializableBytes], generated)
            datastore.connectionSources.defaultConnectionSource.dataSource.connection.withCloseable { Connection connection ->
                PreparedStatement insert = connection.prepareStatement('insert into tn_serializable_bytes (id, version, payload) values (1, 0, ?)')
                insert.setBytes(1, serialized)
                insert.executeUpdate()
            }
            Object value = TnSerializableBytes.withNewSession { TnSerializableBytes.get(1L).payload }
            [(generated): ((byte[]) value).toList()]
        }

        then:
        read[true] == read[false]
        read[true] == 'hello'.bytes.toList()
    }
}

@Entity
class TnSerializableBytes {
    byte[] payload
    static mapping = { payload type: 'serializable' }
}

@Entity
class TnSerializableString {
    String tag
    static mapping = { tag type: 'serializable' }
}
