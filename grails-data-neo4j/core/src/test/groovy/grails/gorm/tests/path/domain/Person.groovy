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

package grails.gorm.tests.path.domain

import grails.gorm.annotation.Entity
import grails.neo4j.Node

import static grails.neo4j.mapping.MappingBuilder.*
import groovy.transform.EqualsAndHashCode

/**
 * Created by graemerocher on 14/03/2017.
 */
@Entity
// tag::class[]
@EqualsAndHashCode(includes = 'name')
class Person implements Node<Person> {
    String name
    static hasMany = [friends: Person]

    static mapping = node {
        id(generator:'assigned', name:'name')
    }
}
// end::class[]
