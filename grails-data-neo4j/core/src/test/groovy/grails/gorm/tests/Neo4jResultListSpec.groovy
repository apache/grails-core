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


import org.apache.grails.data.neo4j.core.Neo4jGormDatastoreSpec
class Neo4jResultListSpec extends Neo4jGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(Pet)
    }


    void "Test mutate result list"() {
        given:
        new Pet(name: "foo", age: 10).save(flush:true, failOnError:true)
        new Pet(name: "bar", age: null).save(flush:true, failOnError:true)
        new Pet(name: "", age: 12).save(flush:true, failOnError:true)
        manager.session.clear()

        when:
        def list = Pet.list()
        list.add(new Pet(name: "another", age: 10))

        then:
        list.size() == 4
    }
}
