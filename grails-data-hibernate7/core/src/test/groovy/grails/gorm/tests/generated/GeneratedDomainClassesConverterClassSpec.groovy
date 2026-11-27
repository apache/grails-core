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
 * A {@code type} that names an {@code AttributeConverter} class, such as {@code org.hibernate.type.YesNoConverter}. The property is
 * converted with it, whatever the class of the property is, and also when the property is a {@code hasMany} stored in one column, as
 * the classic binding of Grails 8 did; the columns and the stored values stated here are the ones classic binding gave.
 */
class GeneratedDomainClassesConverterClassSpec extends Specification {

    static final AtomicInteger BOOTS = new AtomicInteger()

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(List<Class> group) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'     : "jdbc:h2:mem:tns${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate': 'create-drop',
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
    void "#label has the column type classic binding gave it"() {
        when:
        Map<String, Map> generated = schema(boot(group))

        then:
        generated[table].columns[column] == type

        where:
        label                                                | group                  | table                 | column          | type
        'a boolean converted by an attribute converter'      | [TnConvertedBoolean]   | 'tn_converted_boolean' | 'flag'         | 'char(1)'
        'a string typed with an attribute converter'         | [TnConvertedString]    | 'tn_converted_string' | 'data'          | 'char(1)'
        'a set typed with an attribute converter'            | [TnConvertedSet]       | 'tn_converted_set'    | 'categories'    | 'char(1)'
    }

    void "a boolean converted by an attribute converter is stored as the converter says and read back"() {
        when:
        Map generated = roundTrip(boot([TnConvertedBoolean]), TnConvertedBoolean, [flag: true], 'flag', 'select flag from tn_converted_boolean')

        then:
        generated == [stored: [['Y']], reloaded: true]

        and: "a query on the property converts its parameter"
        TnConvertedBoolean.withNewSession { TnConvertedBoolean.findAllByFlag(true)*.flag } == [true]
        TnConvertedBoolean.withNewSession { TnConvertedBoolean.findAllByFlag(false) } == []
    }

    void "an attribute converter on a property of a class it does not convert is accepted, as classic binding accepted it, and fails when used"() {
        given:
        boot([TnConvertedString])

        when:
        TnConvertedString.withTransaction { new TnConvertedString(data: 'Y').save(failOnError: true, flush: true) }

        then: "Hibernate refuses the value (an AssertionError of its converter resolution), as it did with classic binding"
        thrown(Throwable)
    }
}

@Entity
class TnConvertedBoolean {
    Boolean flag
    static mapping = { flag type: 'org.hibernate.type.YesNoConverter' }
}

@Entity
class TnConvertedString {
    String data
    static mapping = { data type: 'org.hibernate.type.YesNoConverter' }
}

@Entity
class TnConvertedSet {
    Set<String> categories
    static hasMany = [categories: String]
    static mapping = { categories type: 'org.hibernate.type.YesNoConverter' }
}
