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

package org.apache.grails.testing.mongo

import org.testcontainers.containers.MongoDBContainer
import spock.lang.Shared
import spock.lang.Specification

/**
 * A base specification class to handling autostarting MongoDB & bootstrapping the mongo datastore if defined.
 * This class may only be used with unit tests.
 *
 * @since 7.0.0
 * @author James Daugherty
 */

abstract class AutoStartedMongoSpec extends Specification {

    @Shared
    String mongoHost

    @Shared
    String mongoPort

    @Shared
    MongoDBContainer dbContainer

    /**
     * If MongoDatastore is defined, it will be initialized with the dbContainer
     */
    boolean shouldInitializeDatastore() {
        return true
    }

    /**
     * The packages to initialize the MongoDatastore (if defined)
     */
    List<Package> getMongoPackages() {
        [getClass().getPackage()]
    }

    // Optionally declare a variable with type `MongoDatastore` and it will be initialized using the dbContainer
    // the the dBContainer connection for domains in the same package as this specification
}
