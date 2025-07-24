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

package grails.events.bus

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.grails.events.bus.ExecutorEventBus

/**
 * Tries to build the default event bus
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@CompileStatic
@Slf4j
class EventBusBuilder {

    /**
     * @return Tries to auto discover and build the event bus
     */
    EventBus build() {
        List<EventBus> eventBuses = ServiceLoader.load(EventBus).toList()
        if(eventBuses.size() == 1) {
            EventBus eventBus = eventBuses.get(0)
            log.debug('Found event bus class to use [{}]', eventBus.getClass().name)
            return eventBus
        }
        else if(eventBuses.size() > 1) {
            throw new IllegalStateException("More than one event bus implementation found on classpath ${eventBuses}. Remove one to continue.")
        }
        else {
            return createDefaultEventBus()
        }
    }

    protected EventBus createDefaultEventBus() {
        log.warn('No event bus implementations found on classpath, using synchronous implementation.')
        return new ExecutorEventBus()
    }
}
