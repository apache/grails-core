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
package org.apache.grails.startup;

import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.SmartLifecycle;

/**
 * Runs an action when the context's lifecycle reaches a phase next to the embedded web server's own:
 * just before the server starts, to hand the port over, or just after, to say the application is
 * now running its startup hooks.
 */
final class StartupProgressLifecycle implements SmartLifecycle {

    private final int phase;

    private final Runnable action;

    private volatile boolean running;

    private StartupProgressLifecycle(int phase, Runnable action) {
        this.phase = phase;
        this.action = action;
    }

    static StartupProgressLifecycle beforeWebServer(Runnable action) {
        return new StartupProgressLifecycle(WebServerApplicationContext.START_STOP_LIFECYCLE_PHASE - 1, action);
    }

    static StartupProgressLifecycle afterWebServer(Runnable action) {
        return new StartupProgressLifecycle(WebServerApplicationContext.START_STOP_LIFECYCLE_PHASE + 1, action);
    }

    @Override
    public void start() {
        running = true;
        action.run();
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return phase;
    }
}
