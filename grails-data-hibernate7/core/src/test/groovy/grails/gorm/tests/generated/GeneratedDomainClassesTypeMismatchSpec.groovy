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
 * A registered type name for a different Java type than the property's, for example {@code type: 'string'} on an {@code Integer}. As
 * the classic binding of Grails 8 did, the name is resolved and not the property's class: the column has the registered type's SQL type,
 * and the type cannot convert the value, so a value that is not null fails when it is saved with a {@code ClassCastException}.
 */
class GeneratedDomainClassesTypeMismatchSpec extends Specification {

    static final AtomicInteger BOOTS = new AtomicInteger()

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(List<Class> group) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'     : "jdbc:h2:mem:tm${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate': 'create-drop',
        ], group as Class[])
        return datastore
    }

    /** The tables of the boot model as plain data: columns with SQL type and nullability. */
    private Map<String, Map> schema(HibernateDatastore booted) {
        Map<String, Map> result = new TreeMap<String, Map>()
        for (Table table : booted.metadata.collectTableMappings()) {
            if (table.physicalTable) {
                result[table.name] = [
                        columns   : table.columns.collectEntries { org.hibernate.mapping.Column column ->
                            boolean nullable = column.nullable && !(table.primaryKey != null && table.primaryKey.columns*.name.contains(column.name))
                            [(column.name): "${column.getSqlType(booted.metadata)}${nullable ? '' : ' not null'}".toString()]
                        },
                        primaryKey: table.primaryKey?.columns*.name?.sort(),
                ]
            }
        }
        return result
    }

    private static String attempt(Closure<?> step) {
        try {
            return "ok: ${step.call()}".toString()
        } catch (Throwable e) {
            Throwable root = e
            while (root.cause != null && root.cause != root) {
                root = root.cause
            }
            return "failed: ${root.class.simpleName}".toString()
        }
    }

    @Unroll
    void "#label has the column type classic binding gave it"() {
        when:
        Map<String, Map> generated = schema(boot([type]))

        then:
        generated[table].columns.tag.startsWith(sqlType)

        where:
        label                                      | type          | table          | sqlType
        'a string type on an Integer'              | TmStringOnInt | 'tm_string_on_int' | 'varchar(255)'
        'an integer type on a String'              | TmIntOnString | 'tm_int_on_string' | 'integer'
        'a long type on an Integer'                | TmLongOnInt   | 'tm_long_on_int'   | 'bigint'
        'a boolean type on a String'               | TmBoolOnString | 'tm_bool_on_string' | 'boolean'
        'a text type on an Integer'                | TmTextOnInt   | 'tm_text_on_int'   | 'varchar(32600)'
        'a timestamp type on a Long'               | TmDateOnLong  | 'tm_date_on_long'  | 'timestamp'
        'a materialized_clob type on an Integer'   | TmClobOnInt   | 'tm_clob_on_int'   | 'clob'
    }

    @Unroll
    void "#label fails to save a value as classic binding did, and stores null and the other properties"() {
        when:
        boot([type])
        Map steps = [:]
        steps.saveValue = attempt { type.withTransaction { type.newInstance(tag: value, name: 'a').save(failOnError: true, flush: true).id } }
        Long id = null
        steps.saveNull = attempt { id = type.withTransaction { type.newInstance(name: 'b').save(failOnError: true, flush: true).id } }
        steps.reload = attempt { type.withNewSession { Object found = type.get(id); [found.name, found.tag] } }

        then:
        steps.saveValue == 'failed: ClassCastException'
        steps.saveNull.startsWith('ok: ')
        steps.reload.startsWith('ok: [b, null]')

        where:
        label                       | type           | value
        'a string type on an Integer' | TmStringOnInt | 42
        'an integer type on a String' | TmIntOnString | '42'
        'a long type on an Integer'   | TmLongOnInt   | 42
        'a boolean type on a String'  | TmBoolOnString | 'x'
    }
}

@Entity
class TmStringOnInt {
    String name
    Integer tag
    static constraints = { name nullable: true; tag nullable: true }
    static mapping = { tag type: 'string' }
}

@Entity
class TmIntOnString {
    String name
    String tag
    static constraints = { name nullable: true; tag nullable: true }
    static mapping = { tag type: 'integer' }
}

@Entity
class TmLongOnInt {
    String name
    Integer tag
    static constraints = { name nullable: true; tag nullable: true }
    static mapping = { tag type: 'long' }
}

@Entity
class TmBoolOnString {
    String name
    String tag
    static constraints = { name nullable: true; tag nullable: true }
    static mapping = { tag type: 'boolean' }
}

@Entity
class TmTextOnInt {
    String name
    Integer tag
    static constraints = { name nullable: true; tag nullable: true }
    static mapping = { tag type: 'text' }
}

@Entity
class TmDateOnLong {
    String name
    Long tag
    static constraints = { name nullable: true; tag nullable: true }
    static mapping = { tag type: 'timestamp' }
}

@Entity
class TmClobOnInt {
    String name
    Integer tag
    static constraints = { name nullable: true; tag nullable: true }
    static mapping = { tag type: 'materialized_clob' }
}
