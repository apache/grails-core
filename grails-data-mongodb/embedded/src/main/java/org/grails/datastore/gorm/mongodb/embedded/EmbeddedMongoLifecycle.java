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
package org.grails.datastore.gorm.mongodb.embedded;

import org.springframework.context.SmartLifecycle;

/**
 * Stops the embedded server while the process is checkpointed and starts it again when the
 * process is restored.
 *
 * <p>CRaC refuses to checkpoint a process that holds an open socket, and the in-memory
 * backend holds several: a listening socket, the event loops behind it, and the server side
 * of every connection the driver has open. Without this, an application using an embedded
 * MongoDB cannot be checkpointed at all.
 *
 * <p>Spring stops {@link SmartLifecycle} beans before the checkpoint and starts them again
 * after the restore, so taking part costs no dependency on {@code org.crac}. It also means
 * the same bean covers an ordinary shutdown.
 *
 * @author Grails
 * @since 8.0
 */
public class EmbeddedMongoLifecycle implements SmartLifecycle {

    public static final String BEAN_NAME = "embeddedMongoLifecycle";

    /**
     * Spring starts in ascending phase order and stops in descending, so a phase below
     * everything else makes the server the first thing up and the last thing down. It has to
     * outlive the datastore that talks to it, which uses
     * {@code MongoDatastore.LIFECYCLE_PHASE}, itself below the web server's.
     */
    public static final int PHASE = -2000;

    private final RunningEmbeddedMongo running;

    /** The server is already listening by the time this is constructed. */
    private volatile boolean started = true;

    public EmbeddedMongoLifecycle(RunningEmbeddedMongo running) {
        this.running = running;
    }

    @Override
    public void start() {
        if (!this.started) {
            this.running.restart();
            this.started = true;
        }
    }

    @Override
    public void stop() {
        if (this.started) {
            this.running.stop();
            this.started = false;
        }
    }

    @Override
    public boolean isRunning() {
        return this.started;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }
}
