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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A job which runs until it is released, so that a test can observe what the scheduler does while an
 * execution is still in flight. It has no triggers, so it only runs when a test triggers it.
 */
class LongRunningJob {

    static concurrent = false

    static final AtomicInteger STARTED = new AtomicInteger()
    static final AtomicInteger FINISHED = new AtomicInteger()
    static final CountDownLatch RELEASE = new CountDownLatch(1)

    static triggers = {
    }

    void execute() {
        STARTED.incrementAndGet()
        RELEASE.await(30, TimeUnit.SECONDS)
        FINISHED.incrementAndGet()
    }
}
