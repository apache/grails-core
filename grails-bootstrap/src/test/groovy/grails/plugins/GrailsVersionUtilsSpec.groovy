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
package grails.plugins

import spock.lang.Specification

class GrailsVersionUtilsSpec extends Specification {

    void 'plugin version #pluginVersion satisfies the required version #requiredVersion: #valid'() {
        expect:
        GrailsVersionUtils.isValidVersion(pluginVersion, requiredVersion) == valid

        where:
        pluginVersion    | requiredVersion || valid
        '9.0.0-SNAPSHOT' | '*'             || true
        '1.0'            | ' * '           || true
        null             | '1.0'           || false
        null             | '1.0 > *'       || false
        null             | '*'             || true
        null             | ' * '           || true
        '1.1'            | '1.1'           || true
        '1.1'            | '1.2'           || false
        '1.1'            | '1.0 > *'       || true
        '0.9'            | '1.0 > *'       || false
        '9.0.0-SNAPSHOT' | '9.0.0 > *'     || true
    }
}
