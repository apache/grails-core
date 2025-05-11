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
package grails.gorm.specs.uuid

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 05/04/2017.
 */
class UuidInsertSpec extends Specification {
    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(getClass().getPackage())


    @Rollback
    @Issue('https://github.com/grails/grails-data-mapping/issues/902')
    void "Test UUID insert"() {
        when:"A UUID is used"
        Person p = new Person(name: "test").save(flush:true)

        then:"An update should not be triggered"
        p.id
        p.name == 'test'
    }
}

@Entity
class Person {
    UUID id
    String name

    def beforeUpdate() {
        name = "changed"
    }
    static mapping = {
        id generator : 'uuid2', type: 'uuid-binary'
    }
}
