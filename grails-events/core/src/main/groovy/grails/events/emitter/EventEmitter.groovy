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

package grails.events.emitter

import grails.events.Event
import org.springframework.transaction.event.TransactionPhase

/**
 * An emitter sends events
 *
 * @author Graeme Rocher
 * @since 3.3
 */
interface EventEmitter {

    /**
     * Notify of an event
     *
     * @param eventId The event
     * @param data The data
     *
     * @return This emitter
     */
    EventEmitter notify(CharSequence eventId, Object...data)

    /**
     * Notify of an event
     *
     * @param event The event
     *
     * @return This emitter
     */
    EventEmitter notify(Event event)

    /**
     * Notify of an event
     *
     * @param event The event
     * @param transactionPhase The transaction Phase to use if a transaction is present (defaults to {@link TransactionPhase#AFTER_COMMIT}
     *
     * @return This emitter
     */
    EventEmitter notify(Event event, TransactionPhase transactionPhase)

    /**
     * Synonym for {@link #notify(Event)}
     */
    EventEmitter publish(CharSequence eventId, Object...data)

    /**
     * Synonym for {@link #notify(Event)}
     */
    EventEmitter publish(Event event)

    /**
     * Synonym for {@link #notify(Event, org.springframework.transaction.event.TransactionPhase)}
     */
    EventEmitter publish(Event event, TransactionPhase transactionPhase)
    /**
     * Send and event and receive a reply. If the EventBus is asynchronous the reply may be invoked on a different thread to the caller
     *
     * @param event The event
     * @param reply The reply logic
     * @return This emitter
     */
    EventEmitter sendAndReceive(Event event, Closure reply)

    /**
     * Send and event and receive a reply. If the EventBus is asynchronous the reply may be invoked on a different thread to the caller
     *
     * @param eventId The event
     * @param reply The reply logic
     * @return This emitter
     */
    EventEmitter sendAndReceive(CharSequence eventId, Object data, Closure reply)
}
