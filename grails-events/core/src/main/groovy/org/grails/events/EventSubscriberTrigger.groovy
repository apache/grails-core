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

package org.grails.events

import grails.events.Event
import grails.events.subscriber.EventSubscriber
import grails.events.subscriber.Subscriber
import grails.events.trigger.EventTrigger
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Simple trigger for an Subscriber
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@Slf4j
@AutoFinal
@CompileStatic
class EventSubscriberTrigger implements EventTrigger {

    final Event event
    final Subscriber subscriber

    EventSubscriberTrigger(Event event, Subscriber subscriber) {
        this.event = event
        this.subscriber = subscriber
    }

    @Override
    Object proceed() {
        try {
            if (subscriber instanceof EventSubscriber) {
                return subscriber.call(event)
            } else {
                return subscriber.call(event.data)
            }
        } catch (Throwable e) {
            log.error("Error triggering event [$event.id] for subscriber [${subscriber}]: $e.message", e)
            throw e
        }
    }
}
