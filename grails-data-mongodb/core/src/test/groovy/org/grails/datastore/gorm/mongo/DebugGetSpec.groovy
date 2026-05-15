/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License')
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.datastore.gorm.mongo

import org.apache.grails.data.mongo.core.MongoDatastoreSpec
import grails.persistence.Entity

class DebugGetSpec extends MongoDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([TestPlace])
    }

    void 'test Place.get() returns entity'() {
        when: 'A simple Place is saved'
        def p = new TestPlace(name: 'Test')
        p.save(flush: true, validate: false)
        def savedId = p.id
        println "Saved TestPlace with id: ${savedId}"
        
        and: 'The session is cleared'
        manager.session.clear()
        
        and: 'TestPlace.get() is called'
        println "Calling TestPlace.get(${savedId})..."
        def retrieved = TestPlace.get(savedId)
        println "Retrieved: ${retrieved}"
        
        then: 'The TestPlace should be retrieved'
        retrieved != null
        retrieved.id == savedId
        retrieved.name == 'Test'
    }
}

@Entity
class TestPlace {

    Long id
    String name
}
