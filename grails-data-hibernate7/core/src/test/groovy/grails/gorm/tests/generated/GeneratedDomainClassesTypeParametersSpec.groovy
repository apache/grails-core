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
import java.sql.Types
import java.util.concurrent.atomic.AtomicInteger

import grails.gorm.annotation.Entity
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.mapping.Table
import org.hibernate.usertype.ParameterizedType
import org.hibernate.usertype.UserType
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.orm.hibernate.HibernateDatastore

/**
 * {@code params} on a {@code type}. They are handed to the type, as the classic binding of Grails 8 handed them: a registered type
 * name ignores them, a {@code UserType} that implements {@code ParameterizedType} receives them.
 */
class GeneratedDomainClassesTypeParametersSpec extends Specification {

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
        'a string typed string with type parameters'         | [TnStringParams]       | 'tn_string_params'    | 'my_property'   | 'varchar(255)'
        'an integer typed integer with type parameters'      | [TnIntegerParams]      | 'tn_integer_params'   | 'amount'        | 'integer'
        'a string with a user type that takes parameters'    | [TnPrefixed]           | 'tn_prefixed'         | 'label'         | 'varchar(255)'
    }

    void "a type with type parameters is saved and read back as with classic binding, which handed the parameters to a registered type that ignores them"() {
        when:
        Map generated = roundTrip(boot([TnStringParams]), TnStringParams, [myProperty: 'hello'], 'myProperty', 'select my_property from tn_string_params')

        then:
        generated == [stored: [['hello']], reloaded: 'hello']
    }

    void "the parameters of a user type reach the user type"() {
        when:
        Map generated = roundTrip(boot([TnPrefixed]), TnPrefixed, [label: 'hello'], 'label', 'select label from tn_prefixed')

        then:
        generated == [stored: [['p:hello']], reloaded: 'hello']
    }
}

@Entity
class TnStringParams {
    String myProperty
    static mapping = { myProperty type: 'string', params: [param1: 'value1'] }
}

@Entity
class TnIntegerParams {
    Integer amount
    static mapping = { amount type: 'integer', params: [param1: 'value1'] }
}

class TnPrefixType implements UserType<String>, ParameterizedType {

    String prefix = ''

    void setParameterValues(Properties parameters) {
        prefix = parameters.getProperty('prefix')
    }

    int getSqlType() { Types.VARCHAR }
    Class<String> returnedClass() { String }
    boolean equals(String x, String y) { x == y }
    int hashCode(String x) { x.hashCode() }

    String nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) {
        String text = rs.getString(position)
        text == null ? null : text.substring(prefix.length())
    }

    void nullSafeSet(PreparedStatement st, String value, int index, SharedSessionContractImplementor session) {
        if (value == null) {
            st.setNull(index, Types.VARCHAR)
        } else {
            st.setString(index, prefix + value)
        }
    }

    String deepCopy(String value) { value }
    boolean isMutable() { false }
    Serializable disassemble(String value) { value }
    String assemble(Serializable cached, Object owner) { (String) cached }
}

@Entity
class TnPrefixed {
    String label
    static mapping = { label type: TnPrefixType, params: [prefix: 'p:'] }
}
