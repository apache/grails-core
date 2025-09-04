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

package org.grails.events.spring

import groovy.transform.AutoFinal
import groovy.transform.CompileStatic

import org.springframework.context.ApplicationEvent

import grails.events.Event

/**
 * An event issues by the {@link SpringEventBus}
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@AutoFinal
@CompileStatic
class SpringEventBusEvent extends ApplicationEvent {

    final Closure replyTo

    /**
     * Create a new ApplicationEvent.
     * @param source the object on which the event initially occurred (never {@code null})
     */
    SpringEventBusEvent(Event source, Closure replyTo = null) {
        super(source)
        this.replyTo = replyTo
    }

    @Override
    Event getSource() {
        return (Event) super.getSource()
    }
}
