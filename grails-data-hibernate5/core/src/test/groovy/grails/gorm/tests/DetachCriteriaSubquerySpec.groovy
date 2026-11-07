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

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

@SuppressWarnings("GrMethodMayBeStatic")
class DetachCriteriaSubquerySpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {
    void setupSpec() {
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
            def u = User
            exists(
                    GroupAssignment.where {
                        def ga0 = GroupAssignment
                        user.id == u.id && group.supervisor.email == supervisorEmail
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
        DetachedCriteria<User> criteria = User.where {
            inList('organisation', Organisation.where { name == 'A' || name == 'B' }.id())
        }
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
            def u = User
            exists(
                    GroupAssignment.where {
                        user.id == u.id && group.supervisor.email == supervisorEmail
                    }.id()
            )
        }
        List<User> result = criteria.list()

        then:
        noExceptionThrown()
        result.size() == 1
    }

    void "a deferred criteria used as an inList subquery keeps its restriction"() {
        setup:
        new Organisation(name: 'A').save(flush: true)
        new Organisation(name: 'B').save(flush: true)

        when:
        def subquery = new DetachedCriteria<>(Organisation).buildLazy { eq('name', 'A') }.property('name')

        then:
        Organisation.where { inList('name', subquery) }.list()*.name == ['A']
        Organisation.where { 'in'('name', subquery) }.list()*.name == ['A']
    }

    void "a deferred criteria used as a notIn subquery keeps its restriction"() {
        setup:
        new Organisation(name: 'A').save(flush: true)
        new Organisation(name: 'B').save(flush: true)

        when:
        def subquery = new DetachedCriteria<>(Organisation).buildLazy { eq('name', 'A') }.property('name')

        then:
        Organisation.where { notIn('name', subquery) }.list()*.name == ['B']
    }

    void "a deferred criteria used as an exists subquery that matches nothing selects no row"() {
        setup:
        new Organisation(name: 'A').save(flush: true)

        when:
        def subquery = new DetachedCriteria<>(Organisation).buildLazy { eq('name', 'Z') }.id()

        then:
        Organisation.where { exists(subquery) }.list().isEmpty()
    }

    void "the same where query with a deferred subquery selects the same rows for list and deleteAll"() {
        setup:
        new Organisation(name: 'A').save(flush: true)
        new Organisation(name: 'B').save(flush: true)
        def subquery = new DetachedCriteria<>(Organisation).buildLazy { eq('name', 'A') }.property('name')

        when:
        List<String> listed = Organisation.where { inList('name', subquery) }.list()*.name
        Number deleted = Organisation.where { inList('name', subquery) }.deleteAll()

        then:
        listed == ['A']
        deleted == 1
        Organisation.list()*.name == ['B']
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
