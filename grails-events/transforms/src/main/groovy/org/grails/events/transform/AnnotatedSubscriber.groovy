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

package org.grails.events.transform

import java.beans.Introspector
import java.lang.reflect.Method

import groovy.transform.CompileStatic

import jakarta.annotation.PostConstruct

import org.springframework.util.ReflectionUtils

import grails.events.Event
import grails.events.annotation.Events
import grails.events.annotation.Subscriber
import grails.events.bus.EventBusAware
import grails.events.subscriber.MethodEventSubscriber
import grails.events.subscriber.MethodSubscriber
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.events.EventIdUtils

/**
 * Registers subscribed methods. Used by the {@link Subscriber} transformation
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@CompileStatic
trait AnnotatedSubscriber extends EventBusAware {

    List<Method> getSubscribedMethods() {
        return []
    }

    @PostConstruct
    void registerMethods() {
        Events events = getClass().getAnnotation(Events)
        for(Method m in subscribedMethods) {
            ReflectionUtils.makeAccessible(m)
            Subscriber sub = m.getAnnotation(Subscriber)
            if(sub != null) {
                String eventId = sub.value()
                if(!eventId) {
                    eventId = EventIdUtils.eventIdForMethodName(m.name)
                }

                String namespace = events?.namespace()
                if(namespace) {
                    eventId = namespace + ':' + eventId
                }

                Class[] parameterTypes = m.parameterTypes
                boolean hasArgument = parameterTypes.length == 1
                if(hasArgument && AbstractPersistenceEvent.isAssignableFrom(parameterTypes[0])) {
                    eventId = "gorm:${Introspector.decapitalize(parameterTypes[0].simpleName)}" - "Event"
                    eventBus.subscribe(eventId, new MethodSubscriber(this, m))
                }
                else if(hasArgument && parameterTypes[0].isAssignableFrom(Event)) {
                    eventBus.subscribe(eventId, new MethodEventSubscriber(this, m))
                }
                else {
                    eventBus.subscribe(eventId, new MethodSubscriber(this, m))
                }
            }
        }
    }
}
