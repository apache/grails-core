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
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Shared

/**
 * Two unidirectional hasMany lists to the same class that share one join table, in both shapes: the default table with
 * a key column and an index column per list and one element column, and a table mapped with one key column and an
 * element column and an index column per list. A row of one list leaves the columns of the other list null
 * (apache/grails-core#16700).
 */
class SharedJoinTableListHibernate7Spec extends HibernateGormDatastoreSpec {

    @Shared postgres = DatabaseContainers.POSTGRES
    @Shared mysql = DatabaseContainers.MYSQL
    @Shared mariadb = DatabaseContainers.MARIADB
    @Shared oracle = DatabaseContainers.ORACLE
    @Shared db2 = DatabaseContainers.DB2

    void setupSpec() {
        manager.registerDomainClasses(SjtlOwner, SjtlKeyOwner, SjtlElementAndKeyOwner, SjtlMapOwner, SjtlRole)
    }

    void cleanup() {
        SjtlOwner.withNewTransaction {
            [SjtlOwner, SjtlKeyOwner, SjtlElementAndKeyOwner, SjtlMapOwner, SjtlRole].each { Class domain ->
                domain.executeQuery("from ${domain.simpleName}".toString())*.delete(flush: true)
            }
        }
    }

    void "two lists sharing the default join table keep their elements in order"() {
        given:
        Long id = SjtlOwner.withNewTransaction {
            SjtlRole a = new SjtlRole(name: 'a').save(flush: true)
            SjtlRole b = new SjtlRole(name: 'b').save(flush: true)
            SjtlRole c = new SjtlRole(name: 'c').save(flush: true)
            SjtlOwner owner = new SjtlOwner(name: 'owner')
            owner.addToRoles(a)
            owner.addToRoles(b)
            owner.addToDenyRoles(c)
            owner.addToDenyRoles(a)
            owner.save(flush: true).id
        }

        expect:
        SjtlOwner.withNewSession {
            SjtlOwner.withNewTransaction {
                SjtlOwner owner = SjtlOwner.get(id)
                owner.roles*.name == ['a', 'b'] && owner.denyRoles*.name == ['c', 'a']
            }
        }
    }

    void "two lists sharing one key column keep their elements in order"() {
        given:
        Long id = SjtlKeyOwner.withNewTransaction {
            SjtlRole a = new SjtlRole(name: 'a').save(flush: true)
            SjtlRole b = new SjtlRole(name: 'b').save(flush: true)
            SjtlKeyOwner owner = new SjtlKeyOwner(name: 'owner')
            owner.addToRoles(a)
            owner.addToRoles(b)
            owner.addToDenyRoles(b)
            owner.save(flush: true).id
        }

        expect:
        SjtlKeyOwner.withNewSession {
            SjtlKeyOwner.withNewTransaction {
                SjtlKeyOwner owner = SjtlKeyOwner.get(id)
                owner.roles*.name == ['a', 'b'] && owner.denyRoles*.name == ['b']
            }
        }
    }

    void "two lists sharing both key and element columns keep their elements in order and do not load each other's rows"() {
        given:
        Long id = SjtlElementAndKeyOwner.withNewTransaction {
            SjtlRole a = new SjtlRole(name: 'a').save(flush: true)
            SjtlRole b = new SjtlRole(name: 'b').save(flush: true)
            SjtlRole c = new SjtlRole(name: 'c').save(flush: true)
            SjtlElementAndKeyOwner owner = new SjtlElementAndKeyOwner(name: 'owner')
            owner.addToRoles(a)
            owner.addToRoles(b)
            owner.addToDenyRoles(c)
            owner.addToDenyRoles(a)
            owner.save(flush: true).id
        }

        expect:
        SjtlElementAndKeyOwner.withNewSession {
            SjtlElementAndKeyOwner.withNewTransaction {
                SjtlElementAndKeyOwner owner = SjtlElementAndKeyOwner.get(id)
                owner.roles*.name == ['a', 'b'] && owner.denyRoles*.name == ['c', 'a']
            }
        }
    }

    void "clearing one list of a table where key and element columns are shared keeps the other list"() {
        given:
        Long id = SjtlElementAndKeyOwner.withNewTransaction {
            SjtlRole a = new SjtlRole(name: 'a').save(flush: true)
            SjtlRole b = new SjtlRole(name: 'b').save(flush: true)
            SjtlElementAndKeyOwner owner = new SjtlElementAndKeyOwner(name: 'owner')
            owner.addToRoles(a)
            owner.addToRoles(b)
            owner.addToDenyRoles(b)
            owner.save(flush: true).id
        }

        when: 'the first list is cleared'
        SjtlElementAndKeyOwner.withNewSession {
            SjtlElementAndKeyOwner.withNewTransaction {
                SjtlElementAndKeyOwner owner = SjtlElementAndKeyOwner.get(id)
                owner.roles.clear()
                owner.save(flush: true)
            }
        }

        then:
        SjtlElementAndKeyOwner.withNewSession {
            SjtlElementAndKeyOwner.withNewTransaction {
                SjtlElementAndKeyOwner owner = SjtlElementAndKeyOwner.get(id)
                owner.roles.isEmpty() && owner.denyRoles*.name == ['b']
            }
        }
    }

    void "removing from one list of a table where key and element columns are shared keeps the other list"() {
        given:
        Map<String, Long> ids = SjtlElementAndKeyOwner.withNewTransaction {
            SjtlRole a = new SjtlRole(name: 'a').save(flush: true)
            SjtlRole b = new SjtlRole(name: 'b').save(flush: true)
            SjtlRole c = new SjtlRole(name: 'c').save(flush: true)
            SjtlElementAndKeyOwner owner = new SjtlElementAndKeyOwner(name: 'owner')
            [a, b, c].each { owner.addToRoles(it) }
            [c, b].each { owner.addToDenyRoles(it) }
            [owner: owner.save(flush: true).id, a: a.id]
        }

        when: 'the first element of the first list is removed'
        SjtlElementAndKeyOwner.withNewSession {
            SjtlElementAndKeyOwner.withNewTransaction {
                SjtlElementAndKeyOwner owner = SjtlElementAndKeyOwner.get(ids.owner)
                owner.removeFromRoles(SjtlRole.get(ids.a))
                owner.save(flush: true)
            }
        }

        then:
        SjtlElementAndKeyOwner.withNewSession {
            SjtlElementAndKeyOwner.withNewTransaction {
                SjtlElementAndKeyOwner owner = SjtlElementAndKeyOwner.get(ids.owner)
                owner.roles*.name == ['b', 'c'] && owner.denyRoles*.name == ['c', 'b']
            }
        }
    }
    void "removing from one list of a shared table keeps the other list and the order of the rest"() {
        given:
        Map<String, Long> ids = SjtlOwner.withNewTransaction {
            SjtlRole a = new SjtlRole(name: 'a').save(flush: true)
            SjtlRole b = new SjtlRole(name: 'b').save(flush: true)
            SjtlRole c = new SjtlRole(name: 'c').save(flush: true)
            SjtlOwner owner = new SjtlOwner(name: 'owner')
            [a, b, c].each { owner.addToRoles(it) }
            [c, b].each { owner.addToDenyRoles(it) }
            [owner: owner.save(flush: true).id, a: a.id]
        }

        when: 'the first element of the first list is removed'
        SjtlOwner.withNewSession {
            SjtlOwner.withNewTransaction {
                SjtlOwner owner = SjtlOwner.get(ids.owner)
                owner.removeFromRoles(SjtlRole.get(ids.a))
                owner.save(flush: true)
            }
        }

        then:
        SjtlOwner.withNewSession {
            SjtlOwner.withNewTransaction {
                SjtlOwner owner = SjtlOwner.get(ids.owner)
                owner.roles*.name == ['b', 'c'] && owner.denyRoles*.name == ['c', 'b']
            }
        }
    }

    void "an element may repeat in a list of a shared table, and clearing one list keeps the other"() {
        given:
        Long id = SjtlKeyOwner.withNewTransaction {
            SjtlRole a = new SjtlRole(name: 'a').save(flush: true)
            SjtlKeyOwner owner = new SjtlKeyOwner(name: 'owner')
            owner.addToRoles(a)
            owner.addToRoles(a)
            owner.addToDenyRoles(a)
            owner.save(flush: true).id
        }

        expect: 'the repeated element is kept'
        SjtlKeyOwner.withNewSession {
            SjtlKeyOwner.withNewTransaction { SjtlKeyOwner.get(id).roles*.name } == ['a', 'a']
        }

        when: 'the first list is cleared'
        SjtlKeyOwner.withNewSession {
            SjtlKeyOwner.withNewTransaction {
                SjtlKeyOwner owner = SjtlKeyOwner.get(id)
                owner.roles.clear()
                owner.save(flush: true)
            }
        }

        then:
        SjtlKeyOwner.withNewSession {
            SjtlKeyOwner.withNewTransaction {
                SjtlKeyOwner owner = SjtlKeyOwner.get(id)
                owner.roles.isEmpty() && owner.denyRoles*.name == ['a']
            }
        }
    }

    void "two maps sharing the default join table keep their entries"() {
        given:
        Long id = SjtlMapOwner.withNewTransaction {
            SjtlRole a = new SjtlRole(name: 'a').save(flush: true)
            SjtlRole b = new SjtlRole(name: 'b').save(flush: true)
            SjtlMapOwner owner = new SjtlMapOwner(name: 'owner', roles: [first: a, second: b], denyRoles: [first: b])
            owner.save(flush: true).id
        }

        expect:
        SjtlMapOwner.withNewSession {
            SjtlMapOwner.withNewTransaction {
                SjtlMapOwner owner = SjtlMapOwner.get(id)
                owner.roles.collectEntries { k, v -> [k, v.name] } == [first: 'a', second: 'b'] &&
                        owner.denyRoles.collectEntries { k, v -> [k, v.name] } == [first: 'b']
            }
        }
    }

    @Requires({ HibernateGormDatastoreSpec.isDockerAvailable() })
    @IgnoreIf({ data.db == 'DB2' && System.getProperty('os.arch') in ['aarch64', 'arm64'] })
    void "on #db two lists sharing a join table are written, read and kept unique by position"() {
        given: 'the datastore on this database'
        if (container != null && !container.isRunning()) {
            container.start()
        }
        manager.destroy()
        manager.grailsConfig = [
                'dataSource.url'            : container?.jdbcUrl ?: 'jdbc:h2:mem:sharedJoinTableListDB;LOCK_TIMEOUT=10000',
                'dataSource.driverClassName': container?.driverClassName ?: 'org.h2.Driver',
                'dataSource.username'       : container?.username ?: 'sa',
                'dataSource.password'       : container?.password ?: '',
                'dataSource.dbCreate'       : 'create-drop'
        ]
        manager.setup(this.class)

        when: 'both shapes are written with a repeated element and one shared element'
        Map<String, Long> ids = SjtlOwner.withNewTransaction {
            SjtlRole a = new SjtlRole(name: 'a').save(flush: true)
            SjtlRole b = new SjtlRole(name: 'b').save(flush: true)
            SjtlOwner owner = new SjtlOwner(name: 'owner')
            [a, b, a].each { owner.addToRoles(it) }
            [b].each { owner.addToDenyRoles(it) }
            SjtlKeyOwner keyOwner = new SjtlKeyOwner(name: 'key owner')
            [b, a].each { keyOwner.addToRoles(it) }
            [a, a].each { keyOwner.addToDenyRoles(it) }
            [owner: owner.save(flush: true).id, keyOwner: keyOwner.save(flush: true).id]
        }

        then:
        SjtlOwner.withNewSession {
            SjtlOwner.withNewTransaction {
                SjtlOwner owner = SjtlOwner.get(ids.owner)
                SjtlKeyOwner keyOwner = SjtlKeyOwner.get(ids.keyOwner)
                owner.roles*.name == ['a', 'b', 'a'] && owner.denyRoles*.name == ['b'] &&
                        keyOwner.roles*.name == ['b', 'a'] && keyOwner.denyRoles*.name == ['a', 'a']
            }
        }

        when: 'a second row is written at a position the first list already holds'
        Long roleId = SjtlRole.withNewTransaction { SjtlRole.findByName('a').id }
        SjtlOwner.withNewTransaction {
            SjtlOwner.withSession { session ->
                session.createNativeMutationQuery(('insert into sjtl_owner_sjtl_role (sjtl_owner_roles_id, roles_idx, ' +
                        "sjtl_role_id) values (${ids.owner}, 0, ${roleId})").toString()).executeUpdate()
            }
        }

        then: 'the position is taken once'
        thrown(Exception)


        where:
        db         | container
        'H2'       | null
        'Postgres' | postgres
        'MySQL'    | mysql
        'MariaDB'  | mariadb
        'Oracle'   | oracle
        'DB2'      | db2
    }

}

@Entity
class SjtlOwner {
    Long id
    String name
    List<SjtlRole> roles
    List<SjtlRole> denyRoles
    static hasMany = [roles: SjtlRole, denyRoles: SjtlRole]
    static mapping = {
        version false
    }
}

@Entity
class SjtlKeyOwner {
    Long id
    String name
    List<SjtlRole> roles
    List<SjtlRole> denyRoles
    static hasMany = [roles: SjtlRole, denyRoles: SjtlRole]
    static mapping = {
        version false
        roles joinTable: [name: 'sjtl_key_owner_roles', key: 'owner_id', column: 'role_id']
        denyRoles joinTable: [name: 'sjtl_key_owner_roles', key: 'owner_id', column: 'deny_role_id']
    }
}

@Entity
class SjtlElementAndKeyOwner {
    Long id
    String name
    List<SjtlRole> roles
    List<SjtlRole> denyRoles
    static hasMany = [roles: SjtlRole, denyRoles: SjtlRole]
    static mapping = {
        version false
        roles joinTable: [name: 'sjtl_element_and_key_roles', key: 'owner_id', column: 'role_id']
        denyRoles joinTable: [name: 'sjtl_element_and_key_roles', key: 'owner_id', column: 'role_id']
    }
}

@Entity
class SjtlMapOwner {
    Long id
    String name
    Map<String, SjtlRole> roles
    Map<String, SjtlRole> denyRoles
    static hasMany = [roles: SjtlRole, denyRoles: SjtlRole]
    static mapping = {
        version false
    }
}

@Entity
class SjtlRole {
    Long id
    String name
    static mapping = {
        version false
    }
}
