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

import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types
import java.util.concurrent.atomic.AtomicInteger

import grails.gorm.annotation.Entity
import org.hibernate.mapping.Table
import org.hibernate.type.descriptor.WrapperOptions
import org.hibernate.usertype.UserType
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.orm.hibernate.HibernateDatastore

/**
 * Mappings that say more than the domain binder uses, and that it boots all the same: a join table that names a composite key (the
 * binder uses the first key column and ignores the others), a {@code type} that names a Java class and not a type (the binder looks
 * the class name up among the types registered for Java classes), and a property of a class GORM does not know and the mapping gives no
 * type (the binder lets Hibernate pick the type, which is binary for a serializable class). The generated mode boots them with the same
 * columns instead of refusing them.
 */
class GeneratedDomainClassesBinderLeniencySpec extends Specification {

    static final AtomicInteger BOOTS = new AtomicInteger()

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(List<Class> group, boolean generated) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'                  : "jdbc:h2:mem:gbl${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
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
                List<String> key = table.primaryKey?.columns*.name ?: []
                result[table.name] = [
                        columns    : table.columns.collectEntries { org.hibernate.mapping.Column column ->
                            [(column.name): "${column.getSqlType(booted.metadata)}${column.nullable && !key.contains(column.name) ? '' : ' not null'}".toString()]
                        },
                        primaryKey : key.sort(),
                        foreignKeys: table.foreignKeys.values().collect { "${it.columns*.name} -> ${it.referencedTable.name}".toString() }.sort(),
                ]
            }
        }
        return result
    }

    @Unroll
    void "#label: the generated mode creates the tables and columns of the domain binder"() {
        when:
        Map<String, Map> binder = schema(group, false)
        Map<String, Map> generated = schema(group, true)

        then:
        generated == binder
        generated[table].columns.keySet() == columns as Set

        where:
        label                                              | group                      | table             | columns
        'a join table of values that names two key columns'  | [GblJoinKeyTags]           | 'gbl_join_tags'   | ['a', 't']
        'a join table of entities that names two key columns' | [GblJoinKeyItems, GblItem] | 'gbl_join_items'  | ['a', 'gbl_item_id']
        'a many-to-many that names two key columns'         | [GblJoinKeyA, GblJoinKeyB] | 'gbl_join_ab'     | ['k1', 'bid']
        'a type that names a Java class'                    | [GblClassTypes]            | 'gbl_class_types' | ['id', 'version', 'total', 'stamp', 'name2']
        'a property of a serializable class with no type'   | [GblSerializable]          | 'gbl_serializable' | ['id', 'version', 'thing']
        'an embedded property that is given a user type'    | [GblUserTyped]             | 'gbl_user_typed'  | ['id', 'version', 'home']
        'an embedded type that extends a domain class'      | [GblEmbBase, GblEmbOwner]  | 'gbl_emb_owner'   | ['id', 'version', 'emb_id', 'emb_a', 'emb_b']
    }

    @Unroll
    void "#label: the rows are stored, read back and changed in both modes"() {
        when:
        Map<Boolean, List> results = [false, true].collectEntries { boolean generated ->
            boot(group, generated)
            [(generated): this."${cycle}"()]
        }

        then:
        results[true] == results[false]
        results[true] == expected

        where:
        label                                              | group                      | cycle            | expected
        'a join table of values that names two key columns'  | [GblJoinKeyTags]           | 'tagsCycle'      | [['a', 'b'], ['c']]
        'a join table of entities that names two key columns' | [GblJoinKeyItems, GblItem] | 'itemsCycle'     | [['one', 'two'], ['three']]
        'a many-to-many that names two key columns'         | [GblJoinKeyA, GblJoinKeyB] | 'manyCycle'      | [['b1', 'b2'], ['b3']]
        'a type that names a Java class'                    | [GblClassTypes]            | 'classTypeCycle' | [null, 'text']
        'a property of a serializable class with no type'   | [GblSerializable]          | 'serialCycle'    | ['first', 'second']
        'an embedded property that is given a user type'    | [GblUserTyped]             | 'userTypedCycle' | ['STREET', 'AVENUE']
        'an embedded type that extends a domain class'      | [GblEmbBase, GblEmbOwner]  | 'embeddedSubCycle' | ['a1/b1', 'a2/b2']
    }

    private List tagsCycle() {
        Long id = GblJoinKeyTags.withTransaction { new GblJoinKeyTags(tags: ['a', 'b'] as Set).save(failOnError: true, flush: true).id }
        List first = GblJoinKeyTags.withNewSession { GblJoinKeyTags.get(id).tags.sort() }
        GblJoinKeyTags.withTransaction { GblJoinKeyTags owner = GblJoinKeyTags.get(id); owner.tags = ['c'] as Set; owner.save(failOnError: true, flush: true) }
        return [first, GblJoinKeyTags.withNewSession { GblJoinKeyTags.get(id).tags.sort() }]
    }

    private List itemsCycle() {
        Long id = GblJoinKeyItems.withTransaction {
            new GblJoinKeyItems(items: [new GblItem(name: 'one').save(failOnError: true), new GblItem(name: 'two').save(failOnError: true)] as Set)
                    .save(failOnError: true, flush: true).id
        }
        List first = GblJoinKeyItems.withNewSession { GblJoinKeyItems.get(id).items*.name.sort() }
        GblJoinKeyItems.withTransaction {
            GblJoinKeyItems owner = GblJoinKeyItems.get(id)
            owner.items = [new GblItem(name: 'three').save(failOnError: true)] as Set
            owner.save(failOnError: true, flush: true)
        }
        return [first, GblJoinKeyItems.withNewSession { GblJoinKeyItems.get(id).items*.name.sort() }]
    }

    private List manyCycle() {
        Long id = GblJoinKeyA.withTransaction {
            new GblJoinKeyA(bs: [new GblJoinKeyB(name: 'b1').save(failOnError: true), new GblJoinKeyB(name: 'b2').save(failOnError: true)] as Set)
                    .save(failOnError: true, flush: true).id
        }
        List first = GblJoinKeyA.withNewSession { GblJoinKeyA.get(id).bs*.name.sort() }
        GblJoinKeyA.withTransaction {
            GblJoinKeyA owner = GblJoinKeyA.get(id)
            owner.bs = [new GblJoinKeyB(name: 'b3').save(failOnError: true)] as Set
            owner.save(failOnError: true, flush: true)
        }
        return [first, GblJoinKeyA.withNewSession { GblJoinKeyA.get(id).bs*.name.sort() }]
    }

    private List classTypeCycle() {
        Long id = GblClassTypes.withTransaction { new GblClassTypes().save(failOnError: true, flush: true).id }
        Object first = GblClassTypes.withNewSession { GblClassTypes.get(id).stamp }
        GblClassTypes.withTransaction { GblClassTypes row = GblClassTypes.get(id); row.name2 = 'text'; row.save(failOnError: true, flush: true) }
        return [first, GblClassTypes.withNewSession { GblClassTypes.get(id).name2 }]
    }

    private List userTypedCycle() {
        Long id = GblUserTyped.withTransaction { new GblUserTyped(home: new GblAddress(street: 'street')).save(failOnError: true, flush: true).id }
        String first = GblUserTyped.withNewSession { GblUserTyped.get(id).home.street }
        GblUserTyped.withTransaction { GblUserTyped row = GblUserTyped.get(id); row.home = new GblAddress(street: 'avenue'); row.save(failOnError: true, flush: true) }
        return [first, GblUserTyped.withNewSession { GblUserTyped.get(id).home.street }]
    }

    private List embeddedSubCycle() {
        Long id = GblEmbOwner.withTransaction { new GblEmbOwner(emb: new GblEmbSub(a: 'a1', b: 'b1')).save(failOnError: true, flush: true).id }
        String first = GblEmbOwner.withNewSession { GblEmbOwner owner = GblEmbOwner.get(id); "${owner.emb.a}/${owner.emb.b}".toString() }
        GblEmbOwner.withTransaction { GblEmbOwner owner = GblEmbOwner.get(id); owner.emb = new GblEmbSub(a: 'a2', b: 'b2'); owner.save(failOnError: true, flush: true) }
        return [first, GblEmbOwner.withNewSession { GblEmbOwner owner = GblEmbOwner.get(id); "${owner.emb.a}/${owner.emb.b}".toString() }]
    }

    private List serialCycle() {
        Long id = GblSerializable.withTransaction { new GblSerializable(thing: new GblThing(a: 'first')).save(failOnError: true, flush: true).id }
        String first = GblSerializable.withNewSession { GblSerializable.get(id).thing.a }
        GblSerializable.withTransaction { GblSerializable row = GblSerializable.get(id); row.thing = new GblThing(a: 'second'); row.save(failOnError: true, flush: true) }
        return [first, GblSerializable.withNewSession { GblSerializable.get(id).thing.a }]
    }
}

@Entity
class GblItem {
    String name
}

@Entity
class GblJoinKeyTags {
    Set<String> tags

    static hasMany = [tags: String]

    static mapping = {
        tags joinTable: [name: 'gbl_join_tags', key: ['a', 'b'], column: 't']
    }
}

@Entity
class GblJoinKeyItems {
    Set<GblItem> items

    static hasMany = [items: GblItem]

    static mapping = {
        items joinTable: [name: 'gbl_join_items', key: ['a', 'b']]
    }
}

@Entity
class GblJoinKeyA {
    Set<GblJoinKeyB> bs

    static hasMany = [bs: GblJoinKeyB]

    static mapping = {
        bs joinTable: [name: 'gbl_join_ab', key: ['k1', 'k2'], column: 'bid']
    }
}

@Entity
class GblJoinKeyB {
    String name
    Set<GblJoinKeyA> owners

    static hasMany = [owners: GblJoinKeyA]
    static belongsTo = [GblJoinKeyA]
}

@Entity
class GblClassTypes {
    Integer total
    String stamp
    String name2

    static mapping = {
        total type: 'java.lang.String'
        stamp type: 'java.util.Date'
    }
}

class GblThing implements Serializable {
    String a
}

@Entity
class GblSerializable {
    GblThing thing
}

class GblAddress {
    String street
}

class GblAddressType implements UserType<GblAddress> {

    @Override
    int getSqlType() {
        return Types.VARCHAR
    }

    @Override
    Class<GblAddress> returnedClass() {
        return GblAddress
    }

    @Override
    boolean equals(GblAddress x, GblAddress y) {
        return x?.street == y?.street
    }

    @Override
    int hashCode(GblAddress x) {
        return x?.street.hashCode() ?: 0
    }

    @Override
    GblAddress nullSafeGet(ResultSet rs, int position, WrapperOptions options) throws SQLException {
        String street = rs.getString(position)
        return street == null ? null : new GblAddress(street: street.toLowerCase().toUpperCase())
    }

    @Override
    void nullSafeSet(PreparedStatement st, GblAddress value, int index, WrapperOptions options) throws SQLException {
        st.setString(index, value?.street?.toUpperCase())
    }

    @Override
    GblAddress deepCopy(GblAddress value) {
        return value == null ? null : new GblAddress(street: value.street)
    }

    @Override
    boolean isMutable() {
        return true
    }

    @Override
    Serializable disassemble(GblAddress value) {
        return value?.street
    }

    @Override
    GblAddress assemble(Serializable cached, Object owner) {
        return cached == null ? null : new GblAddress(street: (String) cached)
    }
}

@Entity
class GblUserTyped {
    GblAddress home

    static embedded = ['home']

    static mapping = {
        home type: GblAddressType
    }
}

@Entity
class GblEmbBase {
    String a
}

class GblEmbSub extends GblEmbBase {
    String b
}

@Entity
class GblEmbOwner {
    GblEmbSub emb

    static embedded = ['emb']
}
