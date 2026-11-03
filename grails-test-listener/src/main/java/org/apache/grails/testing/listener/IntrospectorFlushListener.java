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
package org.apache.grails.testing.listener;

import java.beans.Introspector;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

/**
 * Flushes the caches of the JDK Introspector after each test class.
 *
 * <p>Groovy reads the BeanInfo of a class when it builds the class's MetaClass, and the Introspector
 * keeps it in a cache that releases an entry only when its class is unloaded. The classes of a test
 * JVM are never unloaded, so without a flush the cache holds the BeanInfo of every class, closure
 * and domain class any test in the JVM has touched. The JUnit Platform launcher registers this
 * listener from {@code META-INF/services}, so every test class run on the platform, Spock or JUnit,
 * gets the flush.</p>
 *
 * <p>{@link Introspector#flushCaches()} clears the cache of the calling thread's thread group. The
 * launcher calls this listener on the thread that ran the class, which is the thread group the
 * class's tests built their MetaClasses in.</p>
 */
public final class IntrospectorFlushListener implements TestExecutionListener {

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (isTestClass(testIdentifier)) {
            Introspector.flushCaches();
        }
    }

    private static boolean isTestClass(TestIdentifier testIdentifier) {
        return testIdentifier.isContainer() && testIdentifier.getSource().filter(ClassSource.class::isInstance).isPresent();
    }
}
