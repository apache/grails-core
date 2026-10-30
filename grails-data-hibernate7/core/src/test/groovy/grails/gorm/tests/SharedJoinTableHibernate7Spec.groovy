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
 * A join table two unidirectional hasMany associations share is unique per association on every database: the
 * same member twice in one association of one owner is rejected, while the same member in the other association,
 * in the same association of another owner, and denied by two owners is allowed. The last case is the one a unique
 * key per association over a nullable key column breaks on Oracle, and the first the one a single key over every key
 * column breaks where nulls are distinct.
 */
@Testcontainers
@Requires({ isDockerAvailable() })
class SharedJoinTableHibernate7Spec extends HibernateGormDatastoreSpec {

    @Shared postgres = DatabaseContainers.POSTGRES
    @Shared mysql = DatabaseContainers.MYSQL
    @Shared mariadb = DatabaseContainers.MARIADB
    @Shared oracle = DatabaseContainers.ORACLE
    @Shared db2 = DatabaseContainers.DB2

    void setupSpec() {
        manager.registerDomainClasses(SjtGroup, SjtMember, SjtListOwner)
    }

    @IgnoreIf({ data.db == 'DB2' && System.getProperty('os.arch') in ['aarch64', 'arm64'] })
    void "on #db a shared join table is unique per association"() {
        given: 'the datastore on this database, with the schema created for it'
        if (container != null && !container.isRunning()) {
            container.start()
        }
        manager.destroy()
        manager.grailsConfig = [
                'dataSource.url'            : container?.jdbcUrl ?: 'jdbc:h2:mem:sharedJoinTableDB;LOCK_TIMEOUT=10000',
                'dataSource.driverClassName': container?.driverClassName ?: 'org.h2.Driver',
                'dataSource.username'       : container?.username ?: 'sa',
                'dataSource.password'       : container?.password ?: '',
                'dataSource.dbCreate'       : 'create-drop'
        ]
        manager.setup(this.class)
        Map<String, Long> ids = SjtGroup.withNewTransaction {
            [member: new SjtMember(name: 'shared member').save(flush: true).id,
             first : new SjtGroup(name: 'first').save(flush: true).id,
             second: new SjtGroup(name: 'second').save(flush: true).id]
        }

        when: 'one member sits in both associations of one group and is denied by a second group'
        SjtGroup.withNewSession {
            SjtGroup.withNewTransaction {
                SjtGroup first = SjtGroup.get(ids.first)
                SjtMember member = SjtMember.get(ids.member)
                first.addToRoles(member)
                first.addToDenyRoles(member)
                first.save(flush: true)
                SjtGroup second = SjtGroup.get(ids.second)
                second.addToDenyRoles(member)
                second.save(flush: true)
            }
        }

        then: 'every row is accepted and read back through its own association'
        SjtGroup.withNewSession {
            SjtGroup.withNewTransaction {
                SjtGroup.get(ids.first).roles*.name == ['shared member'] &&
                        SjtGroup.get(ids.first).denyRoles*.name == ['shared member'] &&
                        SjtGroup.get(ids.second).denyRoles*.name == ['shared member'] &&
                        SjtGroup.get(ids.second).roles.isEmpty()
            }
        }

        when: 'two sessions each add the same new member to the same association of the same group'
        Long otherId = SjtGroup.withNewTransaction { new SjtMember(name: 'other member').save(flush: true).id }
        SjtGroup.withNewSession {
            SjtGroup.withNewTransaction {
                SjtGroup outer = SjtGroup.get(ids.first)
                outer.addToRoles(SjtMember.get(otherId))
                SjtGroup.withNewSession {
                    SjtGroup.withNewTransaction {
                        SjtGroup inner = SjtGroup.get(ids.first)
                        inner.addToRoles(SjtMember.get(otherId))
                        inner.save(flush: true)
                    }
                }
                outer.save(flush: true)
            }
        }

        then: 'the second row is rejected by the constraint of that association'
        thrown(DataIntegrityViolationException)

        when: 'two sessions each add the same new member to the second association of the same group'
        Long denyId = SjtGroup.withNewTransaction { new SjtMember(name: 'denied member').save(flush: true).id }
        SjtGroup.withNewSession {
            SjtGroup.withNewTransaction {
                SjtGroup outer = SjtGroup.get(ids.first)
                outer.addToDenyRoles(SjtMember.get(denyId))
                SjtGroup.withNewSession {
                    SjtGroup.withNewTransaction {
                        SjtGroup inner = SjtGroup.get(ids.first)
                        inner.addToDenyRoles(SjtMember.get(denyId))
                        inner.save(flush: true)
                    }
                }
                outer.save(flush: true)
            }
        }

        then: 'the second row is rejected by the constraint of that association too'
        thrown(DataIntegrityViolationException)

        when: 'a list, a map and a bag each repeat an element'
        Long ownerId = SjtListOwner.withNewTransaction {
            SjtMember member = new SjtMember(name: 'repeated member').save(flush: true)
            new SjtListOwner(nicknames: ['x', 'x'], roleList: [member, member], attrs: [a: 'v', b: 'v'],
                    roleMap: [a: member, b: member], labels: ['y', 'y'], bagRoles: [member, member]).save(flush: true).id
        }

        then: 'all of them are written and read back as they were'
        SjtListOwner.withNewSession {
            SjtListOwner.withNewTransaction {
                SjtListOwner owner = SjtListOwner.get(ownerId)
                owner.nicknames == ['x', 'x'] && owner.roleList*.name == ['repeated member', 'repeated member'] &&
                        owner.attrs == [a: 'v', b: 'v'] && owner.roleMap.values()*.name == ['repeated member', 'repeated member'] &&
                        owner.labels.sort() == ['y', 'y'] && owner.bagRoles*.name == ['repeated member', 'repeated member']
            }
        }

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
class SjtGroup {
    Long id
    String name
    static hasMany = [roles: SjtMember, denyRoles: SjtMember]
    static mapping = {
        version false
    }
}

@Entity
class SjtMember {
    Long id
    String name
}

@Entity
class SjtListOwner {
    Long id
    List<String> nicknames
    List<SjtMember> roleList
    Map<String, String> attrs
    Map<String, SjtMember> roleMap
    Collection<String> labels
    Collection<SjtMember> bagRoles
    static hasMany = [nicknames: String, roleList: SjtMember, attrs: String, roleMap: SjtMember, labels: String, bagRoles: SjtMember]
    static mapping = {
        version false
        roleList joinTable: 'sjt_list_owner_role_list'
        roleMap joinTable: 'sjt_list_owner_role_map'
        bagRoles joinTable: 'sjt_list_owner_bag_roles'
    }
}
