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

package grails.events.subscriber

import java.lang.reflect.Method

import groovy.transform.AutoFinal
import groovy.transform.CompileStatic

import grails.events.Event

/**
 * A method subscribers for methods that accept an event argument
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@AutoFinal
@CompileStatic
class MethodEventSubscriber<T> extends MethodSubscriber<Event,T> implements EventSubscriber<T> {

    MethodEventSubscriber(Object target, Method method) {
        super(target, method)
        if( !(parameterTypes.length == 1 && parameterTypes[0].isAssignableFrom(Event)) ) {
            throw new IllegalArgumentException('Specified method must accept an Event as an argument')
        }
    }
}
