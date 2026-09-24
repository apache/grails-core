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
package org.grails.datastore.mapping.mongo.connections

import com.mongodb.client.MongoClient
import spock.lang.Specification

class MongoConnectionSourceSpec extends Specification {

    void 'it hands out the client it was built with, and owns it'() {
        given:
        MongoClient client = Mock(MongoClient)

        when:
        def source = new MongoConnectionSource('reporting', client, new MongoConnectionSourceSettings())

        then:
        source.name == 'reporting'
        source.source.is(client)
        source.closeable
    }

    void 'a replacement is handed out from then on, and the client it replaces is left for the caller'() {
        given:
        MongoClient original = Mock(MongoClient)
        MongoClient replacement = Mock(MongoClient)
        def source = new MongoConnectionSource('reporting', original, new MongoConnectionSourceSettings())

        when:
        source.replaceSource(replacement)

        then:
        source.source.is(replacement)
        0 * original.close()
    }

    void 'closing it closes the client in use, not the one it replaced'() {
        given:
        MongoClient original = Mock(MongoClient)
        MongoClient replacement = Mock(MongoClient)
        def source = new MongoConnectionSource('reporting', original, new MongoConnectionSourceSettings())
        source.replaceSource(replacement)

        when:
        source.close()

        then:
        1 * replacement.close()
        0 * original.close()
    }

    void 'one built around a client the application supplied reports that it does not own it'() {
        given:
        MongoClient client = Mock(MongoClient)

        when:
        def source = new MongoConnectionSource('reporting', client, new MongoConnectionSourceSettings(), false)

        then:
        source.source.is(client)
        !source.closeable
    }

    void 'closing one that does not own its client leaves the client open'() {
        given:
        MongoClient client = Mock(MongoClient)
        def source = new MongoConnectionSource('reporting', client, new MongoConnectionSourceSettings(), false)

        when:
        source.close()

        then:
        0 * client.close()
    }

    void 'a replacement is handed out by one that does not own its client, and is not closed either'() {
        given:
        MongoClient original = Mock(MongoClient)
        MongoClient replacement = Mock(MongoClient)
        def source = new MongoConnectionSource('reporting', original, new MongoConnectionSourceSettings(), false)
        source.replaceSource(replacement)

        when:
        source.close()

        then:
        source.source.is(replacement)
        0 * replacement.close()
        0 * original.close()
    }

    void 'a missing replacement is refused'() {
        given:
        MongoClient client = Mock(MongoClient)
        def source = new MongoConnectionSource('reporting', client, new MongoConnectionSourceSettings())

        when:
        source.replaceSource(null)

        then:
        thrown(IllegalArgumentException)
        source.source.is(client)
    }
}
