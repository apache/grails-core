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
package grails.gorm.tests.detachedcriteria

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.tests.HibernateGormDatastoreSpec
import spock.lang.Ignore

@SuppressWarnings("GrMethodMayBeStatic")
class DetachCriteriaSubquerySpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(User, Group, GroupAssignment, Organisation)
    }

    void "test detached associated criteria in subquery"() {

        setup:
        User supVisor = createUser('supervisor@company.com')
        User user1 = createUser('user1@company.com')
        User user2 = createUser('user2@company.com')

        Group group1 = createGroup('Group 1', supVisor)
        Group group2 = createGroup('Group 2', supVisor)

        assignGroup(user1, group1)
        assignGroup(user1, group2)

        when:
        String supervisorEmail = 'supervisor@company.com'
        DetachedCriteria<User> criteria = User.where {
            exists(
                    GroupAssignment.where {
                        eqProperty "user.id", "{alias}.id"
                        group.supervisor.email == supervisorEmail
                    }.id()
            )
        }
        List<User> result = criteria.list()

        then:
        noExceptionThrown()
        result.size() == 1
    }

    void "test executing detached criteria in sub-query multiple times"() {

        setup:
        Organisation orgA = new Organisation(name: "A")
        orgA.addToUsers(email: 'user1@a')
        orgA.addToUsers(email: 'user2@a')
        orgA.addToUsers(email: 'user3@a')
        orgA.save(flush: true)
        Organisation orgB = new Organisation(name: "B")
        orgB.addToUsers(email: 'user1@b')
        orgB.addToUsers(email: 'user2@b')
        orgB.save(flush: true)

        when:
        def orgDetachedCritera = Organisation.where { name == 'A' || name == 'B' }
        def organisations = orgDetachedCritera.list()
        DetachedCriteria<User> criteria = User.where { inList('organisation', orgDetachedCritera) }
        List<User> result = criteria.list()
        result = criteria.list()

        then:
        result.size() == 5
    }

    void "test that detached criteria subquery should create implicit alias instead of using this_"() {

        setup:
        User supVisor = createUser('supervisor@company.com')
        User user1 = createUser('user1@company.com')
        User user2 = createUser('user2@company.com')

        Group group1 = createGroup('Group 1', supVisor)
        Group group2 = createGroup('Group 2', supVisor)

        assignGroup(user1, group1)
        assignGroup(user1, group2)

        when:
        String supervisorEmail = 'supervisor@company.com'
        DetachedCriteria<User> criteria = User.where {
            exists(
                    GroupAssignment.where {
                        eqProperty "user.id", "{alias}.id"
                        group.supervisor.email == supervisorEmail
                    }.id()
            )
        }
        List<User> result = criteria.list()

        then:
        noExceptionThrown()
        result.size() == 1
    }

    void "test a lazy subquery keeps its restriction when used with inList"() {
        setup:
        new Organisation(name: 'A').save(flush: true)
        new Organisation(name: 'B').save(flush: true)

        when:
        DetachedCriteria<Organisation> subquery = Organisation.whereLazy { eq('name', 'A') }
        List<Organisation> result = Organisation.where { inList('name', subquery) }.list()

        then:
        result*.name == ['A']
        subquery.criteria.size() == 1
        subquery.projections.isEmpty()
    }

    void "test a lazy subquery keeps its restriction when used with gtAll"() {
        setup:
        new Organisation(name: 'A').save(flush: true)
        new Organisation(name: 'B').save(flush: true)

        when:
        DetachedCriteria<Organisation> subquery = Organisation.whereLazy { eq('name', 'A') }
        List<Organisation> result = Organisation.where { gtAll('name', subquery) }.list()

        then:
        result*.name == ['B']
        subquery.criteria.size() == 1
        subquery.projections.isEmpty()
    }

    void "test a buildLazy subquery keeps its restriction when used with notIn"() {
        setup:
        new Organisation(name: 'A').save(flush: true)
        new Organisation(name: 'B').save(flush: true)

        when:
        DetachedCriteria<Organisation> subquery = new DetachedCriteria<>(Organisation).buildLazy { eq('name', 'A') }
        List<Organisation> result = Organisation.where { notIn('name', subquery) }.list()

        then:
        result*.name == ['B']
        subquery.criteria.size() == 1
        subquery.projections.isEmpty()
    }

    void "test a lazy subquery keeps its restriction when used with exists and notExists"() {
        setup:
        new Organisation(name: 'A').save(flush: true)
        new Organisation(name: 'B').save(flush: true)

        when:
        DetachedCriteria<Organisation> matchingSubquery = Organisation.whereLazy { eq('name', 'A') }.id()
        DetachedCriteria<Organisation> nonMatchingSubquery = Organisation.whereLazy { eq('name', 'NON_EXISTENT') }.id()
        List<Organisation> existsResult = Organisation.where { exists(matchingSubquery) }.list()
        List<Organisation> notExistsResult = Organisation.where { notExists(nonMatchingSubquery) }.list()

        then:
        existsResult.size() == 2
        notExistsResult.size() == 2
        matchingSubquery.criteria.size() == 1
        nonMatchingSubquery.criteria.size() == 1
    }

    void "test a lazy criteria is materialized on a copy and keeps only what its closure added"() {
        setup:
        new Organisation(name: 'A').save(flush: true)
        new Organisation(name: 'B').save(flush: true)

        when:
        DetachedCriteria<Organisation> criteria = Organisation.whereLazy { eq('name', 'A') }
        DetachedCriteria<Organisation> materialized = criteria.materializeLazyCriteria()
        DetachedCriteria<Organisation> materializedAgain = materialized.materializeLazyCriteria()
        List<Organisation> fromOriginal = criteria.list()
        List<Organisation> fromCopy = materialized.list()

        then: 'the copy is a different criteria and both read the restriction and the original keeps only what its closure added'
        !materialized.is(criteria)
        materializedAgain.is(materialized)
        criteria.criteria.size() == 1
        materialized.criteria.size() == 1
        fromOriginal*.name == ['A']
        fromCopy*.name == ['A']
    }

    void "test a lazy criteria with defaultMax and defaultOffset applies them on execution"() {
        setup:
        new Organisation(name: 'A').save(flush: true)
        new Organisation(name: 'B').save(flush: true)
        new Organisation(name: 'C').save(flush: true)

        when:
        DetachedCriteria<Organisation> criteria = Organisation.whereLazy {
            ne('name', 'Z')
        }.sort('name').max(1).offset(1)
        List<Organisation> result = criteria.list()

        then:
        result*.name == ['B']
        criteria.criteria.size() == 1
    }

    void "test a lazy criteria invoked with a dynamic finder keeps only what its closure added"() {
        setup:
        new Organisation(name: 'A').save(flush: true)
        new Organisation(name: 'B').save(flush: true)

        when:
        DetachedCriteria<Organisation> criteria = Organisation.whereLazy { ne('name', 'Z') }
        Organisation found = criteria.findByName('A')

        then:
        found != null
        found.name == 'A'
        criteria.criteria.size() == 1
    }

    void "test a lazy criteria used with updateAll and deleteAll keeps only what its closure added"() {
        setup:
        new Organisation(name: 'A').save(flush: true)
        new Organisation(name: 'B').save(flush: true)

        when:
        DetachedCriteria<Organisation> updateCriteria = Organisation.whereLazy { eq('name', 'A') }
        Number updated = updateCriteria.updateAll(name: 'A_UPDATED')
        DetachedCriteria<Organisation> deleteCriteria = Organisation.whereLazy { eq('name', 'A_UPDATED') }
        Number deleted = deleteCriteria.deleteAll()

        then:
        updated == 1
        deleted == 1
        updateCriteria.criteria.size() == 1
        deleteCriteria.criteria.size() == 1
    }

    void "test a lazy criteria runs its deferred closure once however often it is executed"() {
        setup:
        new Organisation(name: 'A').save(flush: true)
        new Organisation(name: 'B').save(flush: true)
        Map wanted = [name: 'A']

        when:
        DetachedCriteria<Organisation> criteria = Organisation.whereLazy { eq('name', wanted.name) }
        List<String> first = criteria.list()*.name
        wanted.name = 'B'
        List<String> second = criteria.list()*.name

        then: 'a later change to what the closure reads does not change the criteria'
        first == ['A']
        second == ['A']
    }

    void "test a lazy subquery without a projection can be translated twice and is never changed"() {
        setup:
        new Organisation(name: 'A').save(flush: true)
        new Organisation(name: 'B').save(flush: true)
        DetachedCriteria<Organisation> subquery = Organisation.whereLazy { eq('name', 'A') }

        when:
        List<String> first = Organisation.where { inList('name', subquery) }.list()*.name
        List<String> second = Organisation.where { inList('name', subquery) }.list()*.name

        then:
        first == ['A']
        second == ['A']

        and: 'translating the subquery added no projection and no restriction to the shared criteria'
        subquery.projections.isEmpty()
        subquery.criteria.size() == 1
    }

    void "test a not-equals subquery materializes a lazy criteria"() {
        setup:
        new Organisation(name: 'A').save(flush: true)
        new Organisation(name: 'B').save(flush: true)

        when:
        DetachedCriteria<Organisation> subquery = Organisation.whereLazy { eq('name', 'A') }
        List<Organisation> result = Organisation.where { ne('name', subquery) }.list()

        then: 'the lazy subquery keeps its restriction and gains nothing from translation'
        result*.name == ['B']
        subquery.criteria.size() == 1
        subquery.projections.isEmpty()
    }

    void "a restriction added to a deferred criteria after its first execution reaches the next execution"() {
        setup:
        Organisation orgA = new Organisation(name: 'A')
        orgA.addToUsers(email: 'a1@a')
        orgA.addToUsers(email: 'a2@a')
        orgA.save(flush: true)
        Organisation orgB = new Organisation(name: 'B')
        orgB.addToUsers(email: 'b1@b')
        orgB.save(flush: true)
        DetachedCriteria<User> criteria = new DetachedCriteria<>(User).buildLazy {
            createAlias('organisation', 'o')
        }

        when: 'the criteria runs, and an existing alias is then restricted'
        List<String> first = criteria.list()*.email.sort()
        criteria.eq('o.name', 'A')
        List<String> second = criteria.list()*.email.sort()

        then:
        first == ['a1@a', 'a2@a', 'b1@b']
        second == ['a1@a', 'a2@a']
    }

    void "a criteria derived from a deferred one after its first execution keeps what the closure added"() {
        setup:
        new Organisation(name: 'A').save(flush: true)
        new Organisation(name: 'B').save(flush: true)
        new Organisation(name: 'Z').save(flush: true)
        def wanted = [name: 'A']
        DetachedCriteria<Organisation> criteria = new DetachedCriteria<>(Organisation).buildLazy {
            eq('name', wanted.name)
        }

        when: 'the value the closure reads changes after the first execution, and the criteria is extended'
        List<String> first = criteria.list()*.name
        wanted.name = 'B'
        DetachedCriteria<Organisation> derived = criteria.where { ne('name', 'Z') }

        then: 'the derived criteria has the restriction of the first run and the new one, and the original is unchanged'
        first == ['A']
        derived.list()*.name == ['A']
        criteria.list()*.name == ['A']
    }

    private User createUser(String email) {
        User user = new User(email: email)
        Organisation defaultOrg = Organisation.findOrCreateByName("default")
        defaultOrg.addToUsers(user)
        defaultOrg.save(flush: true)
        user
    }

    private Group createGroup(String name, User supervisor) {
        Group group = new Group()
        group.name = name
        group.supervisor = supervisor
        group.save(flush: true)
    }

    private void assignGroup(User user, Group group) {
        GroupAssignment groupAssignment = new GroupAssignment()
        groupAssignment.user = user
        groupAssignment.group = group
        groupAssignment.save(flush: true)
    }

}


@Entity
class User implements HibernateEntity<User> {
    String email
    static belongsTo = [organisation: Organisation]
    static mapping = {
        table 'T_USER'
    }
}

@Entity
class Group implements HibernateEntity<Group> {
    String name
    User supervisor
    static mapping = {
        table 'T_GROUP'
    }
}

@Entity
class GroupAssignment implements HibernateEntity<GroupAssignment> {
    User user
    Group group
    static mapping = {
        table 'T_GROUP_ASSIGNMENT'
    }
}

@Entity
class Organisation implements HibernateEntity<Organisation> {
    String name
    static hasMany = [users: User]
}
