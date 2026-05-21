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
import spock.lang.Unroll

class VersionComparatorSpec extends Specification {

    @Unroll
    def "should compare #version1 and #version2 and return #expectedResult"() {
        given:
        def comparator = new VersionComparator();

        when:
        int actualResult = comparator.compare(version1, version2)

        then:
        actualResult == expectedResult

        where:
        version1               | version2               || expectedResult
        "3.1.0"                | "4.0.1"                || -1
        "3.1.10"               | "4.0.1"                || -1
        "3.0.0.BUILD-SNAPSHOT" | "4.0"                  || -1
        "3.1.110"              | "4.0.1"                || -1
        "3.0.0.BUILD-SNAPSHOT" | "3.0.0.BUILD-SNAPSHOT" || 0
        "3.0.0"                | "3.0.0"                || 0
        "4.0.1"                | "3.1.110"              || 1
        "4.0.1"                | "3.0.0.BUILD-SNAPSHOT" || 1
    }
}
