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

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;

/**
 * Turns a mongod started with a replica set name into one that answers.
 *
 * <p>A replica set of one is what a single machine can offer, and it is enough for everything a
 * standalone server refuses: a multi-document transaction, a change stream, a causally consistent
 * read. MongoDB does not configure it from the command line - the set is initiated by a command
 * sent to the server it names - so this is the step between starting the process and handing the
 * server to an application.
 *
 * <p>Every reference to the MongoDB driver lives here, so a standalone embedded server needs
 * nothing of it on the class path.
 *
 * @since 8.0
 */
final class ReplicaSetInitiator {

    /** {@code AlreadyInitialized}: the set this is being asked to create is the set it found. */
    private static final int ALREADY_INITIALIZED = 23;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final long POLL_MILLIS = 100;

    private ReplicaSetInitiator() {
    }

    static void initiate(String host, int port, String replicaSet) {
        String address = host + ":" + port;
        // Direct, because the set does not exist yet: a driver discovering a replica set would have
        // nothing to discover and would wait for a primary that cannot be elected until this runs.
        try (MongoClient client = MongoClients.create("mongodb://" + address + "/?directConnection=true")) {
            initiate(client, address, replicaSet);
            awaitPrimary(client, address, replicaSet);
        }
    }

    private static void initiate(MongoClient client, String address, String replicaSet) {
        List<Document> members = Collections.singletonList(new Document("_id", 0).append("host", address));
        Document configuration = new Document("_id", replicaSet).append("members", members);
        try {
            client.getDatabase("admin").runCommand(new Document("replSetInitiate", configuration));
        }
        catch (MongoCommandException ex) {
            if (ex.getErrorCode() != ALREADY_INITIALIZED) {
                throw new IllegalStateException("Could not initiate the replica set " + replicaSet +
                        " on the embedded MongoDB at " + address, ex);
            }
        }
    }

    /**
     * A node told to form a set is not writable until it has elected itself, which takes about a
     * second. Handing the server over before then leaves the first thing an application does
     * failing against a server that was about to be ready.
     */
    private static void awaitPrimary(MongoClient client, String address, String replicaSet) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (isPrimary(client)) {
                return;
            }
            try {
                Thread.sleep(POLL_MILLIS);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for the embedded MongoDB at " +
                        address + " to elect itself primary of " + replicaSet, ex);
            }
        }
        throw new IllegalStateException("The embedded MongoDB at " + address + " did not become primary of " +
                replicaSet + " within " + TIMEOUT.toSeconds() + " seconds.");
    }

    private static boolean isPrimary(MongoClient client) {
        try {
            Document hello = client.getDatabase("admin").runCommand(new Document("hello", 1));
            return Boolean.TRUE.equals(hello.getBoolean("isWritablePrimary"));
        }
        catch (RuntimeException ex) {
            // The server closes connections as it transitions, so a failure here means "not yet".
            return false;
        }
    }

}
