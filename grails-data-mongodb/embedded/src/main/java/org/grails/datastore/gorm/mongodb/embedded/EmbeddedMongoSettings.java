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

import java.util.Objects;

/**
 * What to start a server with. Not every backend honours every setting; one that cannot
 * says so rather than starting a server that quietly behaves differently than asked.
 *
 * @since 8.0
 */
public final class EmbeddedMongoSettings {

    private final int port;

    private final String version;

    private final String databaseDir;

    private final String replicaSet;

    public EmbeddedMongoSettings(int port, String version, String databaseDir) {
        this(port, version, databaseDir, null);
    }

    public EmbeddedMongoSettings(int port, String version, String databaseDir, String replicaSet) {
        this.port = port;
        this.version = unset(version);
        this.databaseDir = unset(databaseDir);
        this.replicaSet = unset(replicaSet);
    }

    /**
     * A setting written as empty is a setting that was not made - {@code database-dir:} with nothing
     * after it is how a property ends up as an empty string rather than absent. They describe the
     * same server, so they are the same settings, which matters where two of them are compared to
     * decide whether the server already running is the one being asked for.
     */
    private static String unset(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /**
     * @return the port to bind
     */
    public int getPort() {
        return this.port;
    }

    /**
     * @return the requested server version, or null for the backend default
     */
    public String getVersion() {
        return this.version;
    }

    /**
     * @return where the data should be kept, or null to discard it when the server stops
     */
    public String getDatabaseDir() {
        return this.databaseDir;
    }

    /**
     * @return the name of the replica set to run as, or null for a standalone server
     */
    public String getReplicaSet() {
        return this.replicaSet;
    }

    /**
     * @return whether the server is meant to be a replica set, which is what a transaction,
     *         a change stream and a causally consistent read all need
     */
    public boolean isReplicaSet() {
        return this.replicaSet != null && !this.replicaSet.isEmpty();
    }

    /**
     * @return whether the data is meant to outlive the server
     */
    public boolean isPersistent() {
        return this.databaseDir != null && !this.databaseDir.isEmpty();
    }

    /**
     * Two settings are the same when they describe the same server, which is how a restarted
     * application decides whether the server already running is the one it asked for.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EmbeddedMongoSettings)) {
            return false;
        }
        EmbeddedMongoSettings that = (EmbeddedMongoSettings) other;
        return this.port == that.port &&
                Objects.equals(this.version, that.version) &&
                Objects.equals(this.databaseDir, that.databaseDir) &&
                Objects.equals(this.replicaSet, that.replicaSet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.port, this.version, this.databaseDir, this.replicaSet);
    }

    @Override
    public String toString() {
        return "port=" + this.port + ", version=" + this.version +
                ", database-dir=" + this.databaseDir + ", replica-set=" + this.replicaSet;
    }
}
