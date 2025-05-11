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
package grails.gorm.specs.validation

import grails.gorm.transactions.Rollback
import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

@Rollback
class UniqueFalseConstraintSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(User)

    @Issue('https://github.com/grails/grails-data-mapping/issues/1059')
    void 'unique:false constraint is ignored and does not behave as unique:true'() {
        given: 'a user'
        def user1 = new User(name: 'John')
        user1.save(flush: true)

        when: 'trying to save another user with the same name'
        def user2 = new User(name: 'John')
        user2.save(flush: true)

        then: 'both users are saved without errors'
        !user1.hasErrors()
        !user2.hasErrors()
    }
}

@Entity
class User {
    Long id
    String name

    static constraints = {
        name unique: false
    }

    static mapping = {
        table '`user`'
    }
}
