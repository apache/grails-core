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

package gorm

/**
 * Author domain class demonstrating hasMany relationship to Book.
 * Used to test scaffolding with one-to-many associations.
 */
class Author {

    String name
    String email
    Date birthDate
    String biography
    Boolean active = true

    static hasMany = [books: Book]

    static constraints = {
        name blank: false, size: 1..100
        email email: true, unique: true, blank: false
        birthDate nullable: true
        biography nullable: true, maxSize: 2000, widget: 'textarea'
        active nullable: false
    }

    static mapping = {
        biography type: 'text'
    }

    String toString() {
        name
    }
}
