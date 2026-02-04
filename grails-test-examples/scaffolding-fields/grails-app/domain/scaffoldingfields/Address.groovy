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
 * Address embedded class - tests embedded object rendering
 * in scaffolded forms (inline field groups).
 */
class Address {

    String street
    String city
    String postalCode
    String country

    static constraints = {
        street nullable: true, maxSize: 200
        city nullable: true, maxSize: 100
        postalCode nullable: true, maxSize: 20
        country nullable: true, maxSize: 100
    }

    String toString() {
        [street, city, postalCode, country].findAll { it }.join(', ')
    }
}
