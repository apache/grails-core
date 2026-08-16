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

/**
 * A started embedded MongoDB server.
 *
 * @author Grails
 * @since 8.0
 */
public interface RunningEmbeddedMongo {

    /**
     * @return the host the server is listening on
     */
    String getHost();

    /**
     * @return the port the server is listening on, which is the port that was actually
     * bound rather than the one that was requested
     */
    int getPort();

    /**
     * Stops the server. Called from a JVM shutdown hook, so it must not throw.
     */
    void stop();

    /**
     * Binds the server again on the port it was already using, after {@link #stop()}.
     *
     * <p>This exists for CRaC. A checkpoint refuses to run while the process holds an open
     * socket, and an in-JVM server holds a listening socket, its event loops, and the
     * server side of every connection. Stopping before the checkpoint and starting again
     * after the restore is what lets the process be snapshotted at all.
     *
     * <p>Whether data survives is the backend's business. The in-memory backend keeps its
     * data on the heap, which the checkpoint image preserves, so it rebinds a new server
     * onto the same backend. Flapdoodle keeps whatever is in its {@code database-dir} and
     * loses the rest, since the {@code mongod} process is not part of the image.
     */
    void restart();
}
