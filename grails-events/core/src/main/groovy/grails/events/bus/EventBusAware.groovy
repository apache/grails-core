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

import groovy.transform.AutoFinal
import groovy.transform.CompileStatic

import org.springframework.beans.factory.annotation.Autowired

/**
 * Trait for classes aware of the event bus
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@AutoFinal
@CompileStatic
trait EventBusAware {

    private EventBus eventBus

    /**
     * Sets the target event bus to use
     *
     * @param eventBus The event bus
     */
    @Autowired
    void setTargetEventBus(EventBus eventBus) {
        this.eventBus = eventBus
    }

    /**
     * @return Retrieves the event bus
     */
    EventBus getEventBus() {
        if(this.eventBus == null) {
            this.eventBus = new EventBusBuilder().build()
        }
        return this.eventBus
    }
}
