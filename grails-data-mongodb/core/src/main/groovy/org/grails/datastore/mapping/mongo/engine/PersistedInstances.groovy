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
package org.grails.datastore.mapping.mongo.engine

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic

/**
 * The instances that were loaded from, or inserted into, the database and have an assigned identifier. An instance with an
 * assigned identifier gets its identifier before it is saved, so nothing on the instance tells a detached instance that is
 * updated from a new instance that is inserted. The instances are held weakly and compared by identity, not with equals.
 *
 * @since 7.0.18
 */
@CompileStatic
final class PersistedInstances {

    private final Map<InstanceKey, Boolean> keys = new ConcurrentHashMap<>()
    private final ReferenceQueue<Object> queue = new ReferenceQueue<>()

    void add(Object instance) {
        if (instance != null) {
            expunge()
            keys.put(new InstanceKey(instance, queue), Boolean.TRUE)
        }
    }

    void remove(Object instance) {
        if (instance != null) {
            keys.remove(new InstanceKey(instance, null))
        }
    }

    boolean contains(Object instance) {
        instance != null && keys.containsKey(new InstanceKey(instance, null))
    }

    private void expunge() {
        Object collected
        while ((collected = queue.poll()) != null) {
            keys.remove(collected)
        }
    }

    private static final class InstanceKey extends WeakReference<Object> {

        private final int hash

        InstanceKey(Object instance, ReferenceQueue<Object> queue) {
            super(instance, queue)
            hash = System.identityHashCode(instance)
        }

        @Override
        int hashCode() {
            hash
        }

        @Override
        boolean equals(Object other) {
            if (this.is(other)) {
                return true
            }
            if (!(other instanceof InstanceKey)) {
                return false
            }
            Object mine = get()
            mine != null && mine.is(((InstanceKey) other).get())
        }
    }
}
