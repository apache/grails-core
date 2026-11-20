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

/**
 * Comprehensive domain class for testing all common field types
 * supported by grails-fields and grails-scaffolding plugins.
 */
class Employee {

    // Basic text fields
    String firstName
    String lastName
    String email

    // Large text field (textarea)
    String biography

    // Numeric fields
    Integer age
    BigDecimal salary

    // Date fields
    Date hireDate
    Date lastLogin

    // Boolean field
    Boolean active = true

    // Enum field
    EmployeeStatus status

    // InList constraint field
    String priority

    // URL field
    URL website

    // Locale, Currency, TimeZone fields
    Locale preferredLocale
    Currency preferredCurrency
    TimeZone preferredTimezone

    // File upload field
    byte[] photo

    // Association - belongsTo (select dropdown)
    Department department

    // Association - hasMany (multi-select)
    static hasMany = [projects: Project]

    // Embedded object
    Address address

    // Timestamps
    Date dateCreated
    Date lastUpdated

    static embedded = ['address']

    static constraints = {
        firstName blank: false, size: 1..50
        lastName blank: false, size: 1..50
        email email: true, unique: true, blank: false
        biography nullable: true, maxSize: 5000, widget: 'textarea'
        age min: 18, max: 120, nullable: true
        salary min: 0.0, nullable: true
        hireDate nullable: true
        lastLogin nullable: true
        active nullable: false
        status nullable: true
        priority inList: ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'], nullable: true
        website nullable: true, url: true
        preferredLocale nullable: true
        preferredCurrency nullable: true
        preferredTimezone nullable: true
        photo nullable: true, maxSize: 1024 * 1024 // 1MB max
        department nullable: true
        address nullable: true
        dateCreated nullable: true
        lastUpdated nullable: true
    }

    static mapping = {
        biography type: 'text'
    }

    String toString() {
        "$firstName $lastName"
    }
}

/**
 * Enum for employee status - tests enum select rendering
 */
enum EmployeeStatus {
    ACTIVE('Active'),
    ON_LEAVE('On Leave'),
    TERMINATED('Terminated'),
    PROBATION('Probation')

    final String displayName

    EmployeeStatus(String displayName) {
        this.displayName = displayName
    }

    String toString() {
        displayName
    }
}
