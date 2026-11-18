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
package quartz.jobs

import grails.plugins.quartz.QuartzJob

import java.util.concurrent.atomic.AtomicInteger

/**
 * A job which records that it ran, so that a test can prove the scheduler really fires it.
 *
 * The {@code QuartzJob} trait is applied explicitly here; in an application it is contributed to every
 * class under {@code grails-app/jobs} by {@code QuartzJobTraitInjector} at compile time.
 */
class ContextSpecJob implements QuartzJob {

    static final AtomicInteger EXECUTIONS = new AtomicInteger()

    static triggers = {
        simple startDelay: 0L, repeatInterval: 100L
    }

    void execute() {
        EXECUTIONS.incrementAndGet()
    }
}
