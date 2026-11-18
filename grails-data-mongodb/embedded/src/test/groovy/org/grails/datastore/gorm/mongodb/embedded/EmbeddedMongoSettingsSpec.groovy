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
package org.grails.datastore.gorm.mongodb.embedded

import spock.lang.Specification
import spock.lang.Unroll

class EmbeddedMongoSettingsSpec extends Specification {

    void 'what a backend is asked for is what it was given'() {
        when:
        EmbeddedMongoSettings settings = new EmbeddedMongoSettings(27017, 'V8_0', '/var/data/mongo')

        then:
        settings.port == 27017
        settings.version == 'V8_0'
        settings.databaseDir == '/var/data/mongo'
    }

    void 'a setting written as empty describes the same server as one not written'() {
        expect: 'so that a reload does not replace a server over a key with nothing after it'
        new EmbeddedMongoSettings(27017, '', '', '') ==
                new EmbeddedMongoSettings(27017, null, null, null)
        new EmbeddedMongoSettings(27017, '', '', '').hashCode() ==
                new EmbeddedMongoSettings(27017, null, null, null).hashCode()
    }

    void 'settings that describe different servers are different'() {
        expect:
        new EmbeddedMongoSettings(27017, 'V8_0', null, null) !=
                new EmbeddedMongoSettings(27017, 'V7_0', null, null)
        new EmbeddedMongoSettings(27017, null, null, 'rs0') !=
                new EmbeddedMongoSettings(27017, null, null, null)
        new EmbeddedMongoSettings(27017, null, null, null) !=
                new EmbeddedMongoSettings(27018, null, null, null)
    }

    void 'a version the application did not choose is left to the backend'() {
        expect: 'null rather than a default here, so each backend picks one it can actually run'
        new EmbeddedMongoSettings(27017, null, null).version == null
    }

    @Unroll
    void 'a database directory of #databaseDir means persistent = #persistent'() {
        expect: 'an empty directory is no directory, so a backend that cannot persist does not refuse'
        new EmbeddedMongoSettings(27017, null, databaseDir).persistent == persistent

        where:
        databaseDir || persistent
        null        || false
        ''          || false
        './prodDb'  || true
    }
}
