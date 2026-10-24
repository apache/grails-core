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
import spock.lang.PendingFeature

/**
 * Two unidirectional hasMany associations that map to one join table with the same key column and a column of their
 * own for the element: the rows of an association are the rows that have its element column. Hibernate deletes all
 * the rows of a collection with one statement on its key, so clearing one association must not take the rows of the
 * other with it.
 */
class SharedJoinTableBulkDeleteSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(SjtbdAccount, SjtbdRole, SjtbdGroup, SjtbdSolo)
    }

    @PendingFeature(reason = 'is empty ignores the restriction of the association, apache/grails-core#16748')
    void "is empty on an association that shares a join table and a key column ignores the rows of the other"() {
        given: 'an account with a role and no denied role, so its only row has no value in the denied role column'
        SjtbdAccount.withNewTransaction {
            SjtbdRole admin = new SjtbdRole(name: 'admin').save(flush: true)
            SjtbdAccount account = new SjtbdAccount(name: 'account')
            account.addToRoles(admin)
            account.save(flush: true)
        }

        expect: 'the account has no denied roles, and has roles'
        SjtbdAccount.withNewSession {
            SjtbdAccount.withNewTransaction {
                SjtbdAccount.executeQuery('select a.name from SjtbdAccount a where a.denyRoles is empty') == ['account'] &&
                        SjtbdAccount.executeQuery('select a.name from SjtbdAccount a where a.roles is not empty') == ['account'] &&
                        SjtbdAccount.executeQuery('select a.name from SjtbdAccount a where a.denyRoles is not empty').isEmpty()
            }
        }
    }

    void "clearing one association of a shared join table leaves the rows of the other"() {
        given: 'an account with a role in each association'
        Map<String, Long> ids = SjtbdAccount.withNewTransaction {
            SjtbdRole admin = new SjtbdRole(name: 'admin').save(flush: true)
            SjtbdRole guest = new SjtbdRole(name: 'guest').save(flush: true)
            SjtbdAccount account = new SjtbdAccount(name: 'account')
            account.addToRoles(admin)
            account.addToDenyRoles(guest)
            [account: account.save(flush: true).id]
        }

        when: 'the roles are cleared'
        SjtbdAccount.withNewSession {
            SjtbdAccount.withNewTransaction {
                SjtbdAccount account = SjtbdAccount.get(ids.account)
                account.roles.clear()
                account.save(flush: true)
            }
        }

        then: 'the denied roles are still there'
        SjtbdAccount.withNewSession {
            SjtbdAccount.withNewTransaction {
                SjtbdAccount account = SjtbdAccount.get(ids.account)
                account.roles.isEmpty() && account.denyRoles*.name == ['guest']
            }
        }

        when: 'the denied roles are cleared after a role is added back'
        SjtbdAccount.withNewSession {
            SjtbdAccount.withNewTransaction {
                SjtbdAccount account = SjtbdAccount.get(ids.account)
                account.addToRoles(SjtbdRole.findByName('admin'))
                account.denyRoles.clear()
                account.save(flush: true)
            }
        }

        then: 'the roles are still there'
        SjtbdAccount.withNewSession {
            SjtbdAccount.withNewTransaction {
                SjtbdAccount account = SjtbdAccount.get(ids.account)
                account.denyRoles.isEmpty() && account.roles*.name == ['admin']
            }
        }
    }

    @PendingFeature(reason = 'removing one element of a set deletes every row of the association, apache/grails-core#16745')
    void "removing one element of a shared join table's set keeps the other elements, with a key column each"() {
        given: 'a group with two roles in each association'
        Long id = SjtbdGroup.withNewTransaction {
            SjtbdRole one = new SjtbdRole(name: 'one').save(flush: true)
            SjtbdRole two = new SjtbdRole(name: 'two').save(flush: true)
            SjtbdGroup group = new SjtbdGroup(name: 'group')
            [one, two].each { group.addToAllow(it); group.addToDeny(it) }
            group.save(flush: true).id
        }

        when: 'one role is removed from one association'
        SjtbdGroup.withNewSession {
            SjtbdGroup.withNewTransaction {
                SjtbdGroup group = SjtbdGroup.get(id)
                group.removeFromAllow(group.allow.find { it.name == 'one' })
                group.save(flush: true)
            }
        }

        then: 'the other role of that association and both of the other association remain'
        SjtbdGroup.withNewSession {
            SjtbdGroup.withNewTransaction {
                SjtbdGroup group = SjtbdGroup.get(id)
                group.allow*.name == ['two'] && group.deny*.name.sort() == ['one', 'two']
            }
        }
    }

    @PendingFeature(reason = 'removing one element of a set deletes every row of the association, apache/grails-core#16745')
    void "removing one element of a shared join table's set keeps the other elements, with one key column"() {
        given:
        Long id = SjtbdAccount.withNewTransaction {
            SjtbdRole one = new SjtbdRole(name: 'one').save(flush: true)
            SjtbdRole two = new SjtbdRole(name: 'two').save(flush: true)
            SjtbdAccount account = new SjtbdAccount(name: 'account')
            [one, two].each { account.addToRoles(it); account.addToDenyRoles(it) }
            account.save(flush: true).id
        }

        when:
        SjtbdAccount.withNewSession {
            SjtbdAccount.withNewTransaction {
                SjtbdAccount account = SjtbdAccount.get(id)
                account.removeFromRoles(account.roles.find { it.name == 'one' })
                account.save(flush: true)
            }
        }

        then:
        SjtbdAccount.withNewSession {
            SjtbdAccount.withNewTransaction {
                SjtbdAccount account = SjtbdAccount.get(id)
                account.roles*.name == ['two'] && account.denyRoles*.name.sort() == ['one', 'two']
            }
        }
    }

    @PendingFeature(reason = 'removing one element of a set deletes every row of the association, apache/grails-core#16745')
    void "removing one element of a set on a join table of its own keeps the other elements"() {
        given:
        Long id = SjtbdSolo.withNewTransaction {
            SjtbdRole one = new SjtbdRole(name: 'one').save(flush: true)
            SjtbdRole two = new SjtbdRole(name: 'two').save(flush: true)
            SjtbdSolo solo = new SjtbdSolo(name: 'solo')
            [one, two].each { solo.addToRoles(it) }
            solo.save(flush: true).id
        }

        when:
        SjtbdSolo.withNewSession {
            SjtbdSolo.withNewTransaction {
                SjtbdSolo solo = SjtbdSolo.get(id)
                solo.removeFromRoles(solo.roles.find { it.name == 'one' })
                solo.save(flush: true)
            }
        }

        then:
        SjtbdSolo.withNewSession {
            SjtbdSolo.withNewTransaction {
                SjtbdSolo.get(id).roles*.name == ['two']
            }
        }
    }
}

@Entity
class SjtbdGroup {
    Long id
    String name
    static hasMany = [allow: SjtbdRole, deny: SjtbdRole]
    static mapping = {
        version false
    }
}

@Entity
class SjtbdSolo {
    Long id
    String name
    static hasMany = [roles: SjtbdRole]
    static mapping = {
        version false
    }
}

@Entity
class SjtbdAccount {
    Long id
    String name
    static hasMany = [roles: SjtbdRole, denyRoles: SjtbdRole]
    static mapping = {
        version false
        roles joinTable: [name: 'sjtbd_account_roles', key: 'account_id', column: 'role_id']
        denyRoles joinTable: [name: 'sjtbd_account_roles', key: 'account_id', column: 'deny_role_id']
    }
}

@Entity
class SjtbdRole {
    Long id
    String name
}
