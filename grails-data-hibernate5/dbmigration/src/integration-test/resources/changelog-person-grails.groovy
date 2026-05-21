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

import testapp.Person

databaseChangeLog = {
    changeSet(id: 'create-person-grails', author: 'integration-test') {

        grailsChange {
            change {
                Person person = new Person()
                person.firstName = 'Joseph1'
                person.lastName = 'Holmes'
                person.age = 56
                person.gender = 'male'
                person.cell = '734-776-7738'
                person.emailAddress = 'jhomes@example.com'
                person.save(flush: true, failOnError: true)
            }
            rollback {
                confirm('Done: Rollback person Jone')
            }
        }
    }
}