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

import de.flapdoodle.embed.mongo.commands.MongodArguments;
import de.flapdoodle.embed.mongo.commands.ServerAddress;
import de.flapdoodle.embed.mongo.config.Net;
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
 * @author Grails
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
        if (settings.isPersistent()) {
            mongod = persistentIn(mongod, settings.getDatabaseDir());
        }
        return new RunningMongod(mongod, version);
    }

    /**
     * Flapdoodle otherwise stores the database under a temp path it deletes on shutdown,
     * and its default arguments turn off syncing to disc, so both have to change before
     * anything written here outlives the process.
     */
    private ImmutableMongod persistentIn(ImmutableMongod mongod, String databaseDir) {
        Path path;
        try {
            path = Files.createDirectories(Paths.get(databaseDir).toAbsolutePath());
        }
        catch (IOException ex) {
            throw new IllegalStateException("Could not create the embedded MongoDB directory " + databaseDir, ex);
        }
        return mongod
                .withDatabaseDir(Start.to(DatabaseDir.class).initializedWith(DatabaseDir.of(path)))
                .withMongodArguments(Start.to(MongodArguments.class)
                        .initializedWith(MongodArguments.defaults().withUseDefaultSyncDelay(true)));
    }

    private static final class RunningMongod implements RunningEmbeddedMongo {

        /** Held so {@link #restart()} can start the same mongod again after a CRaC restore. */
        private final ImmutableMongod mongod;

        private final Version.Main version;

        private volatile TransitionWalker.ReachedState<RunningMongodProcess> running;

        private final ServerAddress address;

        private RunningMongod(ImmutableMongod mongod, Version.Main version) {
            this.mongod = mongod;
            this.version = version;
            this.running = mongod.start(version);
            this.address = this.running.current().getServerAddress();
        }

        @Override
        public String getHost() {
            return this.address.getHost();
        }

        @Override
        public int getPort() {
            return this.address.getPort();
        }

        @Override
        public void stop() {
            this.running.close();
        }

        /**
         * The replacement binds the same port because {@code Net} was fixed when the server
         * was first configured. Only a persistent {@code database-dir} carries data across;
         * mongod is a separate process, so a checkpoint image does not contain it.
         */
        @Override
        public void restart() {
            this.running = this.mongod.start(this.version);
        }
    }
}
