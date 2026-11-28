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
package org.apache.grails.forge.core.pack

import spock.lang.Specification
import spock.lang.Unroll

class PackVersionRangeSpec extends Specification {

    @Unroll
    void "#version is #verdict the range #range"() {
        expect:
        PackVersionRange.parse(range).contains(version) == within

        where:
        range                             | version           || within
        '[9.0.0,10.0.0)'                  | '9.0.0'           || true
        '[9.0.0,10.0.0)'                  | '9.4.12'          || true
        '[9.0.0,10.0.0)'                  | '10.0.0'          || false
        '[9.0.0,10.0.0)'                  | '8.9.9'           || false
        '[9.0.0,10.0.0)'                  | '9.0.0-SNAPSHOT'  || true
        '[9.0.0,10.0.0)'                  | '9.0.0-M2'        || true
        '[9.0.0,10.0.0)'                  | '9.0.0-RC1'       || true
        '[9.0.0,10.0.0)'                  | '10.0.0-SNAPSHOT' || false
        '[9.0.0,10.0.0)'                  | '9.10.0'          || true
        '(9.0.0,10.0.0]'                  | '9.0.0'           || false
        '(9.0.0,10.0.0]'                  | '10.0.0'          || true
        '[9.1,)'                          | '9.0.9'           || false
        '[9.1,)'                          | '9.1.0'           || true
        '[9.1,)'                          | '12.0.0'          || true
        '(,10)'                           | '9.9.9'           || true
        '(,10)'                           | '10.0.0'          || false
        '9.0.0'                           | '9.0.0'           || true
        '9.0.0'                           | '9.0.0-SNAPSHOT'  || true
        '9.0.0'                           | '9.0.1'           || false
        '9'                               | '9.0.0'           || true
        '[9.0.0]'                         | '9.0.0'           || true
        '[9.0.0]'                         | '9.0.1'           || false
        '[9.0.0,9.1.0),[9.2.0,10.0.0)'    | '9.0.5'           || true
        '[9.0.0,9.1.0),[9.2.0,10.0.0)'    | '9.1.5'           || false
        '[9.0.0,9.1.0) , [9.2.0,10.0.0)'  | '9.2.0'           || true
        ' [ 9.0.0 , 10.0.0 ) '            | '9.3.0'           || true
        '[9.0.0,10.0.0)'                  | '9.0.0.1'         || true
        '9.0.0'                           | '9.0.1a'          || false
        '9.0.0'                           | '9.0.0a'          || false
        '9.0.0'                           | '9.0.0_1'         || false
        '9.0.0'                           | '9.0.12_x'        || false
        '9.0.0'                           | '9.1x'            || false
        '9.0.0'                           | '9.0.0 beta'      || false
        '9.0.0'                           | '9.0.0.0.1'       || false
        '9.0.0'                           | '9.0.0.RELEASE'   || true
        '9.0.0'                           | '9.0.0+build.5'   || true
        '(9.0.0,)'                        | '9.0.1-RC1'       || true
        '[9.0.0,10.0.0)'                  | 'unknown'         || false
        '[9.0.0,10.0.0)'                  | ''                || false
        '[9.0.0,10.0.0)'                  | null              || false
        '[9.0.0,10.0.0)'                  | '9'               || true
        verdict = within ? 'in' : 'not in'
    }

    @Unroll
    void "#range is not a range: it #problem"() {
        when:
        PackVersionRange.parse(range)

        then:
        IllegalArgumentException e = thrown()
        e.message == problem

        where:
        range                    || problem
        '9.0.0-SNAPSHOT'         || 'is not one release, such as 9.0.0, or intervals of releases, such as [9.0.0,10.0.0)'
        '[9.0.0-RC1,10.0.0)'     || 'is not one release, such as 9.0.0, or intervals of releases, such as [9.0.0,10.0.0)'
        '9.+'                    || 'is not one release, such as 9.0.0, or intervals of releases, such as [9.0.0,10.0.0)'
        '[9.0.0,10.0.0'          || 'is not one release, such as 9.0.0, or intervals of releases, such as [9.0.0,10.0.0)'
        '1.2.3.4.5'              || 'is not one release, such as 9.0.0, or intervals of releases, such as [9.0.0,10.0.0)'
        '[10.0.0,9.0.0)'         || 'has the interval [10.0.0,9.0.0), which no version is in'
        '[9.0.0,9.0.0)'          || 'has the interval [9.0.0,9.0.0), which no version is in'
        '(,)'                    || 'has an interval with no version at either end'
        '[,10.0.0)'              || 'has an end without a version that is closed with a square bracket; use a round one'
        '[9.0.0,]'               || 'has an end without a version that is closed with a square bracket; use a round one'
        '[9.0.0,9.1.0)[9.2.0,)'  || 'has intervals that are not separated by one comma each'
        '[9.0.0,9.1.0),'         || 'has intervals that are not separated by one comma each'
        ''                       || 'is empty'
        (['[1,2)'] * 21).join(',') || 'has more than 20 intervals'
    }
}
