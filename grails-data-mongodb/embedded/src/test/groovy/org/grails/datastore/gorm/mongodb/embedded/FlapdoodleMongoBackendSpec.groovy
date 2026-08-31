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

/**
 * Only covers behaviour that does not start a real mongod: FlapdoodleMongoBackend.start()
 * downloads and launches an actual MongoDB binary as a child process, which is functional/
 * integration territory (already exercised by EmbeddedMongoLifecycleSpec/
 * EmbeddedMongoInitializerSpec), not something a unit spec should trigger.
 */
class FlapdoodleMongoBackendSpec extends Specification {

    FlapdoodleMongoBackend backend = new FlapdoodleMongoBackend()

    void 'the backend is named flapdoodle'() {
        expect:
        backend.name == FlapdoodleMongoBackend.NAME
        backend.name == 'flapdoodle'
    }

    void 'the backend is available, since flapdoodle is a test dependency of this module'() {
        expect:
        backend.isAvailable()
    }

    void 'starting with an unrecognised version fails before any mongod is started'() {
        given: 'a version string that is not a Version.Main constant'
        EmbeddedMongoSettings settings = new EmbeddedMongoSettings(0, 'not-a-real-version', null)

        when:
        backend.start(settings)

        then:
        IllegalStateException ex = thrown(IllegalStateException)
        ex.message == "${EmbeddedMongoInitializer.VERSION}=not-a-real-version is not a Version.Main constant. " +
                'Use a name such as V8_0 or V7_0.'
    }
}
