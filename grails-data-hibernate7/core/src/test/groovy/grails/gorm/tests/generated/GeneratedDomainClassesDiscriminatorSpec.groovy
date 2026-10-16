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
 * A discriminator column with a precision or a scale, and a discriminator of a type other than string, integer or character.
 * {@code @DiscriminatorColumn} cannot state either. The precision and the scale change nothing in the DDL of a string or an integer
 * column, but the classic binding of Grails 8 put them on the column, and a mapping that sets them boots with the same column. A
 * discriminator typed {@code 'long'} is a bigint column, as it was. The columns stated here are the ones classic binding created.
 */
class GeneratedDomainClassesDiscriminatorSpec extends Specification {

    static final AtomicInteger BOOTS = new AtomicInteger()

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(List<Class> group) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'     : "jdbc:h2:mem:gdd${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate': 'create-drop',
        ], group as Class[])
        return datastore
    }

    /** The columns of the boot model with their SQL type and nullability, and the precision and scale the model holds for the discriminator. */
    private Map<String, Map> schema(List<Class> group) {
        HibernateDatastore booted = boot(group)
        Map<String, Map> result = new TreeMap<String, Map>()
        for (Table table : booted.metadata.collectTableMappings()) {
            if (table.physicalTable) {
                result[table.name] = table.columns.collectEntries { org.hibernate.mapping.Column column ->
                    String discriminator = column.name == 'kind' ? " precision=${column.precision} scale=${column.scale}" : ''
                    [(column.name): "${column.getSqlType(booted.metadata)}${column.nullable ? '' : ' not null'}${discriminator}".toString()]
                }
            }
        }
        return result
    }

    @Unroll
    void "#label: the discriminator column of classic binding is created, with its precision and scale"() {
        when:
        Map<String, Map> generated = schema(group)

        then:
        generated[table]['kind'] == column

        where:
        label                                   | group                            | table          | column
        'a string discriminator with both'      | [GddStringBase, GddStringSub]    | 'gdd_string_base' | 'varchar(255) not null precision=5 scale=2'
        'an integer discriminator with a precision' | [GddIntBase, GddIntSub]      | 'gdd_int_base'    | 'integer not null precision=5 scale=null'
        'a discriminator typed long'        | [GddLongBase, GddLongSub]        | 'gdd_long_base'   | 'bigint not null precision=null scale=null'
    }

    @Unroll
    void "#label: the rows of the hierarchy are stored and found by the discriminator"() {
        when:
        boot(group)
        List results = this."${cycle}"()

        then:
        results == [2, 1, ['sub']]

        where:
        label                | group                         | cycle
        'a string discriminator' | [GddStringBase, GddStringSub] | 'stringCycle'
        'an integer discriminator' | [GddIntBase, GddIntSub]     | 'intCycle'
        'a discriminator typed long' | [GddLongBase, GddLongSub] | 'longCycle'
    }

    private List stringCycle() {
        GddStringBase.withTransaction {
            new GddStringBase(name: 'base').save(failOnError: true)
            new GddStringSub(name: 'sub', more: 'x').save(failOnError: true, flush: true)
        }
        return GddStringBase.withNewSession { [GddStringBase.count(), GddStringSub.count(), GddStringSub.list()*.name] }
    }

    private List longCycle() {
        GddLongBase.withTransaction {
            new GddLongBase(name: 'base').save(failOnError: true)
            new GddLongSub(name: 'sub', more: 'x').save(failOnError: true, flush: true)
        }
        return GddLongBase.withNewSession { [GddLongBase.count(), GddLongSub.count(), GddLongSub.list()*.name] }
    }

    private List intCycle() {
        GddIntBase.withTransaction {
            new GddIntBase(name: 'base').save(failOnError: true)
            new GddIntSub(name: 'sub', more: 'x').save(failOnError: true, flush: true)
        }
        return GddIntBase.withNewSession { [GddIntBase.count(), GddIntSub.count(), GddIntSub.list()*.name] }
    }
}

@Entity
class GddStringBase {
    String name
    static mapping = {
        discriminator column: [name: 'kind', precision: 5, scale: 2]
    }
}

@Entity
class GddStringSub extends GddStringBase {
    String more
}

@Entity
class GddIntBase {
    String name
    static mapping = {
        discriminator value: '1', type: 'integer', column: [name: 'kind', precision: 5]
    }
}

@Entity
class GddIntSub extends GddIntBase {
    String more
    static mapping = {
        discriminator value: '2'
    }
}

@Entity
class GddLongBase {
    String name
    static mapping = {
        discriminator value: '1', type: 'long', column: [name: 'kind']
    }
}

@Entity
class GddLongSub extends GddLongBase {
    String more
    static mapping = {
        discriminator value: '2'
    }
}
