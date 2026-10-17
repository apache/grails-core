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
package org.apache.grails.events.aot;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Registers the promise factories the event bus reads its executor from.
 *
 * <p>The event bus runs its listeners on the executor of the application's promise factory, and reads it as
 * the factory's {@code executor} bean property so that events need not depend on the async module. That is a
 * reflective call to the getter, which an image keeps only when asked, so an image built without these could
 * not create the event bus, and with it the application context.</p>
 *
 * <p>Named rather than referenced, for the same reason the property is read: the factories are in a module
 * this one does not depend on. Each is registered only when it is present.</p>
 *
 * @since 9.0
 */
public class PromiseFactoryExecutorRuntimeHints implements RuntimeHintsRegistrar {

    /** The promise factories that expose the executor they run on. */
    private static final String[] PROMISE_FACTORIES = {
        "org.grails.async.factory.future.CompletableFuturePromiseFactory",
        "org.grails.async.factory.future.VirtualThreadPromiseFactory",
        "org.grails.async.factory.groovy.AwaitablePromiseFactory"
    };

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        for (String promiseFactory : PROMISE_FACTORIES) {
            hints.reflection().registerTypeIfPresent(classLoader, promiseFactory, MemberCategory.INVOKE_PUBLIC_METHODS);
        }
    }

}
