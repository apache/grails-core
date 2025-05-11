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
package grails.gorm.specs

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 04/11/16.
 */
@ApplyDetachedCriteriaTransform
class TablePerSubClassAndEmbeddedSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(Company, Vendor)
    @Shared PlatformTransactionManager transactionManager = hibernateDatastore.getTransactionManager()

    @Rollback
    void 'test table per subclass with embedded entity'() {
        given:"some test data"
        Vendor vendor = new Vendor(name: "Blah")
        vendor.address = new Address(address: "somewhere", city: "Youngstown", state: "OH", zip: "44555")
        vendor.save(failOnError:true, flush:true)

        when:"a query executed"
        def results = Vendor.where {
//            like 'address.zip', '%44%' ?
            address.zip =~ '%44%'
        }.list(max: 10, offset: 0)

        then:"the results are correct"
        results.size() == 1
    }

    void "test transform query with embedded entity"() {
        when:"A query is parsed that queries the embedded entity"
        def gcl = new GroovyClassLoader()
        DetachedCriteria criteria = gcl.parseClass('''
import grails.gorm.tests.*

Vendor.where {
    address.zip =~ '%44%'
    name == 'blah'
}
''').newInstance().run()

        then:"The criteria contains the correct criterion"
        criteria.criteria[0] instanceof DetachedAssociationCriteria
        criteria.criteria[0].association.name == 'address'
        criteria.criteria[0].criteria[0].property == 'zip'
    }
}


@Entity
class Company {
    Address address
    String name

    static embedded = ['address']
    static constraints = {
        address nullable: true
    }
    static mapping = {
        tablePerSubclass  true
    }
}
@Entity
class Vendor extends Company {

    static constraints = {
    }
}
class Address {
    String address
    String city
    String state
    String zip
}
