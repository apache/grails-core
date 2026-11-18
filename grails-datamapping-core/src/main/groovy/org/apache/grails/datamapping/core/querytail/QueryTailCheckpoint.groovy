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
package org.apache.grails.datamapping.core.querytail

import java.util.concurrent.atomic.AtomicBoolean

import groovy.transform.CompileStatic
import org.crac.Context
import org.crac.Core
import org.crac.Resource

/**
 * Lets a process that captures statements be checkpointed with CRaC, which refuses to checkpoint a
 * process that holds a file open: before the checkpoint every {@link QueryTailCapture} file is
 * closed, along with its lock file, and after the restore each is opened again, as a capture opening
 * it first would open it.
 *
 * <p>It is registered with the global context of {@code org.crac} as the first capture file opens,
 * when {@code org.crac} is on the classpath, as checkpointing with Spring requires it to be. Spring
 * stops its lifecycle beans before a checkpoint taken while the application runs, but none has
 * started when the checkpoint is taken as the context refreshes, while the capture files open as the
 * datastores are built, so the capture takes part on its own.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class QueryTailCheckpoint implements Resource {

    /** The one registered, held here, as the global context holds what it notifies only weakly. */
    private static final QueryTailCheckpoint REGISTERED = new QueryTailCheckpoint()

    private static final AtomicBoolean REGISTERING = new AtomicBoolean()

    /**
     * Registers with the global context of {@code org.crac}, unless this has been registered already. A JVM that
     * cannot checkpoint takes the registration and never notifies it.
     */
    static void register() {
        if (REGISTERING.compareAndSet(false, true)) {
            Core.globalContext.register(REGISTERED)
        }
    }

    /**
     * Closes every capture file, once the statements being written to it have been, so that the process holds
     * none of them open. A statement run until the restore is not recorded.
     */
    @Override
    void beforeCheckpoint(Context<? extends Resource> context) {
        QueryTailCapture.suspendAll()
    }

    /**
     * Opens every capture file closed for the checkpoint again, as a capture opening it first would: the restored
     * process starts a run of its own, unless another JVM is writing one or the build named the run of the file.
     */
    @Override
    void afterRestore(Context<? extends Resource> context) {
        QueryTailCapture.resumeAll()
    }
}
