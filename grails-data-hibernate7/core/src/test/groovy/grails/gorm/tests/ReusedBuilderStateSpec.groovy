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

import spock.lang.Issue

import grails.gorm.annotation.Entity

@Issue('https://github.com/apache/grails-core/issues/16862')
class ReusedBuilderStateSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(ReusedStateTeam)
    }

    void setup() {
        ['both', 'x', 'y', 'z'].each { new ReusedStateTeam(name: it).save(failOnError: true) }
        ReusedStateTeam.withSession { it.flush() }
    }

    void 'second call on a reused builder matches a new builder: #label'() {
        when:
        def c = ReusedStateTeam.createCriteria()
        first(c)
        def reused = second(c)
        def fresh = second(ReusedStateTeam.createCriteria())

        then:
        norm(reused) == norm(fresh)

        where:
        label                     | first                                              | second
        'list then count'         | { it.list { eq('name', 'both') } }                 | { it.count { } }
        'list then count eq'      | { it.list { eq('name', 'both') } }                 | { it.count { eq('name', 'x') } }
        'list then get'           | { it.list { eq('name', 'both') } }                 | { it.get { eq('name', 'x') } }
        'list then listDistinct'  | { it.list { eq('name', 'both') } }                 | { it.listDistinct { } }
        'projected list then list'| { it.list { projections { property('name') } } }  | { it.list { } }
        'count then count'        | { it.count { } }                                   | { it.count { } }
        'count then list'         | { it.count { } }                                   | { it.list { } }
        'get then list'           | { it.get { eq('name', 'both') } }                  | { it.list { } }
        'get then count'          | { it.get { eq('name', 'both') } }                  | { it.count { } }
        'paged list then list'    | { it.list(max: 1) { } }                            | { it.list { } }
    }

    private static Object norm(Object o) {
        o instanceof Collection ? o.collect { it instanceof ReusedStateTeam ? "T:" + it.name : it }.sort { it.toString() } : (o instanceof ReusedStateTeam ? "T:" + o.name : o)
    }
}

@Entity
class ReusedStateTeam {
    String name
}
