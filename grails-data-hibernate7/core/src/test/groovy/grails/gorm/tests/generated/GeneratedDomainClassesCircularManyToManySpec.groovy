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
import java.sql.Statement
import java.util.concurrent.atomic.AtomicInteger

import grails.gorm.annotation.Entity
import org.hibernate.mapping.Collection as HibernateCollection
import org.hibernate.mapping.Table
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.orm.hibernate.HibernateDatastore

/**
 * A self-referencing many-to-many (one entity on both sides) names its join table columns as the classic binding of Grails 8 did.
 * Classic binding renamed the join key of one side while it bound the other, so which side keeps the default key name depends on
 * the order the two collections are bound in, which is the order of the properties of the entity. A user whose database was created
 * by Grails 8 must not see {@code update} add new columns beside the old ones, and a relationship written then must still be read.
 * The join tables stated here are the ones classic binding created, recorded before it was removed.
 */
class GeneratedDomainClassesCircularManyToManySpec extends Specification {

    static final AtomicInteger BOOTS = new AtomicInteger()

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(List<Class> group) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'     : "jdbc:h2:mem:cmmCircular${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate': 'create-drop',
        ], group as Class[])
        return datastore
    }

    /** The collections and the join tables the boot model holds, as plain data. */
    private Map<String, Object> schema(List<Class> group) {
        HibernateDatastore booted = boot(group)
        Map<String, Object> result = [:]
        for (HibernateCollection collection : booted.metadata.collectionBindings) {
            Table table = collection.collectionTable
            result[collection.role] = [
                    table      : table.name,
                    inverse    : collection.inverse,
                    key        : collection.key.columns*.name,
                    element    : collection.element.columns*.name,
                    columns    : table.columns*.name,
                    primaryKey : table.primaryKey?.columns*.name,
                    foreignKeys: table.foreignKeys.values().collect { it.columns*.name }.sort { it.toString() },
            ]
        }
        return result
    }

    @Unroll
    void "the join tables of #label have the column names classic binding gave them"() {
        when:
        Map<String, Object> generated = schema(group)

        then:
        generated == expected

        where:
        label << ['a bidirectional self many-to-many', 'the same with the properties declared the other way',
                  'the same with the properties and hasMany in two orders', 'the same with names that sort the other way',
                  'a self many-to-many inside a hierarchy', 'a superclass collection and a circular subclass one',
                  'a unidirectional self many-to-many', 'a first side that names its join key', 'a second side that names its join key']
        group << [[CmFollowers], [CmFollowersSwapped], [CmFollowersMixed], [CmZebras], [CmHierarchyRoot, CmHierarchySub], [CmMammal, CmDog],
                  [CmFriends], [CmKeyedFirst], [CmKeyedSecond]]
        expected << [
                ['grails.gorm.tests.generated.CmFollowers.following': [table: 'cm_followers_following', inverse: false, key: ['following_id'], element: ['followers_id'], columns: ['followers_id', 'following_id'], primaryKey: ['followers_id', 'following_id'], foreignKeys: [['followers_id'], ['following_id']]],
                 'grails.gorm.tests.generated.CmFollowers.followers': [table: 'cm_followers_followers', inverse: false, key: ['cm_followers_id'], element: ['following_id'], columns: ['cm_followers_id', 'following_id'], primaryKey: ['cm_followers_id', 'following_id'], foreignKeys: [['cm_followers_id'], ['following_id']]]],
                ['grails.gorm.tests.generated.CmFollowersSwapped.following': [table: 'cm_followers_swapped_following', inverse: false, key: ['cm_followers_swapped_id'], element: ['followers_id'], columns: ['cm_followers_swapped_id', 'followers_id'], primaryKey: ['cm_followers_swapped_id', 'followers_id'], foreignKeys: [['cm_followers_swapped_id'], ['followers_id']]],
                 'grails.gorm.tests.generated.CmFollowersSwapped.followers': [table: 'cm_followers_swapped_followers', inverse: false, key: ['followers_id'], element: ['following_id'], columns: ['followers_id', 'following_id'], primaryKey: ['followers_id', 'following_id'], foreignKeys: [['followers_id'], ['following_id']]]],
                ['grails.gorm.tests.generated.CmFollowersMixed.following': [table: 'cm_followers_mixed_following', inverse: false, key: ['cm_followers_mixed_id'], element: ['followers_id'], columns: ['cm_followers_mixed_id', 'followers_id'], primaryKey: ['cm_followers_mixed_id', 'followers_id'], foreignKeys: [['cm_followers_mixed_id'], ['followers_id']]],
                 'grails.gorm.tests.generated.CmFollowersMixed.followers': [table: 'cm_followers_mixed_followers', inverse: false, key: ['followers_id'], element: ['following_id'], columns: ['followers_id', 'following_id'], primaryKey: ['followers_id', 'following_id'], foreignKeys: [['followers_id'], ['following_id']]]],
                ['grails.gorm.tests.generated.CmZebras.zebras': [table: 'cm_zebras_zebras', inverse: false, key: ['cm_zebras_id'], element: ['apples_id'], columns: ['apples_id', 'cm_zebras_id'], primaryKey: ['apples_id', 'cm_zebras_id'], foreignKeys: [['apples_id'], ['cm_zebras_id']]],
                 'grails.gorm.tests.generated.CmZebras.apples': [table: 'cm_zebras_apples', inverse: false, key: ['apples_id'], element: ['zebras_id'], columns: ['apples_id', 'zebras_id'], primaryKey: ['apples_id', 'zebras_id'], foreignKeys: [['apples_id'], ['zebras_id']]]],
                ['grails.gorm.tests.generated.CmHierarchySub.pals': [table: 'cm_hierarchy_sub_pals', inverse: false, key: ['cm_hierarchy_sub_id'], element: ['pal_of_id'], columns: ['cm_hierarchy_sub_id', 'pal_of_id'], primaryKey: null, foreignKeys: [['cm_hierarchy_sub_id'], ['pal_of_id']]],
                 'grails.gorm.tests.generated.CmHierarchySub.palOf': [table: 'cm_hierarchy_sub_pal_of', inverse: false, key: ['pal_of_id'], element: ['pals_id'], columns: ['pal_of_id', 'pals_id'], primaryKey: null, foreignKeys: [['pal_of_id'], ['pals_id']]]],
                ['grails.gorm.tests.generated.CmMammal.dogs': [table: 'cm_mammal_dogs', inverse: false, key: ['cm_mammal_id'], element: ['animals_id'], columns: ['animals_id', 'cm_mammal_id'], primaryKey: null, foreignKeys: [['animals_id'], ['cm_mammal_id']]],
                 'grails.gorm.tests.generated.CmDog.animals': [table: 'cm_mammal_dogs', inverse: true, key: ['animals_id'], element: ['cm_mammal_id'], columns: ['animals_id', 'cm_mammal_id'], primaryKey: null, foreignKeys: [['animals_id'], ['cm_mammal_id']]]],
                ['grails.gorm.tests.generated.CmFriends.friends': [table: 'cm_friends_cm_friends', inverse: false, key: ['cm_friends_friends_id'], element: ['cm_friends_id'], columns: ['cm_friends_friends_id', 'cm_friends_id'], primaryKey: null, foreignKeys: [['cm_friends_friends_id'], ['cm_friends_id']]]],
                ['grails.gorm.tests.generated.CmKeyedFirst.following': [table: 'cm_keyed_first_following', inverse: false, key: ['following_id'], element: ['owner_ref'], columns: ['following_id', 'owner_ref'], primaryKey: ['following_id', 'owner_ref'], foreignKeys: [['following_id'], ['owner_ref']]],
                 'grails.gorm.tests.generated.CmKeyedFirst.followers': [table: 'cm_keyed_first_f', inverse: false, key: ['owner_ref'], element: ['following_id'], columns: ['following_id', 'owner_ref'], primaryKey: ['following_id', 'owner_ref'], foreignKeys: [['following_id'], ['owner_ref']]]],
                ['grails.gorm.tests.generated.CmKeyedSecond.following': [table: 'cm_keyed_second_f', inverse: false, key: ['owner_ref'], element: ['followers_id'], columns: ['followers_id', 'owner_ref'], primaryKey: ['followers_id', 'owner_ref'], foreignKeys: [['followers_id'], ['owner_ref']]],
                 'grails.gorm.tests.generated.CmKeyedSecond.followers': [table: 'cm_keyed_second_followers', inverse: false, key: ['cm_keyed_second_id'], element: ['owner_ref'], columns: ['cm_keyed_second_id', 'owner_ref'], primaryKey: ['cm_keyed_second_id', 'owner_ref'], foreignKeys: [['cm_keyed_second_id'], ['owner_ref']]]],
        ]
    }

    void "a non-circular owning side that names the key and the column of its join table gets those columns, and the inverse side shares them"() {
        when:
        Map<String, Object> generated = schema([CmNamedOwner, CmNamedOther])

        then:
        generated['grails.gorm.tests.generated.CmNamedOwner.others'].key == ['owner_ref']
        generated['grails.gorm.tests.generated.CmNamedOwner.others'].element == ['other_ref']
        generated['grails.gorm.tests.generated.CmNamedOther.owners'] == [table: 'cm_named_join', inverse: true, key: ['other_ref'], element: ['owner_ref'],
                                                                         columns: ['other_ref', 'owner_ref'], primaryKey: ['other_ref', 'owner_ref'], foreignKeys: [['other_ref'], ['owner_ref']]]
    }

    void "the side bound first keeps the default key name and the side bound second is named after its property"() {
        when:
        Map<String, Object> generated = schema([CmFollowers])

        then:
        generated['grails.gorm.tests.generated.CmFollowers.followers'].key == ['cm_followers_id']
        generated['grails.gorm.tests.generated.CmFollowers.followers'].element == ['following_id']
        generated['grails.gorm.tests.generated.CmFollowers.following'].key == ['following_id']
        generated['grails.gorm.tests.generated.CmFollowers.following'].element == ['followers_id']
        generated['grails.gorm.tests.generated.CmFollowers.followers'].primaryKey == ['cm_followers_id', 'following_id']
    }

    void "which side is bound first follows the order of the properties"() {
        when:
        Map<String, Object> generated = schema([CmFollowersSwapped])

        then:
        generated['grails.gorm.tests.generated.CmFollowersSwapped.following'].key == ['cm_followers_swapped_id']
        generated['grails.gorm.tests.generated.CmFollowersSwapped.followers'].key == ['followers_id']
    }

    private static Map<String, List<String>> rows(HibernateDatastore booted, String sql) {
        Map<String, List<String>> result = new TreeMap<String, List<String>>()
        booted.connectionSources.defaultConnectionSource.dataSource.connection.withCloseable { Connection connection ->
            connection.createStatement().withCloseable { Statement statement ->
                statement.executeQuery(sql).withCloseable { ResultSet rs ->
                    while (rs.next()) {
                        result.computeIfAbsent(rs.getString(1)) { new ArrayList<String>() }.add(rs.getString(2))
                    }
                }
            }
        }
        return result
    }

    /** Writes a relationship through one side, then reads it through both sides, in a fresh session. */
    private Map<String, Object> behaviour() {
        HibernateDatastore booted = boot([CmFollowers])
        Map<String, Object> result = [:]
        CmFollowers.withTransaction {
            CmFollowers a = new CmFollowers(name: 'a').save(failOnError: true)
            CmFollowers b = new CmFollowers(name: 'b').save(failOnError: true)
            a.addToFollowers(b)
            a.save(flush: true, failOnError: true)
        }
        CmFollowers.withNewSession {
            result.followersOfA = CmFollowers.findByName('a').followers*.name.sort()
            result.followingOfA = CmFollowers.findByName('a').following*.name.sort()
            result.followersOfB = CmFollowers.findByName('b').followers*.name.sort()
            result.followingOfB = CmFollowers.findByName('b').following*.name.sort()
        }
        result.joinTables = ['cm_followers_followers', 'cm_followers_following'].collectEntries { String table ->
            [table, rows(booted, "select 'x', count(*) from ${table}".toString())['x']]
        }
        return result
    }

    void "a relationship written through one side is read through the sides as classic binding read it"() {
        when:
        Map<String, Object> generated = behaviour()

        then:
        generated.followersOfA == ['b']
        generated.followingOfB == ['a']
        generated.followingOfA.isEmpty()
        generated.followersOfB.isEmpty()
        generated.joinTables == ['cm_followers_followers': ['1'], 'cm_followers_following': ['1']]
    }
}

@Entity
class CmFollowers {
    String name
    Set<CmFollowers> followers
    Set<CmFollowers> following
    static hasMany = [followers: CmFollowers, following: CmFollowers]
    static mappedBy = [followers: 'following', following: 'followers']
    static belongsTo = [CmFollowers]
}

@Entity
class CmFollowersSwapped {
    String name
    Set<CmFollowersSwapped> following
    Set<CmFollowersSwapped> followers
    static hasMany = [following: CmFollowersSwapped, followers: CmFollowersSwapped]
    static mappedBy = [followers: 'following', following: 'followers']
    static belongsTo = [CmFollowersSwapped]
}

@Entity
class CmFollowersMixed {
    String name
    Set<CmFollowersMixed> following
    Set<CmFollowersMixed> followers
    static hasMany = [followers: CmFollowersMixed, following: CmFollowersMixed]
    static mappedBy = [followers: 'following', following: 'followers']
    static belongsTo = [CmFollowersMixed]
}

@Entity
class CmZebras {
    String name
    Set<CmZebras> zebras
    Set<CmZebras> apples
    static hasMany = [zebras: CmZebras, apples: CmZebras]
    static mappedBy = [zebras: 'apples', apples: 'zebras']
    static belongsTo = [CmZebras]
}

@Entity
class CmHierarchyRoot {
    String name
}

@Entity
class CmHierarchySub extends CmHierarchyRoot {
    Set<CmHierarchySub> pals
    Set<CmHierarchySub> palOf
    static hasMany = [pals: CmHierarchySub, palOf: CmHierarchySub]
    static mappedBy = [pals: 'palOf', palOf: 'pals']
    static belongsTo = [CmHierarchySub]
}

@Entity
class CmMammal {
    String name
    static hasMany = [dogs: CmDog]
}

@Entity
class CmDog extends CmMammal {
    static hasMany = [animals: CmMammal]
    static belongsTo = [CmMammal]
}

@Entity
class CmFriends {
    String name
    Set<CmFriends> friends
    static hasMany = [friends: CmFriends]
    static belongsTo = [CmFriends]
}

@Entity
class CmKeyedFirst {
    String name
    Set<CmKeyedFirst> followers
    Set<CmKeyedFirst> following
    static hasMany = [followers: CmKeyedFirst, following: CmKeyedFirst]
    static mappedBy = [followers: 'following', following: 'followers']
    static belongsTo = [CmKeyedFirst]
    static mapping = {
        followers joinTable: [name: 'cm_keyed_first_f', key: 'owner_ref', column: 'other_ref']
    }
}

@Entity
class CmKeyedSecond {
    String name
    Set<CmKeyedSecond> followers
    Set<CmKeyedSecond> following
    static hasMany = [followers: CmKeyedSecond, following: CmKeyedSecond]
    static mappedBy = [followers: 'following', following: 'followers']
    static belongsTo = [CmKeyedSecond]
    static mapping = {
        following joinTable: [name: 'cm_keyed_second_f', key: 'owner_ref', column: 'other_ref']
    }
}

@Entity
class CmNamedOwner {
    String name
    Set<CmNamedOther> others
    static hasMany = [others: CmNamedOther]
    static mapping = {
        others joinTable: [name: 'cm_named_join', key: 'owner_ref', column: 'other_ref']
    }
}

@Entity
class CmNamedOther {
    String name
    Set<CmNamedOwner> owners
    static hasMany = [owners: CmNamedOwner]
    static belongsTo = [owners: CmNamedOwner]
}
