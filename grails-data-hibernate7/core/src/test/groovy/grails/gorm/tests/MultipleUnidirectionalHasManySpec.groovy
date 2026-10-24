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

/**
 * An owner with several unidirectional hasMany associations writes an element added to any of them to the join table
 * of that association: a table of its own when the associations point at different classes or map join tables of their
 * own, or the one table two associations to the same class share, with a key column each. A sibling to-many that is not
 * bound as a collection, such as a serializable one, does not block the session factory.
 */
class MultipleUnidirectionalHasManySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(MuhItem, MuhRole, MuhTag, MuhAccount, MuhNote, MuhGroup, MuhSharedAccount, MuhRepeats)
    }

    void cleanup() {
        MuhItem.withNewTransaction {
            MuhItem.executeUpdate('delete from MuhItem', [:])
            MuhAccount.executeUpdate('delete from MuhAccount', [:])
            MuhSharedAccount.executeUpdate('delete from MuhSharedAccount', [:])
            MuhRepeats.executeUpdate('delete from MuhRepeats', [:])
            MuhNote.executeUpdate('delete from MuhNote', [:])
            MuhGroup.executeUpdate('delete from MuhGroup', [:])
            MuhRole.executeUpdate('delete from MuhRole', [:])
            MuhTag.executeUpdate('delete from MuhTag', [:])
        }
    }

    void "an element added to one of two unidirectional hasMany associations is written to its join table"() {
        given: "a persisted item with two unidirectional hasMany associations to different classes"
        def item = new MuhItem(name: 'Example item').save(flush: true)

        when: "a role is added to the managed item and the item is flushed"
        item.addToRoles(new MuhRole(authority: 'ROLE_EXAMPLE'))
        item.save(flush: true)
        MuhItem.withSession { it.clear() }

        then: "the role is found through the reloaded item"
        MuhItem.get(item.id).roles*.authority == ['ROLE_EXAMPLE']
        MuhItem.get(item.id).tags.isEmpty()
    }

    void "elements added to both unidirectional hasMany associations are written to their own join tables"() {
        given:
        def item = new MuhItem(name: 'Example item').save(flush: true)

        when:
        item.addToRoles(new MuhRole(authority: 'ROLE_A'))
        item.addToTags(new MuhTag(name: 'tag-a'))
        item.save(flush: true)
        MuhItem.withSession { it.clear() }

        then:
        MuhItem.get(item.id).roles*.authority == ['ROLE_A']
        MuhItem.get(item.id).tags*.name == ['tag-a']
    }

    void "two unidirectional hasMany associations to the same class with their own join tables are each written"() {
        given: "a persisted account whose allow and deny roles map their own join tables"
        def account = new MuhAccount(name: 'Example account').save(flush: true)

        when: "a role is added to each association and the account is flushed"
        account.addToRoles(new MuhRole(authority: 'ROLE_ALLOW'))
        account.addToDenyRoles(new MuhRole(authority: 'ROLE_DENY'))
        account.save(flush: true)
        MuhAccount.withSession { it.clear() }

        then: "each role is found through its own association of the reloaded account"
        MuhAccount.get(account.id).roles*.authority == ['ROLE_ALLOW']
        MuhAccount.get(account.id).denyRoles*.authority == ['ROLE_DENY']
    }

    void "two unidirectional hasMany associations to the same class sharing a join table are each written"() {
        given: "a persisted group whose allow and deny roles share one join table, with a key column each"
        def group = new MuhGroup(name: 'Example group').save(flush: true)

        when: "a role is added to each association and the group is flushed"
        group.addToRoles(new MuhRole(authority: 'ROLE_ALLOW'))
        group.addToDenyRoles(new MuhRole(authority: 'ROLE_DENY'))
        group.save(flush: true)
        MuhGroup.withSession { it.clear() }

        then: "each role is found through its own association of the reloaded group"
        MuhGroup.get(group.id).roles*.authority == ['ROLE_ALLOW']
        MuhGroup.get(group.id).denyRoles*.authority == ['ROLE_DENY']
    }

    void "one role denied by two groups is written to the shared join table once per group"() {
        given: "a role and two persisted groups"
        def role = new MuhRole(authority: 'ROLE_SHARED').save(flush: true)
        def first = new MuhGroup(name: 'First group').save(flush: true)
        def second = new MuhGroup(name: 'Second group').save(flush: true)

        when: "both groups deny the role, which leaves the first association's key column null in both rows"
        first.addToDenyRoles(role)
        second.addToDenyRoles(role)
        first.save(flush: true)
        second.save(flush: true)
        MuhGroup.withSession { it.clear() }

        then: "each group finds the role, and the rows are unique by the key column of their own association"
        MuhGroup.get(first.id).denyRoles*.authority == ['ROLE_SHARED']
        MuhGroup.get(second.id).denyRoles*.authority == ['ROLE_SHARED']
        MuhGroup.get(first.id).roles.isEmpty()
    }

    void "two unidirectional hasMany associations sharing one join table keep their rows across a flush of the owner"() {
        given: "a persisted account whose allow and deny roles share one join table, with a row for each in that table"
        def account = new MuhSharedAccount(name: 'Shared account').save(flush: true)
        def allow = new MuhRole(authority: 'ROLE_ALLOW').save(flush: true)
        def deny = new MuhRole(authority: 'ROLE_DENY').save(flush: true)
        MuhSharedAccount.withSession { session ->
            session.createNativeMutationQuery(
                    "insert into muh_shared_account_roles (account_id, role_id, deny_role_id) values (${account.id}, ${allow.id}, null)".toString()
            ).executeUpdate()
            session.createNativeMutationQuery(
                    "insert into muh_shared_account_roles (account_id, role_id, deny_role_id) values (${account.id}, null, ${deny.id})".toString()
            ).executeUpdate()
            session.clear()
        }

        expect: "each association of the reloaded account reads the row with its own key column"
        MuhSharedAccount.get(account.id).roles*.authority == ['ROLE_ALLOW']
        MuhSharedAccount.get(account.id).denyRoles*.authority == ['ROLE_DENY']

        when: "the account changes, gains a deny role and is flushed, then the session is cleared"
        MuhSharedAccount account1 = MuhSharedAccount.get(account.id)
        account1.name = 'Renamed account'
        account1.addToDenyRoles(new MuhRole(authority: 'ROLE_DENY_2'))
        account1.save(flush: true)
        MuhSharedAccount.withSession { it.clear() }

        then: "the shared table keeps both rows, and the added deny role has a row of its own with the deny key column"
        MuhRole deny2 = MuhRole.findByAuthority('ROLE_DENY_2')
        sharedRows() == [[account.id, allow.id, null], [account.id, null, deny.id], [account.id, null, deny2.id]]
        MuhSharedAccount.get(account.id).roles*.authority == ['ROLE_ALLOW']
        MuhSharedAccount.get(account.id).denyRoles*.authority.sort() == ['ROLE_DENY', 'ROLE_DENY_2']
    }

    void "the same member twice in one association of a shared join table is refused, while the other association takes it"() {
        given: "an account whose allow and deny roles share a join table, and a role already allowed"
        def account = new MuhSharedAccount(name: 'Strict account').save(flush: true)
        def role = new MuhRole(authority: 'ROLE_ONCE').save(flush: true)
        String allow = "insert into muh_shared_account_roles (account_id, role_id, deny_role_id) values (${account.id}, ${role.id}, null)"
        String deny = "insert into muh_shared_account_roles (account_id, role_id, deny_role_id) values (${account.id}, null, ${role.id})"
        MuhSharedAccount.withSession { it.createNativeMutationQuery(allow.toString()).executeUpdate() }

        expect: "the same role denied by the same account is accepted: it is another association"
        MuhSharedAccount.withSession { it.createNativeMutationQuery(deny.toString()).executeUpdate() } == 1

        when: "the same role is allowed a second time"
        MuhSharedAccount.withSession { it.createNativeMutationQuery(allow.toString()).executeUpdate() }

        then: "the shared table refuses it"
        Exception e = thrown()
        e instanceof org.springframework.dao.DataIntegrityViolationException
    }

    void "a list, a map and a bag keep an element that repeats"() {
        given: "a role and an owner with a repeated element in each kind of collection"
        def role = new MuhRole(authority: 'ROLE_REPEATED').save(flush: true)
        def owner = new MuhRepeats(name: 'Repeats', nicknames: ['a', 'a'], roleList: [role, role], attrs: [k1: 'v', k2: 'v'],
                roleMap: [a: role, b: role], labels: ['x', 'x'], bagRoles: [role, role]).save(flush: true)
        MuhRepeats.withSession { it.clear() }

        when:
        def found = MuhRepeats.get(owner.id)

        then: "every collection reads back with its repeated element"
        found.nicknames == ['a', 'a']
        found.roleList*.authority == ['ROLE_REPEATED', 'ROLE_REPEATED']
        found.attrs == [k1: 'v', k2: 'v']
        found.roleMap.values()*.authority == ['ROLE_REPEATED', 'ROLE_REPEATED']
        found.labels.sort() == ['x', 'x']
        found.bagRoles*.authority == ['ROLE_REPEATED', 'ROLE_REPEATED']
    }

    void "a unidirectional hasMany next to a serializable to-many is written to its join table"() {
        given: "a persisted note whose tags are a serializable column, not a collection"
        def note = new MuhNote(name: 'Example note', tags: ['tag-a', 'tag-b'] as Set).save(flush: true)

        when: "a role is added to the managed note and the note is flushed"
        note.addToRoles(new MuhRole(authority: 'ROLE_NOTE'))
        note.save(flush: true)
        MuhNote.withSession { it.clear() }

        then: "the role is found through the reloaded note and the serializable tags are intact"
        MuhNote.get(note.id).roles*.authority == ['ROLE_NOTE']
        MuhNote.get(note.id).tags == ['tag-a', 'tag-b'] as Set
    }

    private static List<List> sharedRows() {
        MuhSharedAccount.withSession { session ->
            session.createNativeQuery(
                    'select account_id, role_id, deny_role_id from muh_shared_account_roles order by role_id nulls last, deny_role_id', Object[]
            ).list()*.toList()
        }
    }
}

@Entity
class MuhItem {
    Long id
    String name
    static hasMany = [roles: MuhRole, tags: MuhTag]
}

@Entity
class MuhRole {
    Long id
    String authority
}

@Entity
class MuhTag {
    Long id
    String name
}

@Entity
class MuhAccount {
    Long id
    String name
    static hasMany = [roles: MuhRole, denyRoles: MuhRole]
    static mapping = {
        roles joinTable: 'muh_account_allow_roles'
        denyRoles joinTable: 'muh_account_deny_roles'
    }
}

@Entity
class MuhNote {
    Long id
    String name
    Set<String> tags
    static hasMany = [roles: MuhRole, tags: String]
    static mapping = {
        tags type: 'serializable'
    }
}

@Entity
class MuhGroup {
    Long id
    String name
    static hasMany = [roles: MuhRole, denyRoles: MuhRole]
}

@Entity
class MuhSharedAccount {
    Long id
    String name
    static hasMany = [roles: MuhRole, denyRoles: MuhRole]
    static mapping = {
        roles joinTable: [name: 'muh_shared_account_roles', key: 'account_id', column: 'role_id']
        denyRoles joinTable: [name: 'muh_shared_account_roles', key: 'account_id', column: 'deny_role_id']
    }
}

@Entity
class MuhRepeats {
    Long id
    String name
    List<String> nicknames
    List<MuhRole> roleList
    Map<String, String> attrs
    Map<String, MuhRole> roleMap
    Collection<String> labels
    Collection<MuhRole> bagRoles
    static hasMany = [nicknames: String, roleList: MuhRole, attrs: String, roleMap: MuhRole, labels: String, bagRoles: MuhRole]
    static mapping = {
        roleList joinTable: 'muh_repeats_role_list'
        roleMap joinTable: 'muh_repeats_role_map'
        bagRoles joinTable: 'muh_repeats_bag_roles'
    }
}
