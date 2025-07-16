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

package example

import grails.gorm.transactions.Rollback
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 20/03/2017.
 */
class PersonServiceSpec extends Specification {

    @Shared @AutoCleanup Neo4jDatastore datastore = new Neo4jDatastore(getClass().getPackage())
    @Shared PersonService personService = datastore.getService(PersonService)

    @Rollback
    void "test person service"() {
        when:"A new person is saved"
        new Person(name: "Fred").save(flush:true)

        then:"the person can be found"
        personService.findPerson("Fred") != null
    }
}
