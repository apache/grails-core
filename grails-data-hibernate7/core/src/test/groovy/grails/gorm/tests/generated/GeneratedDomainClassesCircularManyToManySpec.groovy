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
 * A self-referencing many-to-many (one entity on both sides) over the generated classes names its join table columns as the
 * domain binder does. The binder renames the join key of one side while it binds the other, so which side keeps the default key
 * name depends on the order the two collections are bound in, which is the order of the properties of the entity. A user whose
 * database was created by the binder and who turns generated mode on must not see {@code update} add new columns beside the old
 * ones, and a relationship the binder wrote must still be read.
 */
class GeneratedDomainClassesCircularManyToManySpec extends Specification {

    static final AtomicInteger BOOTS = new AtomicInteger()

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(List<Class> group, boolean generated) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'                  : "jdbc:h2:mem:cmmCircular${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'             : 'create-drop',
                'hibernate.generatedDomainClasses': generated,
        ], group as Class[])
        return datastore
    }

    /** The collections and the join tables the boot model holds, as plain data. */
    private Map<String, Object> schema(List<Class> group, boolean generated) {
        HibernateDatastore booted = boot(group, generated)
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
    void "the join tables of #label have the column names the domain binder gives them"() {
        when:
        Map<String, Object> binder = schema(group, false)
        Map<String, Object> generated = schema(group, true)

        then:
        !binder.isEmpty()
        generated == binder

        where:
        label                                                    | group
        'a bidirectional self many-to-many'                      | [CmFollowers]
        'the same with the properties declared the other way'    | [CmFollowersSwapped]
        'the same with the properties and hasMany in two orders' | [CmFollowersMixed]
        'the same with names that sort the other way'            | [CmZebras]
        'a self many-to-many inside a hierarchy'                 | [CmHierarchyRoot, CmHierarchySub]
        'a superclass collection and a circular subclass one'    | [CmMammal, CmDog]
        'a unidirectional self many-to-many'                     | [CmFriends]
        'a first side that names its join key'                   | [CmKeyedFirst]
        'a second side that names its join key'                  | [CmKeyedSecond]
    }

    void "a non-circular owning side that names the key and the column of its join table gives the generated mode the same columns"() {
        when:
        Map<String, Object> binder = schema([CmNamedOwner, CmNamedOther], false)
        Map<String, Object> generated = schema([CmNamedOwner, CmNamedOther], true)

        then:
        generated == binder
        generated['grails.gorm.tests.generated.CmNamedOwner.others'].key == ['owner_ref']
        generated['grails.gorm.tests.generated.CmNamedOwner.others'].element == ['other_ref']
    }

    void "the side bound first keeps the default key name and the side bound second is named after its property"() {
        when:
        Map<String, Object> generated = schema([CmFollowers], true)

        then:
        generated['grails.gorm.tests.generated.CmFollowers.followers'].key == ['cm_followers_id']
        generated['grails.gorm.tests.generated.CmFollowers.followers'].element == ['following_id']
        generated['grails.gorm.tests.generated.CmFollowers.following'].key == ['following_id']
        generated['grails.gorm.tests.generated.CmFollowers.following'].element == ['followers_id']
        generated['grails.gorm.tests.generated.CmFollowers.followers'].primaryKey == ['cm_followers_id', 'following_id']
    }

    void "which side is bound first follows the order of the properties"() {
        when:
        Map<String, Object> generated = schema([CmFollowersSwapped], true)

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
    private Map<String, Object> behaviour(boolean generated) {
        HibernateDatastore booted = boot([CmFollowers], generated)
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

    void "a relationship written through one side is read through the sides exactly as with the domain binder"() {
        when:
        Map<String, Object> binder = behaviour(false)
        Map<String, Object> generated = behaviour(true)

        then:
        generated == binder
        binder.followersOfA == ['b']
        binder.followingOfB == ['a']
        binder.followingOfA.isEmpty()
        binder.followersOfB.isEmpty()
        binder.joinTables == ['cm_followers_followers': ['1'], 'cm_followers_following': ['1']]
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
