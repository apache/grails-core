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
 * What to start a server with. Not every backend honours every setting; one that cannot
 * says so rather than starting a server that quietly behaves differently than asked.
 *
 * @author Grails
 * @since 8.0
 */
public final class EmbeddedMongoSettings {

    private final int port;

    private final String version;

    private final String databaseDir;

    public EmbeddedMongoSettings(int port, String version, String databaseDir) {
        this.port = port;
        this.version = version;
        this.databaseDir = databaseDir;
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
     * @return whether the data is meant to outlive the server
     */
    public boolean isPersistent() {
        return this.databaseDir != null && !this.databaseDir.isEmpty();
    }
}
