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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import de.flapdoodle.embed.mongo.commands.ImmutableMongodArguments;
import de.flapdoodle.embed.mongo.commands.MongodArguments;
import de.flapdoodle.embed.mongo.commands.ServerAddress;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.config.Storage;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.ImmutableMongod;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.embed.mongo.types.DatabaseDir;
import de.flapdoodle.reverse.TransitionWalker;
import de.flapdoodle.reverse.transitions.Start;

import org.springframework.util.ClassUtils;

/**
 * Runs a real mongod as a child process using Flapdoodle Embedded MongoDB, which
 * downloads a genuine MongoDB binary on first use and caches it under
 * {@code ~/.embedmongo}. Because it is actual MongoDB, transactions, change streams and
 * {@code $text} all behave as they do in production, and the data can be kept between
 * runs.
 *
 * <p>Flapdoodle is not a dependency of this module: it pulls in jgrapht, which is offered
 * under LGPL-2.1 or EPL-2.0, and an Apache release should not require it. An application
 * that wants a real mongod adds {@code de.flapdoodle.embed:de.flapdoodle.embed.mongo}
 * itself, the same way an application picks its own SQL database driver. Every reference
 * to flapdoodle is therefore confined to methods that {@link #isAvailable()} guards.
 *
 * @since 8.0
 */
public class FlapdoodleMongoBackend implements EmbeddedMongoBackend {

    public static final String NAME = "flapdoodle";

    /**
     * Package-private, and a compile-time constant, so that {@link EmbeddedMongoInitializer} can ask
     * whether flapdoodle is present without naming this class in a way that would load it.
     */
    static final String MONGOD_CLASS = "de.flapdoodle.embed.mongo.transitions.Mongod";

    private static final String DEFAULT_VERSION = "V8_0";

    /** What runs {@code replSetInitiate}, which only a driver can send. */
    private static final String MONGO_CLIENTS_CLASS = "com.mongodb.client.MongoClients";

    /** Small, because a replica set of one keeps an oplog it never ships anywhere. */
    private static final int OPLOG_SIZE_MB = 64;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean isAvailable() {
        return ClassUtils.isPresent(MONGOD_CLASS, getClass().getClassLoader());
    }

    @Override
    public RunningEmbeddedMongo start(EmbeddedMongoSettings settings) {
        String versionName = settings.getVersion() != null ? settings.getVersion() : DEFAULT_VERSION;

        Version.Main version;
        try {
            version = Version.Main.valueOf(versionName);
        }
        catch (IllegalArgumentException ex) {
            throw new IllegalStateException(EmbeddedMongoInitializer.VERSION + "=" + versionName +
                    " is not a Version.Main constant. Use a name such as V8_0 or V7_0.", ex);
        }

        ImmutableMongod mongod = Mongod.instance()
                .withNet(Start.to(Net.class).initializedWith(Net.of("localhost", settings.getPort(), false)));

        ImmutableMongodArguments arguments = MongodArguments.defaults();
        if (settings.isPersistent()) {
            mongod = keepDataIn(mongod, settings.getDatabaseDir());
            // Flapdoodle's defaults turn syncing to disc off, which anything meant to outlive the
            // process needs back on.
            arguments = arguments.withUseDefaultSyncDelay(true);
        }
        if (settings.isReplicaSet()) {
            requireDriver(settings.getReplicaSet());
            arguments = arguments.withReplication(Storage.of(settings.getReplicaSet(), OPLOG_SIZE_MB));
        }
        mongod = mongod.withMongodArguments(Start.to(MongodArguments.class).initializedWith(arguments));

        return new RunningMongod(this, mongod, version, settings.getReplicaSet());
    }

    /**
     * Sends the command that turns a mongod started with a replica set name into one that answers.
     *
     * <p>Not private, so that the specification can stand in a failure here and see that the server
     * this started does not outlive it.
     */
    void initiateReplicaSet(String host, int port, String replicaSet) {
        ReplicaSetInitiator.initiate(host, port, replicaSet);
    }

    /**
     * A replica set is initiated by a driver rather than by a command-line argument, and this module
     * does not carry one: an application that talks to MongoDB brought its own.
     */
    private void requireDriver(String replicaSet) {
        if (!ClassUtils.isPresent(MONGO_CLIENTS_CLASS, getClass().getClassLoader())) {
            throw new IllegalStateException(EmbeddedMongoInitializer.REPLICA_SET + "=" + replicaSet +
                    " needs the MongoDB driver on the class path to initiate the replica set. Add " +
                    "org.mongodb:mongodb-driver-sync, or leave the server standalone.");
        }
    }

    /**
     * Flapdoodle otherwise stores the database under a temp path it deletes on shutdown, so this
     * has to change before anything written here outlives the process.
     */
    private ImmutableMongod keepDataIn(ImmutableMongod mongod, String databaseDir) {
        Path path;
        try {
            path = Files.createDirectories(Paths.get(databaseDir).toAbsolutePath());
        }
        catch (IOException ex) {
            throw new IllegalStateException("Could not create the embedded MongoDB directory " + databaseDir, ex);
        }
        return mongod.withDatabaseDir(Start.to(DatabaseDir.class).initializedWith(DatabaseDir.of(path)));
    }

    private static final class RunningMongod implements RunningEmbeddedMongo {

        /** Held so {@link #restart()} can start the same mongod again after a CRaC restore. */
        private final ImmutableMongod mongod;

        private final FlapdoodleMongoBackend backend;

        private final Version.Main version;

        private final String replicaSet;

        private volatile TransitionWalker.ReachedState<RunningMongodProcess> running;

        private final ServerAddress address;

        private RunningMongod(FlapdoodleMongoBackend backend, ImmutableMongod mongod,
                Version.Main version, String replicaSet) {
            this.backend = backend;
            this.mongod = mongod;
            this.version = version;
            this.replicaSet = replicaSet;
            this.running = mongod.start(version);
            this.address = this.running.current().getServerAddress();
            try {
                initiateReplicaSet();
            }
            catch (RuntimeException | Error setDidNotForm) {
                // The process is running and this object is about to be thrown away, so nothing
                // would ever stop it: mongod is a child process that outlives the JVM that started
                // it, and it holds both the port and the database directory. The next start would
                // find them taken by a server no one can name.
                try {
                    stop();
                }
                catch (RuntimeException | Error alsoFailedToStop) {
                    // The reason the caller asked is the set that never formed; how the cleanup
                    // then went is worth carrying, but not worth reporting in its place.
                    setDidNotForm.addSuppressed(alsoFailedToStop);
                }
                throw setDidNotForm;
            }
        }

        /**
         * A mongod started with a replica set name refuses every write until the set is initiated,
         * and answers reads and writes only once it has elected itself, so the server is not handed
         * back before it has. A set that is already initiated - which is what a persistent database
         * directory carries across a restart - says so, and that is an answer rather than a failure.
         */
        private void initiateReplicaSet() {
            if (this.replicaSet == null || this.replicaSet.isEmpty()) {
                return;
            }
            this.backend.initiateReplicaSet(getHost(), getPort(), this.replicaSet);
        }

        @Override
        public String getHost() {
            return this.address.getHost();
        }

        @Override
        public int getPort() {
            return this.address.getPort();
        }

        /**
         * Stopping twice is what an ordinary shutdown does: the lifecycle bean stops the server
         * when the application context closes, and the JVM shutdown hook - which is there for a
         * context that never closes - runs after it. Flapdoodle deletes the process directory as
         * it tears the server down, so a second teardown fails on the files the first one removed.
         */
        @Override
        public synchronized void stop() {
            TransitionWalker.ReachedState<RunningMongodProcess> current = this.running;
            if (current == null) {
                return;
            }
            try {
                current.close();
            }
            finally {
                // Cleared only once the server is actually down. Clearing first meant a close that
                // threw left a running mongod that this object reported as stopped, which is a
                // process nothing would stop and a port a restart would try to bind again.
                this.running = null;
            }
        }

        @Override
        public boolean isRunning() {
            return this.running != null;
        }

        /**
         * The replacement binds the same port because {@code Net} was fixed when the server
         * was first configured. Only a persistent {@code database-dir} carries data across;
         * mongod is a separate process, so a checkpoint image does not contain it.
         *
         * <p>A server that is somehow still up is torn down first rather than left: the
         * replacement binds the port the old process is holding, so starting one beside the
         * other only fails on the port. Ordinarily there is nothing to tear down, because the
         * lifecycle bean stops the server before the checkpoint that this restores from.
         */
        @Override
        public synchronized void restart() {
            stop();
            this.running = this.mongod.start(this.version);
            try {
                initiateReplicaSet();
            }
            catch (RuntimeException | Error setDidNotForm) {
                // As in the constructor: a process that is running and cannot be used is worse than
                // no process, and this one would hold the port against every later attempt.
                try {
                    stop();
                }
                catch (RuntimeException | Error alsoFailedToStop) {
                    setDidNotForm.addSuppressed(alsoFailedToStop);
                }
                throw setDidNotForm;
            }
        }
    }
}
