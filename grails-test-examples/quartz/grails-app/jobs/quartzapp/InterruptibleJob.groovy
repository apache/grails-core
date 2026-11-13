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

package quartzapp

import java.util.concurrent.atomic.AtomicInteger

/**
 * A job which keeps working until it is interrupted, the way an application stops work that has been
 * running for too long. It has no triggers, so it only runs when a test triggers it.
 */
class InterruptibleJob {

    static final AtomicInteger STARTED = new AtomicInteger()
    static final AtomicInteger INTERRUPTED = new AtomicInteger()

    private volatile boolean interrupted

    static triggers = {
    }

    void execute() {
        STARTED.incrementAndGet()
        long deadline = System.currentTimeMillis() + 30_000
        while (!interrupted && System.currentTimeMillis() < deadline) {
            sleep 50
        }
        if (interrupted) {
            INTERRUPTED.incrementAndGet()
        }
    }

    void interrupt() {
        interrupted = true
    }
}
