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

package scaffoldingfields

import groovy.time.TimeCategory

/**
 * Bootstrap data for testing scaffolding and fields functionality.
 */
class BootStrap {

    def init = {

        use(TimeCategory) {

            // Create test departments
            def engineering = new Department(name: 'Engineering', description: 'Software development team').save(failOnError: true)
            def marketing = new Department(name: 'Marketing', description: 'Marketing and sales team').save(failOnError: true)
            def hr = new Department(name: 'Human Resources', description: 'HR and recruitment').save(failOnError: true)

            // Create test projects
            def projectAlpha = new Project(name: 'Project Alpha', code: 'ALPHA', startDate: new Date(), active: true).save(failOnError: true)
            def projectBeta = new Project(name: 'Project Beta', code: 'BETA', startDate: new Date() - 30.days, active: true).save(failOnError: true)
            def projectGamma = new Project(name: 'Project Gamma', code: 'GAMMA', startDate: new Date() - 60.days, active: false).save(failOnError: true)

            // Create test employees with various field types populated
            def john = new Employee(
                firstName: 'John',
                lastName: 'Doe',
                email: 'john.doe@example.com',
                biography: 'Senior software engineer with 10 years of experience in Java and Groovy development.',
                age: 35,
                salary: new BigDecimal('85000.00'),
                hireDate: new Date() - 365.days,
                active: true,
                status: EmployeeStatus.ACTIVE,
                priority: 'HIGH',
                department: engineering,
                website: new URL('https://johndoe.dev'),
                preferredLocale: Locale.US,
                preferredCurrency: Currency.getInstance('USD'),
                preferredTimezone: TimeZone.getTimeZone('America/New_York'),
                address: new Address(street: '123 Main St', city: 'New York', postalCode: '10001', country: 'USA')
            ).addToProjects(projectAlpha).addToProjects(projectBeta).save(failOnError: true)

            new Employee(
                firstName: 'Jane',
                lastName: 'Smith',
                email: 'jane.smith@example.com',
                biography: 'Marketing specialist focused on digital campaigns.',
                age: 28,
                salary: new BigDecimal('65000.00'),
                hireDate: new Date() - 180.days,
                active: true,
                status: EmployeeStatus.ACTIVE,
                priority: 'MEDIUM',
                department: marketing,
                preferredLocale: Locale.UK,
                preferredCurrency: Currency.getInstance('GBP'),
                preferredTimezone: TimeZone.getTimeZone('Europe/London'),
                address: new Address(street: '456 High Street', city: 'London', postalCode: 'SW1A 1AA', country: 'UK')
            ).addToProjects(projectBeta).save(failOnError: true)

            new Employee(
                firstName: 'Bob',
                lastName: 'Wilson',
                email: 'bob.wilson@example.com',
                age: 45,
                salary: new BigDecimal('95000.00'),
                hireDate: new Date() - 730.days,
                active: false,
                status: EmployeeStatus.ON_LEAVE,
                priority: 'LOW',
                department: hr,
                preferredLocale: Locale.CANADA,
                preferredCurrency: Currency.getInstance('CAD'),
                preferredTimezone: TimeZone.getTimeZone('America/Toronto')
            ).save(failOnError: true)

            // Create additional employees for pagination testing
            (1..25).each { i ->
                new Employee(
                    firstName: "Test${i}",
                    lastName: "User${i}",
                    email: "test${i}@example.com",
                    age: 20 + (i % 30),
                    salary: new BigDecimal(50000 + (i * 1000)),
                    hireDate: new Date() - (i * 10).days,
                    active: i % 2 == 0,
                    status: EmployeeStatus.values()[i % EmployeeStatus.values().length],
                    priority: ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'][i % 4],
                    department: [engineering, marketing, hr][i % 3]
                ).save(failOnError: true)
            }
        }
    }
}
