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

package org.grails.events.registry

import grails.events.subscriber.Subscription
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic

/**
 * Abstract subscription
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@AutoFinal
@CompileStatic
abstract class AbstractSubscription implements Subscription {

    final CharSequence eventKey
    private final Map<CharSequence, Collection<Subscription>> subscriptions

    AbstractSubscription(CharSequence eventKey, Map<CharSequence, Collection<Subscription>> subscriptions) {
        this.eventKey = eventKey
        this.subscriptions = subscriptions
        this.subscriptions.get(eventKey).add(this)
    }

    @Override
    Subscription cancel() {
        subscriptions.get(eventKey).remove(this)
        return this
    }

    @Override
    boolean isCancelled() {
        return !subscriptions.get(eventKey).contains(this)
    }
}
