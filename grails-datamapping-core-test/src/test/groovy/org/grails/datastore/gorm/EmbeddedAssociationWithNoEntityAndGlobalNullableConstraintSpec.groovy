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
package org.grails.datastore.gorm

import grails.gorm.transactions.Transactional
import grails.persistence.Entity
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

class EmbeddedAssociationWithNoEntityAndGlobalNullableConstraintSpec extends Specification {

    @Shared @AutoCleanup SimpleMapDatastore datastore = new SimpleMapDatastore(
        DatastoreUtils.createPropertyResolver((Settings.SETTING_DEFAULT_CONSTRAINTS): { '*'(nullable: true) }),
        [],
        User2
    )

    @Transactional
    @Issue('https://github.com/grails/grails-core/issues/10867')
    void "global constraints are applied to embedded properties defined as POGO"() {
        given: 'a user with only a few properties set'
        def user = new User2()
        user.username = 'admin'
        user.address.city = 'Madrid'

        def context = datastore.mappingContext
        context.setValidatorRegistry(new DefaultValidatorRegistry(context, datastore.getConnectionSources().getDefaultConnectionSource().settings))

        when: 'validating the user'
        user.validate()

        then: 'it has no errors because global constraints are applied'
        !user.hasErrors()
    }
}

@Entity
class User2 {
    Long id
    String username
    String password
    Address2 address = new Address2()

    static embedded = ['address']

    static mapping = {
    }
}

class Address2 {
    Long id
    String city
    String postCode
}
