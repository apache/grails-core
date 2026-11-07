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

package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.neo4j.Relationship
import org.apache.grails.data.neo4j.core.Neo4jGormDatastoreSpec
import spock.lang.Issue

/**
 * An association is stored as a relationship, so isNull and isNotNull on it test for the absence and presence of
 * the relationship and not for a property of the node.
 */
@Issue('16584')
class IsNullAssociationSpec extends Neo4jGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(NullTestFolder, NullTestKind, NullTestAddress, NullTestOwner, NullTestPet,
                NullTestShelf, NullTestItem, NullTestTagged, NullTestPerson, NullTestMate, NullTestBond)
    }

    private void seed() {
        NullTestFolder other = new NullTestFolder(name: 'Other').save(flush: true, failOnError: true)
        new NullTestFolder(name: 'Root', parent: other).save(flush: true, failOnError: true)
        NullTestFolder.withSession { it.clear() }
    }

    void "isNull on an association matches the nodes without the relationship"() {
        given:
        seed()

        when:
        List<NullTestFolder> folders = NullTestFolder.createCriteria().list { isNull('parent') }

        then:
        folders*.name == ['Other']
    }

    void "isNotNull on an association matches the nodes with the relationship"() {
        given:
        seed()

        when:
        List<NullTestFolder> folders = NullTestFolder.createCriteria().list { isNotNull('parent') }

        then:
        folders*.name == ['Root']
    }

    void "isNull on an association combined with a property criterion"() {
        given:
        seed()

        when:
        List<NullTestFolder> folders = NullTestFolder.createCriteria().list {
            isNull('parent')
            eq('name', 'Root')
        }

        then:
        folders.isEmpty()
    }

    void "isNull on a plain property still tests the node property"() {
        given:
        new NullTestFolder(name: 'Named').save(flush: true, failOnError: true)
        new NullTestFolder(name: null).save(flush: true, failOnError: true)
        NullTestFolder.withSession { it.clear() }

        when:
        List<NullTestFolder> folders = NullTestFolder.createCriteria().list { isNull('name') }

        then:
        folders.size() == 1
        folders[0].name == null
    }

    private void seedPets() {
        NullTestKind kind = new NullTestKind(name: 'K').save(flush: true, failOnError: true)
        NullTestAddress address = new NullTestAddress(street: 'Main').save(flush: true, failOnError: true)
        NullTestOwner withAll = new NullTestOwner(name: 'A', kind: 'cat person', address: address).save(flush: true, failOnError: true)
        NullTestOwner withNone = new NullTestOwner(name: 'B').save(flush: true, failOnError: true)
        new NullTestPet(name: 'one', owner: withAll, kind: kind).save(flush: true, failOnError: true)
        new NullTestPet(name: 'two', owner: withNone, kind: kind).save(flush: true, failOnError: true)
        new NullTestPet(name: 'three').save(flush: true, failOnError: true)
        NullTestPet.withSession { it.clear() }
    }

    void "a property nested in an association block is looked up on the associated entity: #label"() {
        given:
        seedPets()

        expect: 'the property is a plain property of the owner, although the pet has an association of that name'
        names(NullTestPet.createCriteria().list(criteria)) == expected

        where:
        label                 | criteria                          | expected
        'isNull of a scalar'  | { owner { isNull('kind') } }      | ['two']
        'isNotNull of scalar' | { owner { isNotNull('kind') } }   | ['one']
        'isNull association'  | { owner { isNull('address') } }   | ['two']
        'isNotNull association' | { owner { isNotNull('address') } } | ['one']
    }

    void "an association of the root entity is still a relationship test beside a nested block"() {
        given:
        seedPets()

        expect:
        names(NullTestPet.createCriteria().list { isNull('kind') }) == ['three']
        names(NullTestPet.createCriteria().list { isNotNull('kind'); owner { isNull('kind') } }) == ['two']
    }

    void "isNull and isNotNull on the many-to-one side of a bidirectional association"() {
        given:
        NullTestShelf shelf = new NullTestShelf(name: 's1').save(flush: true, failOnError: true)
        new NullTestShelf(name: 's2').save(flush: true, failOnError: true)
        shelf.addToItems(new NullTestItem(name: 'i1'))
        shelf.addToItems(new NullTestItem(name: 'i2'))
        shelf.save(flush: true, failOnError: true)
        new NullTestItem(name: 'i3').save(flush: true, failOnError: true)
        NullTestItem.withSession { it.clear() }

        expect:
        names(NullTestItem.createCriteria().list { isNull('shelf') }) == ['i3']
        names(NullTestItem.createCriteria().list { isNotNull('shelf') }) == ['i1', 'i2']
    }

    void "isNull and isNotNull on a to-many association"() {
        given:
        NullTestShelf shelf = new NullTestShelf(name: 's1').save(flush: true, failOnError: true)
        new NullTestShelf(name: 's2').save(flush: true, failOnError: true)
        shelf.addToItems(new NullTestItem(name: 'i1'))
        shelf.save(flush: true, failOnError: true)
        NullTestShelf.withSession { it.clear() }

        expect:
        names(NullTestShelf.createCriteria().list { isNull('items') }) == ['s2']
        names(NullTestShelf.createCriteria().list { isNotNull('items') }) == ['s1']
    }

    void "isNull on a basic collection still tests the node property"() {
        given:
        new NullTestTagged(name: 'tagged', tags: ['a']).save(flush: true, failOnError: true)
        new NullTestTagged(name: 'bare').save(flush: true, failOnError: true)
        NullTestTagged.withSession { it.clear() }

        expect:
        names(NullTestTagged.createCriteria().list { isNull('tags') }) == ['bare']
        names(NullTestTagged.createCriteria().list { isNotNull('tags') }) == ['tagged']
    }

    void "an embedded component is not an association: its null test is a property test and does not throw"() {
        given:
        new NullTestPerson(name: 'p1', home: new NullTestHome(city: 'X')).save(flush: true, failOnError: true)
        new NullTestPerson(name: 'p2').save(flush: true, failOnError: true)
        NullTestPerson.withSession { it.clear() }

        when:
        List<NullTestPerson> nulls = NullTestPerson.createCriteria().list { isNull('home') }
        List<NullTestPerson> notNulls = NullTestPerson.createCriteria().list { isNotNull('home') }

        then:
        noExceptionThrown()
        nulls.size() + notNulls.size() == 2
    }

    void "a null test on a scalar of a relationship entity block tests the relationship's property, not the node's association of that name"() {
        given: 'a mate with a spouse association, and bonds whose own spouse property is a plain string'
        NullTestMate spouse = new NullTestMate(name: 'spouse').save(flush: true, failOnError: true)
        NullTestMate owner = new NullTestMate(name: 'owner', spouse: spouse).save(flush: true, failOnError: true)
        NullTestMate friend = new NullTestMate(name: 'friend').save(flush: true, failOnError: true)
        new NullTestBond(from: owner, to: friend, spouse: 'set').save(flush: true)
        new NullTestBond(from: owner, to: spouse).save(flush: true)
        NullTestMate.withSession { it.clear() }

        expect:
        NullTestMate.createCriteria().list { connections { isNull('spouse') } }*.name == ['spouse']
        NullTestMate.createCriteria().list { connections { isNotNull('spouse') } }*.name == ['friend']
    }

    private static List<String> names(List<?> entities) {
        entities*.name.sort()
    }
}

@Entity
class NullTestFolder {
    String name
    NullTestFolder parent

    static constraints = {
        name nullable: true
        parent nullable: true
    }
}

@Entity
class NullTestKind {
    String name
}

@Entity
class NullTestAddress {
    String street
}

@Entity
class NullTestOwner {
    String name
    String kind
    NullTestAddress address

    static constraints = {
        kind nullable: true
        address nullable: true
    }
}

@Entity
class NullTestPet {
    String name
    NullTestOwner owner
    NullTestKind kind

    static constraints = {
        owner nullable: true
        kind nullable: true
    }
}

@Entity
class NullTestShelf {
    String name
    static hasMany = [items: NullTestItem]
}

@Entity
class NullTestItem {
    String name
    NullTestShelf shelf
    static belongsTo = [shelf: NullTestShelf]

    static constraints = {
        shelf nullable: true
    }
}

@Entity
class NullTestTagged {
    String name
    List<String> tags

    static constraints = {
        tags nullable: true
    }
}

class NullTestHome {
    String city
}

@Entity
class NullTestPerson {
    String name
    NullTestHome home
    static embedded = ['home']

    static constraints = {
        home nullable: true
    }
}

@Entity
class NullTestMate {
    String name
    NullTestMate spouse
    static hasMany = [connections: NullTestBond]

    static constraints = {
        spouse nullable: true
    }
}

@Entity
class NullTestBond implements Relationship<NullTestMate, NullTestMate> {
    String spouse

    static constraints = {
        spouse nullable: true
    }
}
