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
import org.grails.datastore.mapping.core.DatastoreUtils
import spock.lang.Specification
import org.grails.datastore.mapping.model.PersistentEntity
import grails.gorm.annotation.Entity
import org.springframework.context.ApplicationEventPublisher
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PostInsertEvent

class SimpleMapEventsSpec extends Specification {

    void 'test events are fired during persistence'() {
        given:
        def events = []
        def publisher = [
            publishEvent: { event -> events << event }
        ] as ApplicationEventPublisher
        
        def datastore = new SimpleMapDatastore(DatastoreUtils.createPropertyResolver(null), publisher, TestEventEntity)
        def session = datastore.connect()
        
        when:
        def entity = new TestEventEntity(name: 'test')
        session.insert(entity)
        session.flush()
        
        then:
        events.any { it instanceof PreInsertEvent }
        events.any { it instanceof PostInsertEvent }
    }
}

@Entity
class TestEventEntity {

    Long id
    String name
}
