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
 * An owner with several unidirectional hasMany associations keeps a join table for each of them, whether they point
 * at different classes or at the same class with join tables of their own, and an element added to one of them is
 * written to its own join table. A sibling to-many that is not bound as a collection, such as a serializable one,
 * neither blocks the session factory nor counts as a sharer of the join table.
 */
class MultipleUnidirectionalHasManySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(MuhItem, MuhRole, MuhTag, MuhAccount, MuhNote, MuhSharedAccount)
    }

    void cleanup() {
        MuhItem.withNewTransaction {
            MuhItem.executeUpdate('delete from MuhItem', [:])
            MuhAccount.executeUpdate('delete from MuhAccount', [:])
            MuhSharedAccount.executeUpdate('delete from MuhSharedAccount', [:])
            MuhNote.executeUpdate('delete from MuhNote', [:])
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

        then: "the shared table keeps both rows: the non-updatable key stops one association from deleting or nulling the other's"
        sharedRows() == [[account.id, allow.id, null], [account.id, null, deny.id]]
        MuhSharedAccount.get(account.id).roles*.authority == ['ROLE_ALLOW']

        and: "an element added to an association sharing its join table is not written, which is issue 10811 and not changed here"
        MuhSharedAccount.get(account.id).denyRoles*.authority == ['ROLE_DENY']
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
                    'select account_id, role_id, deny_role_id from muh_shared_account_roles order by role_id nulls last', Object[]
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
class MuhSharedAccount {
    Long id
    String name
    static hasMany = [roles: MuhRole, denyRoles: MuhRole]
    static mapping = {
        roles joinTable: [name: 'muh_shared_account_roles', key: 'account_id', column: 'role_id']
        denyRoles joinTable: [name: 'muh_shared_account_roles', key: 'account_id', column: 'deny_role_id']
    }
}
