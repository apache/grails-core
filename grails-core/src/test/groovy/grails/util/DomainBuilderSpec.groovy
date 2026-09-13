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
package grails.util

import spock.lang.Specification

class DomainBuilderSpec extends Specification {

    void 'a nested node whose pluralized name matches a parent collection property is added via addToXxx'() {
        given:
        DomainBuilder builder = new DomainBuilder()
        builder.classNameResolver = 'grails.util'

        when:
        DbCompany company = builder.dbCompany(name: 'ACME') {
            dbEmployee(name: 'Duke')
            dbEmployee(name: 'George')
        }

        then:
        company.name == 'ACME'
        company.dbEmployees*.name == ['Duke', 'George']
    }

    void 'a nested node with no matching plural collection property is set directly by singular name'() {
        given:
        DomainBuilder builder = new DomainBuilder()
        builder.classNameResolver = 'grails.util'

        when:
        DbEmployee employee = builder.dbEmployee(name: 'Duke') {
            dbAddress(street: '123 Groovy Rd')
        }

        then:
        employee.name == 'Duke'
        employee.dbAddress.street == '123 Groovy Rd'
    }

    void 'the shared child property setter is used directly for a single-reference property'() {
        given:
        DomainBuilder.DefaultGrailsChildPropertySetter setter = new DomainBuilder.DefaultGrailsChildPropertySetter()
        DbEmployee parent = new DbEmployee(name: 'Duke')
        DbAddress address = new DbAddress(street: '123 Groovy Rd')

        when:
        setter.setChild(parent, address, 'dbEmployee', 'dbAddress')

        then:
        parent.dbAddress.is(address)
    }
}

class DbCompany {
    String name
    List<DbEmployee> dbEmployees = []

    void addToDbEmployees(DbEmployee employee) {
        dbEmployees << employee
    }
}

class DbEmployee {
    String name
    DbAddress dbAddress
}

class DbAddress {
    String street
}
