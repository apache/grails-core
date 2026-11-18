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
package org.grails.events.groovy

import java.util.concurrent.Callable
import java.util.concurrent.Executor

import groovy.concurrent.Actor
import groovy.concurrent.ActorOptions
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import grails.events.Event
import grails.events.subscriber.Subscription
import grails.events.trigger.EventTrigger
import org.grails.events.bus.AbstractEventBus

/**
 * An event bus that delivers events through a Groovy {@link Actor}.
 *
 * <p>Every notification is queued on a single reactor actor, so subscribers run one event at a time,
 * in publication order, off the publishing thread. The actor runs on Groovy's default async executor
 * unless an executor is supplied.</p>
 *
 * @since 9.0
 */
@Slf4j
@AutoFinal
@CompileStatic
class ActorEventBus extends AbstractEventBus implements Closeable {

    private final Actor<Notification> actor

    ActorEventBus() {
        this(null)
    }

    /**
     * @param executor the executor the actor's processing loop runs on, or {@code null}
     *                 for Groovy's default async executor
     */
    ActorEventBus(Executor executor) {
        ActorOptions options = executor == null ? ActorOptions.DEFAULTS : ActorOptions.DEFAULTS.withExecutor(executor)
        this.actor = Actor.reactor((Notification notification) -> deliver(notification), options)
    }

    @Override
    protected Callable buildNotificationCallable(Event event, Collection<Subscription> eventSubscriptions, Closure reply) {
        Notification notification = new Notification(event, eventSubscriptions, reply)
        return { ->
            actor.send(notification)
            return null
        }
    }

    @Override
    boolean isActive() {
        return actor.isActive()
    }

    @Override
    void close() {
        if (actor.isActive()) {
            actor.stop()
        }
    }

    private Object deliver(Notification notification) {
        for (Subscription subscription : notification.subscriptions()) {
            EventTrigger trigger = subscription.buildTrigger(notification.event(), notification.reply())
            try {
                trigger.proceed()
            }
            catch (Throwable failure) {
                log.error("Error triggering event [${notification.event().id}]: ${failure.message}", failure)
            }
        }
        return null
    }

    /**
     * A queued notification: the event, the subscriptions it was published to, and the optional reply handler.
     */
    record Notification(Event event, Collection<Subscription> subscriptions, Closure reply) {
    }
}
