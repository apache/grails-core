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

package functionaltests.springevents

import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationEventPublisherAware
import grails.gorm.transactions.Transactional

/**
 * Service that publishes Spring application events.
 */
class EventPublisherService implements ApplicationEventPublisherAware {

    ApplicationEventPublisher applicationEventPublisher

    @Override
    void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
        this.applicationEventPublisher = publisher
    }

    /**
     * Publishes a custom application event.
     */
    void publishCustomEvent(String message, Map<String, Object> payload = [:]) {
        def event = new CustomApplicationEvent(this, message, payload)
        applicationEventPublisher.publishEvent(event)
    }

    /**
     * Publishes a user action event.
     */
    void publishUserAction(String userId, String action, Map<String, String> metadata = [:]) {
        def event = new UserActionEvent(this, userId, action, metadata)
        applicationEventPublisher.publishEvent(event)
    }

    /**
     * Publishes a priority event.
     */
    void publishPriorityEvent(int priority, String data) {
        def event = new PriorityEvent(this, priority, data)
        applicationEventPublisher.publishEvent(event)
    }

    /**
     * Publishes multiple events in sequence.
     */
    void publishMultipleEvents(int count) {
        count.times { i ->
            publishCustomEvent("Event #${i + 1}", [index: i])
        }
    }

    /**
     * Publishes event within a transactional context.
     */
    @Transactional
    void publishEventTransactional(String message) {
        publishCustomEvent("TRANSACTIONAL:${message}")
    }
}
