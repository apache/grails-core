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
package org.grails.build.parsing

import spock.lang.Specification

class OptionSpec extends Specification {

    void 'stores the given name and description'() {
        when:
        Option option = new Option('offline', 'Run offline')

        then:
        option.name == 'offline'
        option.description == 'Run offline'
    }

    void 'defaults a null description to an empty string'() {
        when:
        Option option = new Option('offline', null)

        then:
        option.description == ''
    }

    void 'rejects a null name'() {
        when:
        new Option(null, 'desc')

        then:
        thrown(IllegalArgumentException)
    }

    void 'rejects an empty name'() {
        when:
        new Option('', 'desc')

        then:
        thrown(IllegalArgumentException)
    }

}
