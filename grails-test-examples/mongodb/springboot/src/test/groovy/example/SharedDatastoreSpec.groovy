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
package example

import spock.lang.Shared
import spock.lang.Specification

import org.grails.datastore.mapping.mongo.MongoDatastore

/**
 * {@link SpringBootStartMongoExtension} gives a spec's shared {@link MongoDatastore} field its datastore before the
 * spec's own shared initializers run, so they can use it, and before {@code setupSpec}.
 */
class SharedDatastoreSpec extends Specification {

    @Shared
    MongoDatastore datastore

    @Shared
    BookService fromInitializer = datastore.getService(BookService)

    @Shared
    List<String> initializedToo = ['own initializer']

    @Shared
    BookService fromSetupSpec

    void setupSpec() {
        fromSetupSpec = datastore.getService(BookService)
    }

    void 'a shared initializer can use the datastore'() {
        expect:
        fromInitializer != null
        fromInitializer.is(datastore.getService(BookService))
    }

    void 'setupSpec can use the datastore'() {
        expect:
        fromSetupSpec.is(datastore.getService(BookService))
    }

    void 'the spec\'s own shared initializers still run'() {
        expect:
        initializedToo == ['own initializer']
    }
}
