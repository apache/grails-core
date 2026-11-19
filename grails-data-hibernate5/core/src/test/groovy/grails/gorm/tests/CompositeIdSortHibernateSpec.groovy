/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package grails.gorm.tests

import grails.gorm.annotation.Entity
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue

/**
 * An entity with a composite id has no single identity property, so listing it sorted by one of the properties of
 * its id, and querying through them, must not fail on the missing identity.
 */
@Issue('https://github.com/apache/grails-core/issues/14451')
class CompositeIdSortHibernateSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([CompUser, CompRole, CompUserRole])
    }

    private void seed() {
        CompUser ann = new CompUser(name: 'ann').save(failOnError: true)
        CompUser bob = new CompUser(name: 'bob').save(failOnError: true)
        CompRole admin = new CompRole(name: 'admin').save(failOnError: true)
        CompRole user = new CompRole(name: 'user').save(failOnError: true)
        new CompUserRole(user: ann, role: user).save(failOnError: true)
        new CompUserRole(user: bob, role: admin).save(failOnError: true)
        new CompUserRole(user: ann, role: admin).save(flush: true, failOnError: true)
        manager.session.clear()
    }

    void "a composite id entity listed sorted by one of the properties of its id"() {
        given:
        seed()

        when:
        List<String> byRole = CompUserRole.list(sort: 'role')*.role*.name
        List<String> byUserDesc = CompUserRole.list(sort: 'user', order: 'desc')*.user*.name
        List<String> byMap = CompUserRole.list(sort: [user: 'asc', role: 'desc']).collect { it.user.name + ':' + it.role.name }

        then: 'sorting by an association sorts by its id'
        byRole == ['admin', 'admin', 'user']
        byUserDesc == ['bob', 'ann', 'ann']
        byMap == ['ann:user', 'ann:admin', 'bob:admin']
    }

    void "a composite id entity queried through a block on one of the properties of its id"() {
        given:
        seed()

        when:
        List<String> byCriteria = CompUserRole.createCriteria().list {
            user {
                eq('name', 'ann')
            }
        }*.role*.name.sort()
        List<String> byWithCriteria = CompUserRole.withCriteria {
            role {
                eq('name', 'admin')
            }
        }*.user*.name.sort()

        then:
        byCriteria == ['admin', 'user']
        byWithCriteria == ['ann', 'bob']
    }
}

@Entity
class CompUser {
    String name
}

@Entity
class CompRole {
    String name
}

@Entity
class CompUserRole implements Serializable {
    CompUser user
    CompRole role

    static mapping = {
        id composite: ['user', 'role']
    }
}
