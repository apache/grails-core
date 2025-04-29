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
import org.apache.grails.data.testing.tck.domains.GroupWithin
import org.apache.grails.data.testing.tck.domains.UniqueGroup
import org.grails.datastore.gorm.GormEntity
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Tests the unique constraint
 */
/**
 *
 *  NOTE: This test is disabled because in order for the test suite to run quickly we need to run each test in a transaction.
 *  This makes it not possible to test the scenario outlined here, however tests for this use case exist in the hibernate plugin itself
 *  so we are covered.
 *
 */
class UniqueConstraintHibernateSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(UniqueGroup, GroupWithin, Driver, License)
    @Shared PlatformTransactionManager transactionManager = hibernateDatastore.getTransactionManager()

    void "Test simple unique constraint"() {
        when:"Two domain classes with the same name are saved"
        UniqueGroup one = UniqueGroup.withTransaction {
            new UniqueGroup(name:"foo").save(flush:true)
        }


        UniqueGroup two = UniqueGroup.withTransaction {
            def ug = new UniqueGroup(name: "foo")
            ug.save(flush:true)
            return ug
        }


        then:"The second has errors"
        two.hasErrors()
        UniqueGroup.withTransaction { UniqueGroup.count() } == 1

        when:"The first is saved again"
        one = UniqueGroup.withTransaction {
            def ug = UniqueGroup.findByName("foo")
            ug.save(flush:true)
            return ug
        }

        then:"The are no errors"
        one != null

        when:"Three domain classes are saved within different uniqueness groups"
        GroupWithin group1
        GroupWithin group2
        GroupWithin group3
        GroupWithin.withTransaction {
            group1 = new GroupWithin(name:"foo", org:"mycompany").save(flush:true)
            group2 = new GroupWithin(name:"foo", org:"othercompany").save(flush:true)
            group3 = new GroupWithin(name:"foo", org:"mycompany")
            group3.save(flush:true)

        }

        then:"Only the third has errors"
        one != null
        two != null
        group3.hasErrors()
        GroupWithin.withTransaction {  GroupWithin.count() } == 2

    }

    @spock.lang.Ignore
    def "Test unique constraint with a hasOne association"() {
        when:"Two domain classes with the same license are saved"
        Driver one
        Driver two
        License license
        Driver.withTransaction {
            license = new License()
            def driver = new Driver(license: license)
            driver.license = license
            one = driver.save(flush: true)
            two = new Driver(license: license)
            two.license = license
            two.save(flush: true)
        }

        then:"The second has errors"
        one != null
        two.hasErrors()
        Driver.withTransaction { Driver.count() } == 1
        Driver.withTransaction { License.count() } == 1

        when:"The first is saved again"
        one = Driver.withTransaction {
            Driver d = Driver.findByLicense(license)
            d.save(flush:true)
            return d
        }

        then:"The are no errors"
        one != null
    }

}

@Entity
class Driver implements Serializable {
    Long id
    Long version
    static hasOne = [license: License]
    License license
    static constraints = {
        license unique: true
    }
}

@Entity
class License implements GormEntity<License> {
    Long id
    Long version
    Driver driver
}
