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

package org.apache.grails.buildsrc

import spock.lang.Specification

class BomDependencyGraphSpec extends Specification {

    void "isDynamicVersion recognizes #version as #kind"() {
        expect:
        BomDependencyGraph.isDynamicVersion(version) == dynamic

        where:
        version              | dynamic
        '[3,3.1)'            | true
        ']1.0,2.0['          | true
        '[1.0,)'             | true
        '(,2.0]'             | true
        '1.+'                | true
        '+'                  | true
        'latest.release'     | true
        'latest.integration' | true
        '4.0.9'              | false
        '8.0.0-SNAPSHOT'     | false
        '2.4-groovy-5.0'     | false
        '1.0(build)'         | false
        '1,2'                | false
        ''                   | false
        null                 | false
        kind = dynamic ? 'dynamic' : 'fixed'
    }
}
