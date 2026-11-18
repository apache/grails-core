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

package org.grails.events.gpars

import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean

import groovy.transform.AutoFinal
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import groovyx.gpars.actor.Actor

import grails.events.Event
import grails.events.subscriber.Subscription
import grails.events.trigger.EventTrigger
import org.grails.events.bus.AbstractEventBus

import static groovyx.gpars.actor.Actors.actor

/**
 * A event bus that uses GPars actors
 *
 * @since 3.3
 * @deprecated GPars is superseded by Groovy 6's {@code groovy.concurrent} toolkit. Replace the
 * {@code grails-events-gpars} dependency with {@code grails-events-groovy-concurrent}, which provides
 * {@code org.grails.events.groovy.ActorEventBus} on Groovy actors.
 */
@Deprecated(since = '9.0', forRemoval = true)
@Slf4j
@AutoFinal
@CompileStatic
class ActorEventBus extends AbstractEventBus implements Closeable {

    private static final AtomicBoolean DEPRECATION_LOGGED = new AtomicBoolean()

    final Actor actor

    @CompileDynamic
    ActorEventBus() {
        if (DEPRECATION_LOGGED.compareAndSet(false, true)) {
            log.warn('grails-events-gpars is deprecated and will be removed in a future release. ' +
                'Replace it with grails-events-groovy-concurrent, which uses the groovy.concurrent toolkit built into Groovy 6.')
        }
        actor = actor {
            loop {
                react() { Event msg ->
                    if (subscriptions.containsKey(msg.id)) {
                        Collection<Subscription> subscriptions = subscriptions.get(msg.id)
                        for (Subscription sub : subscriptions) {
                            try {
                                EventTrigger eventTrigger = sub.buildTrigger(msg)
                                replyIfExists(eventTrigger.proceed())
                            } catch (Throwable e) {
                                replyIfExists(e)
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected Callable buildNotificationCallable(Event event, Collection<Subscription> eventSubscriptions, Closure reply) {
        Actor actor = this.actor
        return {
            if (reply != null) {
                actor.sendAndContinue(event, reply)
            }
            else {
                actor.send(event)
            }
        }
    }

    @Override
    void close() throws IOException {
        if (actor.isActive()) {
            actor.stop()
        }
    }

    @Override
    boolean isActive() {
        return actor.isActive()
    }
}
