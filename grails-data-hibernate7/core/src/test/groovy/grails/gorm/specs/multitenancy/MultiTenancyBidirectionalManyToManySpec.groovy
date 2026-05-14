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
package grails.gorm.specs.multitenancy

import grails.gorm.transactions.Rollback

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

/**
 * Created by puneetbehl on 21/03/2018.
 */
@RestoreSystemProperties
class MultiTenancyBidirectionalManyToManySpec extends Specification {

    @Shared
    final Map config = [
            "grails.gorm.multiTenancy.mode"               : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
            "grails.gorm.multiTenancy.tenantResolverClass": SystemPropertyTenantResolver.name,
            'dataSource.url'                              : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
            'dataSource.dialect'                          : H2Dialect.name,
            'dataSource.formatSql'                        : 'true',
            'hibernate.flush.mode'                        : 'COMMIT',
            'hibernate.cache.queries'                     : 'true',
            'hibernate.hbm2ddl.auto'                      : 'create',
    ]

    @Shared
    DepartmentService departmentService
    @Shared
    UserService userService

    @AutoCleanup
    HibernateDatastore datastore

    void setupSpec() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "oci")
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), getClass().getPackage())
        departmentService = datastore.getService(DepartmentService)
        userService = datastore.getService(UserService)
    }

    @Rollback
    @Issue("https://github.com/grails/gorm-hibernate5/issues/58")
    void "test hasMany and 'in' query with multi-tenancy"() {
        given:
        createSomeUsers()

        when:
        List<User> users = userService.findAllByDepartment("Grails")

        then:
        users.size() == 4
    }

    Number createSomeUsers() {
        Department department = new Department(name: "Grails")
        department.addToUsers(new User(username: "John Doe"))
        department.addToUsers(new User(username: "Hanna William"))
        department.addToUsers(new User(username: "Mark"))
        department.addToUsers(new User(username: "Karl"))

        department.save(flush: true)
        department.users.size()
    }

}