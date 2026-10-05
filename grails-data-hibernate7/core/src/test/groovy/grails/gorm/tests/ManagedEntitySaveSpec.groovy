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
import grails.gorm.hibernate.annotation.ManagedEntity

/**
 * Saving an entity enhanced by {@link ManagedEntity} must satisfy the assertions Hibernate makes
 * when the entity is added to a persistence context. Gradle runs tests with JVM assertions
 * enabled, so these features also verify that contract.
 */
class ManagedEntitySaveSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(ManagedRole)
    }

    void 'a new managed entity can be saved'() {
        when:
        def role = new ManagedRole(authority: 'ROLE_USER').save(flush: true, failOnError: true)
        manager.session.clear()

        then:
        role.id
        ManagedRole.get(role.id).authority == 'ROLE_USER'
    }

    void 'several new managed entities can be saved in the same session'() {
        when:
        def roles = ['ROLE_USER', 'ROLE_ADMIN', 'ROLE_GUEST'].collect { authority ->
            new ManagedRole(authority: authority).save(failOnError: true)
        }
        manager.session.flush()
        manager.session.clear()

        then:
        roles*.id.every()
        ManagedRole.list(sort: 'authority')*.authority == ['ROLE_ADMIN', 'ROLE_GUEST', 'ROLE_USER']
    }
}

@Entity
@ManagedEntity
class ManagedRole {

    String authority
}
