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

package grails.events

import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * Wraps an event
 *
 * @since 3.3
 * @author Graeme Rocher
 */
@CompileStatic
@AutoFinal
@EqualsAndHashCode
@ToString
class Event<T> extends EventObject {

    /**
     * The id of the event
     */
    final String id
    /**
     * The data of the event
     */
    final T data
    /**
     * The parameters for the event
     */
    final Map<String, Object> parameters

    Event(String id, T data) {
        this(id, Collections.emptyMap() as Map<String, Object>, data)
    }

    Event(String id, Map<String, Object> parameters, T data) {
        super(id)
        this.id = id
        this.data = data
        this.parameters = Collections.unmodifiableMap(parameters)
    }

    /**
     * Wrap the given object with an {@link Event}.
     *
     * @param obj
     *     The object to from.
     *
     * @return The new {@link Event}.
     */
    static <T> Event<T> from(String id, T obj) {
        return new Event<T>(id, obj)
    }

    /**
     * Wrap the given object with an {@link Event}.
     *
     * @param obj
     *     The object to from.
     *
     * @return The new {@link Event}.
     */
    static <T> Event<T> from(String id, Map<String, Object> parameters, T obj) {
        return new Event<T>(id, parameters, obj)
    }
}
