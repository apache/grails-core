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

package org.grails.events.bus

import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

import groovy.transform.AutoFinal
import groovy.transform.CompileStatic

import org.springframework.core.task.SyncTaskExecutor

import grails.events.Event
import grails.events.subscriber.Subscription
import grails.events.trigger.EventTrigger

/**
 * An event bus that uses an {@link Executor}
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@AutoFinal
@CompileStatic
class ExecutorEventBus extends AbstractEventBus {

    final Executor executor

    ExecutorEventBus(Executor executor = new SyncTaskExecutor()) {
        this.executor = executor
    }

    @Override
    protected Callable buildNotificationCallable(Event event, Collection<Subscription> eventSubscriptions, Closure reply) {
        Executor executor = this.executor
        if(executor instanceof ExecutorService) {
            ExecutorService executorService = (ExecutorService)this.executor
            return {
                executorService.submit {
                    for (Subscription subscription in eventSubscriptions) {
                        EventTrigger trigger = subscription.buildTrigger(event, reply)
                        trigger.proceed()
                    }
                }
            }
        }
        else {
            return {
                executor.execute {
                    for (Subscription subscription in eventSubscriptions) {
                        EventTrigger trigger = subscription.buildTrigger(event, reply)
                        trigger.proceed()
                    }
                }
            }
        }
    }
}
