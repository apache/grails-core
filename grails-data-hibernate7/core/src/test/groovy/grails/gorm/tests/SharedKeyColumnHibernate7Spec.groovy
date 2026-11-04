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
import org.springframework.dao.DataIntegrityViolationException
import org.testcontainers.spock.Testcontainers
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Shared

/**
 * Two unidirectional hasMany associations that map to one join table with the same key column and a column of their
 * own for the element: an owner can hold several rows in each association, which the database must accept on every
 * database. The rows of the second association leave the element column of the first null, and a database that
 * compares the non-null columns of a partly null key, as Oracle does, must not see them as duplicates of each other.
 */
@Testcontainers
@Requires({ isDockerAvailable() })
class SharedKeyColumnHibernate7Spec extends HibernateGormDatastoreSpec {

    @Shared postgres = DatabaseContainers.POSTGRES
    @Shared mysql = DatabaseContainers.MYSQL
    @Shared mariadb = DatabaseContainers.MARIADB
    @Shared oracle = DatabaseContainers.ORACLE
    @Shared db2 = DatabaseContainers.DB2

    void setupSpec() {
        manager.registerDomainClasses(SjtkAccount, SjtkRole)
    }

    private static void addInTwoSessions(Long accountId, Long roleId, String association) {
        String add = association == 'roles' ? 'addToRoles' : 'addToDenyRoles'
        SjtkAccount.withNewSession {
            SjtkAccount.withNewTransaction {
                SjtkAccount outer = SjtkAccount.get(accountId)
                outer."$add"(SjtkRole.get(roleId))
                SjtkAccount.withNewSession {
                    SjtkAccount.withNewTransaction {
                        SjtkAccount inner = SjtkAccount.get(accountId)
                        inner."$add"(SjtkRole.get(roleId))
                        inner.save(flush: true)
                    }
                }
                outer.save(flush: true)
            }
        }
    }

    @IgnoreIf({ data.db == 'DB2' && System.getProperty('os.arch') in ['aarch64', 'arm64'] })
    void "on #db several rows in each association of a join table with a shared key column are accepted"() {
        given: 'the datastore on this database, with the schema created for it'
        if (container != null && !container.isRunning()) {
            container.start()
        }
        manager.destroy()
        manager.grailsConfig = [
                'dataSource.url'            : container?.jdbcUrl ?: 'jdbc:h2:mem:sharedKeyColumnDB;LOCK_TIMEOUT=10000',
                'dataSource.driverClassName': container?.driverClassName ?: 'org.h2.Driver',
                'dataSource.username'       : container?.username ?: 'sa',
                'dataSource.password'       : container?.password ?: '',
                'dataSource.dbCreate'       : 'create-drop'
        ]
        manager.setup(this.class)
        Map<String, Long> ids = SjtkAccount.withNewTransaction {
            [first : new SjtkRole(name: 'first').save(flush: true).id,
             second: new SjtkRole(name: 'second').save(flush: true).id,
             owner : new SjtkAccount(name: 'owner').save(flush: true).id]
        }

        when: 'the account holds two roles and denies two roles, one of them also allowed'
        SjtkAccount.withNewSession {
            SjtkAccount.withNewTransaction {
                SjtkAccount account = SjtkAccount.get(ids.owner)
                account.addToRoles(SjtkRole.get(ids.first))
                account.addToRoles(SjtkRole.get(ids.second))
                account.addToDenyRoles(SjtkRole.get(ids.first))
                account.addToDenyRoles(SjtkRole.get(ids.second))
                account.save(flush: true)
            }
        }

        then: 'every row is accepted and read back through its own association'
        SjtkAccount.withNewSession {
            SjtkAccount.withNewTransaction {
                SjtkAccount account = SjtkAccount.get(ids.owner)
                account.roles*.name.sort() == ['first', 'second'] && account.denyRoles*.name.sort() == ['first', 'second']
            }
        }

        when: 'two sessions each add the same new role to the roles of the account'
        Long extraId = SjtkAccount.withNewTransaction { new SjtkRole(name: 'extra').save(flush: true).id }
        addInTwoSessions(ids.owner, extraId, 'roles')

        then: 'the second row is rejected by the constraint of the roles'
        thrown(DataIntegrityViolationException)

        when: 'two sessions each add the same new role to the denied roles of the account'
        addInTwoSessions(ids.owner, extraId, 'denyRoles')

        then: 'the second row is rejected by the constraint of the denied roles'
        thrown(DataIntegrityViolationException)

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
class SjtkAccount {
    Long id
    String name
    static hasMany = [roles: SjtkRole, denyRoles: SjtkRole]
    static mapping = {
        version false
        roles joinTable: [name: 'sjtk_account_roles', key: 'account_id', column: 'role_id']
        denyRoles joinTable: [name: 'sjtk_account_roles', key: 'account_id', column: 'deny_role_id']
    }
}

@Entity
class SjtkRole {
    Long id
    String name
}
