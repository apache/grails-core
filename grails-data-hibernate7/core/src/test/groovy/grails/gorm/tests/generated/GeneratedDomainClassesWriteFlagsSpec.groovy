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
 * {@code insertable: false} and {@code updatable: false} on a property that has no column of its own. A collection and the
 * inverse side of a one-to-one are written through their own tables or by the other side, so the domain binder writes them
 * whatever the flags say, and the generated classes ignore the flags there the same way, instead of refusing the mapping. An
 * embedded object is written through the columns of its properties, which the binder keeps out of the insert or the update, and so
 * do the generated classes.
 */
class GeneratedDomainClassesWriteFlagsSpec extends Specification {

    static final AtomicInteger BOOTS = new AtomicInteger()

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(List<Class> group, boolean generated) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'                  : "jdbc:h2:mem:gdw${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'             : 'create-drop',
                'hibernate.generatedDomainClasses': generated,
        ], group as Class[])
        return datastore
    }

    private Map<String, Map> schema(List<Class> group, boolean generated) {
        HibernateDatastore booted = boot(group, generated)
        Map<String, Map> result = new TreeMap<String, Map>()
        for (Table table : booted.metadata.collectTableMappings()) {
            if (table.physicalTable) {
                result[table.name] = [
                        columns    : table.columns.collectEntries { org.hibernate.mapping.Column column ->
                            [(column.name): "${column.getSqlType(booted.metadata)}${column.nullable ? '' : ' not null'}".toString()]
                        },
                        primaryKey : table.primaryKey?.columns*.name?.sort(),
                        foreignKeys: table.foreignKeys.values().collect { "${it.columns*.name} -> ${it.referencedTable.name}".toString() }.sort(),
                ]
            }
        }
        return result
    }

    @Unroll
    void "#label: the generated mode creates the schema of the domain binder"() {
        expect:
        schema(group, true) == schema(group, false)

        where:
        label                                   | group
        'insertable false on a basic collection' | [GdwTagsOwner]
        'updatable false on a basic collection'  | [GdwUpdTagsOwner]
        'insertable false on a collection of entities' | [GdwItemsOwner, GdwItem]
        'insertable false on an embedded object' | [GdwEmbeddedOwner]
        'updatable false on an embedded object'  | [GdwUpdEmbeddedOwner]
        'insertable false on a many-to-many'     | [GdwManyA, GdwManyB]
        'insertable false on the hasOne side'    | [GdwHasOneOwner, GdwHasOneDetail]
    }

    @Unroll
    void "#label: the property is written and updated in both modes, whatever the flag says"() {
        when:
        Map<Boolean, List> results = [false, true].collectEntries { boolean generated ->
            boot(group, generated)
            [(generated): this."${cycle}"()]
        }

        then:
        results[true] == results[false]
        results[true] == expected

        where:
        label                                   | group                          | cycle                | expected
        'the tags with insertable false'        | [GdwTagsOwner]                 | 'tagsCycle'          | [['a', 'b'], ['c']]
        'the tags with updatable false'         | [GdwUpdTagsOwner]              | 'updTagsCycle'       | [['a', 'b'], ['c']]
        'the items with insertable false'       | [GdwItemsOwner, GdwItem]       | 'itemsCycle'         | [['one', 'two'], ['three']]
        'the embedded address with insertable false' | [GdwEmbeddedOwner]        | 'embeddedCycle'      | [null, 'avenue']
        'the embedded address with updatable false'  | [GdwUpdEmbeddedOwner]     | 'updEmbeddedCycle'   | ['street', 'street']
        'the many-to-many with insertable false' | [GdwManyA, GdwManyB]          | 'manyCycle'          | [['b1', 'b2'], ['b2', 'b3']]
        'the hasOne detail with insertable false' | [GdwHasOneOwner, GdwHasOneDetail] | 'hasOneCycle'   | ['d1', 'd2']
    }

    private List tagsCycle() {
        Long id = GdwTagsOwner.withTransaction { new GdwTagsOwner(tags: ['a', 'b'] as Set).save(failOnError: true, flush: true).id }
        List first = GdwTagsOwner.withNewSession { GdwTagsOwner.get(id).tags.sort() }
        GdwTagsOwner.withTransaction { GdwTagsOwner owner = GdwTagsOwner.get(id); owner.tags = ['c'] as Set; owner.save(failOnError: true, flush: true) }
        return [first, GdwTagsOwner.withNewSession { GdwTagsOwner.get(id).tags.sort() }]
    }

    private List updTagsCycle() {
        Long id = GdwUpdTagsOwner.withTransaction { new GdwUpdTagsOwner(tags: ['a', 'b'] as Set).save(failOnError: true, flush: true).id }
        List first = GdwUpdTagsOwner.withNewSession { GdwUpdTagsOwner.get(id).tags.sort() }
        GdwUpdTagsOwner.withTransaction { GdwUpdTagsOwner owner = GdwUpdTagsOwner.get(id); owner.tags = ['c'] as Set; owner.save(failOnError: true, flush: true) }
        return [first, GdwUpdTagsOwner.withNewSession { GdwUpdTagsOwner.get(id).tags.sort() }]
    }

    private List itemsCycle() {
        Long id = GdwItemsOwner.withTransaction {
            GdwItem one = new GdwItem(name: 'one').save(failOnError: true)
            GdwItem two = new GdwItem(name: 'two').save(failOnError: true)
            new GdwItemsOwner(items: [one, two] as Set).save(failOnError: true, flush: true).id
        }
        List first = GdwItemsOwner.withNewSession { GdwItemsOwner.get(id).items*.name.sort() }
        GdwItemsOwner.withTransaction {
            GdwItem three = new GdwItem(name: 'three').save(failOnError: true)
            GdwItemsOwner owner = GdwItemsOwner.get(id)
            owner.items = [three] as Set
            owner.save(failOnError: true, flush: true)
        }
        return [first, GdwItemsOwner.withNewSession { GdwItemsOwner.get(id).items*.name.sort() }]
    }

    private List embeddedCycle() {
        Long id = GdwEmbeddedOwner.withTransaction { new GdwEmbeddedOwner(home: new GdwAddress(street: 'street')).save(failOnError: true, flush: true).id }
        String first = GdwEmbeddedOwner.withNewSession { GdwEmbeddedOwner.get(id).home?.street }
        GdwEmbeddedOwner.withTransaction { GdwEmbeddedOwner owner = GdwEmbeddedOwner.get(id); owner.home = new GdwAddress(street: 'avenue'); owner.save(failOnError: true, flush: true) }
        return [first, GdwEmbeddedOwner.withNewSession { GdwEmbeddedOwner.get(id).home?.street }]
    }

    private List updEmbeddedCycle() {
        Long id = GdwUpdEmbeddedOwner.withTransaction { new GdwUpdEmbeddedOwner(home: new GdwAddress(street: 'street')).save(failOnError: true, flush: true).id }
        String first = GdwUpdEmbeddedOwner.withNewSession { GdwUpdEmbeddedOwner.get(id).home?.street }
        GdwUpdEmbeddedOwner.withTransaction { GdwUpdEmbeddedOwner owner = GdwUpdEmbeddedOwner.get(id); owner.home = new GdwAddress(street: 'avenue'); owner.save(failOnError: true, flush: true) }
        return [first, GdwUpdEmbeddedOwner.withNewSession { GdwUpdEmbeddedOwner.get(id).home?.street }]
    }

    private List manyCycle() {
        Long id = GdwManyA.withTransaction {
            GdwManyB one = new GdwManyB(name: 'b1').save(failOnError: true)
            GdwManyB two = new GdwManyB(name: 'b2').save(failOnError: true)
            new GdwManyA(bs: [one, two] as Set).save(failOnError: true, flush: true).id
        }
        List first = GdwManyA.withNewSession { GdwManyA.get(id).bs*.name.sort() }
        GdwManyA.withTransaction {
            GdwManyB three = new GdwManyB(name: 'b3').save(failOnError: true)
            GdwManyA owner = GdwManyA.get(id)
            owner.bs = [GdwManyB.findByName('b2'), three] as Set
            owner.save(failOnError: true, flush: true)
        }
        return [first, GdwManyA.withNewSession { GdwManyA.get(id).bs*.name.sort() }]
    }

    private List hasOneCycle() {
        Long id = GdwHasOneOwner.withTransaction {
            GdwHasOneOwner owner = new GdwHasOneOwner(detail: new GdwHasOneDetail(label: 'd1'))
            owner.save(failOnError: true, flush: true).id
        }
        String first = GdwHasOneOwner.withNewSession { GdwHasOneOwner.get(id).detail?.label }
        GdwHasOneOwner.withTransaction {
            GdwHasOneOwner owner = GdwHasOneOwner.get(id)
            owner.detail.label = 'd2'
            owner.save(failOnError: true, flush: true)
        }
        return [first, GdwHasOneOwner.withNewSession { GdwHasOneOwner.get(id).detail?.label }]
    }
}

@Entity
class GdwTagsOwner {
    Set<String> tags
    static hasMany = [tags: String]
    static mapping = {
        tags insertable: false
    }
}

@Entity
class GdwUpdTagsOwner {
    Set<String> tags
    static hasMany = [tags: String]
    static mapping = {
        tags updatable: false
    }
}

@Entity
class GdwItem {
    String name
}

@Entity
class GdwItemsOwner {
    Set<GdwItem> items
    static hasMany = [items: GdwItem]
    static mapping = {
        items insertable: false
    }
}

class GdwAddress {
    String street
}

@Entity
class GdwEmbeddedOwner {
    GdwAddress home
    static embedded = ['home']
    static mapping = {
        home insertable: false
    }
}

@Entity
class GdwUpdEmbeddedOwner {
    GdwAddress home
    static embedded = ['home']
    static mapping = {
        home updatable: false
    }
}

@Entity
class GdwManyA {
    Set<GdwManyB> bs
    static hasMany = [bs: GdwManyB]
    static mapping = {
        bs insertable: false
    }
}

@Entity
class GdwManyB {
    String name
    Set<GdwManyA> owners
    static hasMany = [owners: GdwManyA]
    static belongsTo = [GdwManyA]
}

@Entity
class GdwHasOneOwner {
    static hasOne = [detail: GdwHasOneDetail]
    static mapping = {
        detail insertable: false
    }
}

@Entity
class GdwHasOneDetail {
    String label
    GdwHasOneOwner owner
}
