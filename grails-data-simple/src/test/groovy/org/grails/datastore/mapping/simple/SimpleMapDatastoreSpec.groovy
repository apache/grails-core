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

package org.grails.datastore.mapping.simple

import org.grails.datastore.mapping.core.connections.ConnectionSource
import spock.lang.Specification
import org.grails.datastore.mapping.model.PersistentEntity
import grails.gorm.annotation.Entity

class SimpleMapDatastoreSpec extends Specification {

    void 'test backing map isolation for multiple datasources'() {
        given:
        def datastore = new SimpleMapDatastore([ConnectionSource.DEFAULT, 'one'], TestEntity)
        def secondary = datastore.getDatastoreForConnection('one')

        when:
        def entity = datastore.mappingContext.getPersistentEntity(TestEntity.name)
        
        // This is what the failing test does: it expects backingMap[entityName] to be isolated per datastore
        // However, SimpleMapDatastore.getBackingMap() returns the shared static map.
        
        then:
        datastore.connectionName == ConnectionSource.DEFAULT
        secondary.connectionName == 'one'
        
        when:
        def session = datastore.connect()
        session.beginTransaction()
        def t1 = new TestEntity(name: 'default')
        session.insert(t1)
        session.flush()
        
        def session2 = secondary.connect()
        session2.beginTransaction()
        def t2 = new TestEntity(name: 'secondary')
        session2.insert(t2)
        session2.flush()
        
        then:
        datastore.backingMap[TestEntity.name].size() == 1
        secondary.backingMap[TestEntity.name].size() == 1
    }
}

@Entity
class TestEntity {

    Long id
    String name
}
