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
package org.grails.core.lifecycle;

import java.util.Collection;
import java.util.LinkedHashSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import grails.util.Holders;

/**
 * Operations that should be executed on shutdown.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class ShutdownOperations {
    private static final Log LOG = LogFactory.getLog(ShutdownOperations.class);

    private static final Collection<Runnable> shutdownOperations = new LinkedHashSet<>();
    private static final Collection<Runnable> preservedShutdownOperations = new LinkedHashSet<>();

    public static final Runnable DEFAULT_SHUTDOWN_OPERATION = Holders::reset;

    static {
        resetOperations();
    }

    /**
     * Runs the shutdown operations
     */
    public static synchronized void runOperations() {
        try {
            for (Runnable shutdownOperation : shutdownOperations) {
                try {
                    shutdownOperation.run();
                } catch (Exception e) {
                    LOG.warn("Error occurred running shutdown operation: " + e.getMessage(), e);
                }
            }
        } finally {
            shutdownOperations.clear();
            shutdownOperations.addAll(preservedShutdownOperations);
        }
    }

    /**
     * Adds a shutdown operation which will be run once for the next shutdown
     * @param runnable The runnable operation
     */
    public static synchronized void addOperation(Runnable runnable) {
        addOperation(runnable, false);
    }

    /**
     * Adds a shutdown operation
     * @param runnable The runnable operation
     * @param preserveForNextShutdown should preserve the operation for subsequent shutdowns, useful in tests
     */
    public static synchronized void addOperation(Runnable runnable, boolean preserveForNextShutdown) {
        shutdownOperations.add(runnable);
        if(preserveForNextShutdown) {
            preservedShutdownOperations.add(runnable);
        }
    }

    /**
     * Clears all shutdown operations without running them. Also clears operations that are kept after running operations.
     */
    public static synchronized void resetOperations() {
        shutdownOperations.clear();
        preservedShutdownOperations.clear();
        // default operations
        addOperation(DEFAULT_SHUTDOWN_OPERATION, true);
    }
}
