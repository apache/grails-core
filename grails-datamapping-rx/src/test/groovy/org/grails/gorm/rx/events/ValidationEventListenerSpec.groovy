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

package org.grails.gorm.rx.events

import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.event.PostInsertEvent
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.rx.RxDatastoreClient
import org.springframework.validation.Errors
import spock.lang.Specification
import spock.lang.Unroll

class ValidationEventListenerSpec extends Specification {

    ValidationEventListener listener = new ValidationEventListener()

    PreInsertEvent buildEvent(Object entityObject) {
        PersistentEntity entity = Stub(PersistentEntity)
        EntityAccess entityAccess = Stub(EntityAccess) {
            getEntity() >> entityObject
        }
        new PreInsertEvent(new Object(), entity, entityAccess)
    }

    void "supports pre-insert and pre-update event types but not others"() {
        expect:
        listener.supportsEventType(PreInsertEvent)
        listener.supportsEventType(PreUpdateEvent)

        and:
        !listener.supportsEventType(PostInsertEvent)
    }

    void "supports rx datastore client source types but not others"() {
        expect:
        listener.supportsSourceType(RxDatastoreClient)
        !listener.supportsSourceType(String)
    }

    void "does not cancel the event when the entity is not GormValidateable"() {
        given:
        PreInsertEvent event = buildEvent(new Object())

        when:
        listener.onApplicationEvent(event)

        then:
        !event.isCancelled()
    }

    @Unroll
    void "cancels the event when validation is skipped and errors are already present: #expectedCancelled"() {
        given:
        Errors errors = Stub(Errors) {
            hasErrors() >> expectedCancelled
        }
        GormValidateable entityObject = Mock(GormValidateable) {
            shouldSkipValidation() >> true
            getErrors() >> errors
        }
        PreInsertEvent event = buildEvent(entityObject)

        when:
        listener.onApplicationEvent(event)

        then:
        event.isCancelled() == expectedCancelled

        where:
        expectedCancelled << [true, false]
    }

    void "does not cancel the event when validation is skipped and there are no errors to report"() {
        given:
        GormValidateable entityObject = Mock(GormValidateable) {
            shouldSkipValidation() >> true
            getErrors() >> null
        }
        PreInsertEvent event = buildEvent(entityObject)

        when:
        listener.onApplicationEvent(event)

        then:
        !event.isCancelled()
    }

    @Unroll
    void "cancels the event based on the validate() outcome when validation is not skipped: #validationResult"() {
        given:
        GormValidateable entityObject = Mock(GormValidateable) {
            shouldSkipValidation() >> false
            validate() >> validationResult
        }
        PreUpdateEvent event = new PreUpdateEvent(new Object(), Stub(PersistentEntity), Stub(EntityAccess) {
            getEntity() >> entityObject
        })

        when:
        listener.onApplicationEvent(event)

        then:
        event.isCancelled() == !validationResult

        where:
        validationResult << [true, false]
    }

    void "reports the default listener order"() {
        expect:
        listener.getOrder() == 0
    }
}
