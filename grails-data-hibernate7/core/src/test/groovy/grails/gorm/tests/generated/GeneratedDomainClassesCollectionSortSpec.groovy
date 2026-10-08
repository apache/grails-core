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
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.orm.hibernate.HibernateDatastore

/**
 * The default {@code sort} of a bidirectional {@code hasMany}. The domain binder orders a set and a map by the property, also when it
 * is an association (it orders by the foreign key), and a list stays in the order of its index column, so the sort changes nothing for
 * it. The generated mode accepts all three the same way instead of refusing the list, the map and the sort by an association.
 */
class GeneratedDomainClassesCollectionSortSpec extends Specification {

    static final AtomicInteger BOOTS = new AtomicInteger()

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(List<Class> group, boolean generated) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'                  : "jdbc:h2:mem:gcs${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'             : 'create-drop',
                'hibernate.generatedDomainClasses': generated,
        ], group as Class[])
        return datastore
    }

    @Unroll
    void "#label: the elements come back in the same order in both modes"() {
        when:
        Map<Boolean, Object> results = [false, true].collectEntries { boolean generated ->
            boot(group, generated)
            [(generated): this."${cycle}"()]
        }

        then:
        results[true] == results[false]
        results[true] == expected

        where:
        label                              | group                          | cycle          | expected
        'a list with a sort'               | [GcsListOwner, GcsListKid]     | 'listCycle'    | ['b', 'a', 'c']
        'a map with a sort'                | [GcsMapOwner, GcsMapKid]       | 'mapCycle'     | [k2: 'a', k1: 'b']
        'a set sorted by an association'   | [GcsRefOwner, GcsRefKid, GcsRef] | 'refCycle'   | ['y', 'x']
    }

    private List listCycle() {
        GcsListOwner.withTransaction {
            GcsListOwner owner = new GcsListOwner()
            ['b', 'a', 'c'].each { owner.addToKids(new GcsListKid(name: it)) }
            owner.save(failOnError: true, flush: true)
        }
        return GcsListOwner.withNewSession { GcsListOwner.list().first().kids*.name }
    }

    private Map mapCycle() {
        GcsMapOwner.withTransaction {
            GcsMapOwner owner = new GcsMapOwner()
            owner.kids = [k1: new GcsMapKid(name: 'b'), k2: new GcsMapKid(name: 'a')]
            owner.save(failOnError: true, flush: true)
        }
        return GcsMapOwner.withNewSession { GcsMapOwner.list().first().kids.collectEntries { String key, GcsMapKid kid -> [(key): kid.name] } }
    }

    private List refCycle() {
        GcsRefOwner.withTransaction {
            GcsRef first = new GcsRef(label: 'r1').save(failOnError: true)
            GcsRef second = new GcsRef(label: 'r2').save(failOnError: true)
            GcsRefOwner owner = new GcsRefOwner()
            owner.addToKids(new GcsRefKid(name: 'x', ref: second))
            owner.addToKids(new GcsRefKid(name: 'y', ref: first))
            owner.save(failOnError: true, flush: true)
        }
        return GcsRefOwner.withNewSession { GcsRefOwner.list().first().kids*.name }
    }
}

@Entity
class GcsListOwner {
    List<GcsListKid> kids

    static hasMany = [kids: GcsListKid]

    static mapping = {
        kids sort: 'name'
    }
}

@Entity
class GcsListKid {
    String name
    GcsListOwner owner

    static belongsTo = [owner: GcsListOwner]
}

@Entity
class GcsMapOwner {
    Map<String, GcsMapKid> kids

    static hasMany = [kids: GcsMapKid]

    static mapping = {
        kids sort: 'name'
    }
}

@Entity
class GcsMapKid {
    String name
    GcsMapOwner owner

    static belongsTo = [owner: GcsMapOwner]
}

@Entity
class GcsRef {
    String label
}

@Entity
class GcsRefOwner {
    Set<GcsRefKid> kids

    static hasMany = [kids: GcsRefKid]

    static mapping = {
        kids sort: 'ref'
    }
}

@Entity
class GcsRefKid {
    String name
    GcsRef ref
    GcsRefOwner owner

    static belongsTo = [owner: GcsRefOwner]
}
