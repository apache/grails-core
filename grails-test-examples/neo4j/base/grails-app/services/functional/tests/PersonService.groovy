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

package functional.tests

import grails.gorm.services.Service
import grails.neo4j.Path
import grails.neo4j.services.Cypher

// tag::class[]
@Service(Person)
interface PersonService {
// end::class[]

    // tag::findPath[]
    Path<Person, Person> findPath(Person from, Person to)
    // end::findPath[]

    // tag::findPathCypher[]
    @Cypher("""MATCH ${Person from},${Person to}, p = shortestPath(($from)-[*..15]-($to)) 
               WHERE $from.name = $start AND $to.name = $end 
               RETURN p""")
    Path<Person, Person> findPath(String start, String end)
    // end::findPathCypher[]

    // tag::updatePerson[]
    @Cypher("""MATCH ${Person p} 
               WHERE $p.name = $name  
               SET p.age = $age""")
    void updatePerson(String name, int age)
    // end::updatePerson[]
}
