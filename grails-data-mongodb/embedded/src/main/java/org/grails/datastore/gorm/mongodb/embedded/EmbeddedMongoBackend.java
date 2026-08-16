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
 * A MongoDB server that can be started inside, or alongside, the application process.
 *
 * <p>Everything else about running an embedded server is the same whichever one is used:
 * choosing a port, reusing a server that is already listening, publishing the URL and
 * stopping the server when the JVM exits. Only starting it differs, which is all this
 * interface covers.
 *
 * @author Grails
 * @since 8.0
 * @see InMemoryMongoBackend
 * @see FlapdoodleMongoBackend
 */
public interface EmbeddedMongoBackend {

    /**
     * The name this backend is selected by, through {@code embedded.mongodb.backend}.
     *
     * @return the backend name
     */
    String getName();

    /**
     * Whether the library this backend needs is on the classpath. Only one backend is a
     * dependency of this module; the other is added by applications that want it, so this
     * is what makes selection possible without either being mandatory.
     *
     * @return true when this backend can be started
     */
    boolean isAvailable();

    /**
     * Starts a server bound to localhost.
     *
     * @param settings the port to bind and any backend specific options
     * @return the running server, for the caller to stop
     */
    RunningEmbeddedMongo start(EmbeddedMongoSettings settings);
}
