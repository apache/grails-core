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

package org.grails.events.gorm

import java.lang.reflect.Method

import groovy.transform.AutoFinal
import groovy.transform.CompileStatic

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.springframework.util.ReflectionUtils

import grails.events.annotation.gorm.Listener
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent

/**
 * Marks a class as a synchronous listener of GORM events
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@AutoFinal
@CompileStatic
trait GormAnnotatedListener extends GormAnnotatedSubscriber {

    private static final Logger log = LoggerFactory.getLogger(GormAnnotatedListener)
    /**
     * Whether the listener supports the given event
     * @param event The event
     * @return True if it does
     */
    boolean supports(AbstractPersistenceEvent event) {
        getSubscribedEvents().contains(event.getClass())
    }
    /**
     * Dispatch the event to this listener
     * @param event
     */
    void dispatch(AbstractPersistenceEvent event) {
        def entity = event.getEntityObject()
        for(Method method : getSubscribedMethods()) {
            Class[] types = method.getAnnotation(Listener)?.value()
            boolean applies = types == null || types.length == 0 || types.any() { Class cls -> cls.isInstance(entity) }
            if(applies && method.parameterTypes[0].isInstance(event)) {
                try {
                    log.debug("Invoking method [{}] for event [{}]", method, event)
                    ReflectionUtils.invokeMethod(method, this, event)
                } catch (Throwable e) {
                    log.error("Error triggering event [$event] for listener [${method}]: $e.message", e)
                    throw e
                }
            }
        }
    }
}
