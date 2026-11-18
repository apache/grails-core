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

import de.flapdoodle.embed.mongo.distribution.Version
import spock.lang.Specification
import spock.lang.Unroll

class EmbeddedMongoVersionsSpec extends Specification {

    PrintStream standardError
    ByteArrayOutputStream writtenToStandardError = new ByteArrayOutputStream()

    void setup() {
        standardError = System.err
        System.setErr(new PrintStream(writtenToStandardError, true, 'UTF-8'))
    }

    void cleanup() {
        System.setErr(standardError)
    }

    @Unroll
    void 'the container tag #tag names the line #line, which flapdoodle has, and nothing is announced'() {
        expect: 'the line the tag names'
        EmbeddedMongoVersions.lineFor(tag) == line

        and: 'nothing on standard error'
        writtenToStandardError.size() == 0

        where:
        tag         || line
        '7.0.19'    || 'V7_0'
        '7.0'       || 'V7_0'
        '8'         || 'V8_0'
        '8.0'       || 'V8_0'
        '8.0.0-rc9' || 'V8_0'
        '8.2'       || 'V8_2'
        '4.4.1'     || 'V4_4'
    }

    @Unroll
    void 'the tag #tag names a line flapdoodle does not have, so the newest line below it, #line, is used and announced'() {
        expect: 'the newest line below the tag'
        EmbeddedMongoVersions.lineFor(tag) == line

        and: 'one line on standard error naming the version asked for and the line that runs instead'
        with(writtenToStandardError.toString('UTF-8')) {
            startsWith(EmbeddedMongoVersions.NOTICE)
            contains(" MongoDB $tag line ")
            endsWith(" runs $line${System.lineSeparator()}")
            readLines().size() == 1
        }

        where:
        tag   || line
        '3.7' || 'V3_6'
        '4.1' || 'V4_0'
        '4.3' || 'V4_2'
        '8.9' || 'V8_3'
    }

    void 'a MongoDB 9.0 build is served from a line flapdoodle has that is no newer than 9.0'() {
        when: 'the line for 9.0 is asked for'
        String line = EmbeddedMongoVersions.lineFor('9.0')
        List<Integer> served = (line - 'V').tokenize('_')*.toInteger()

        then: 'it is a line flapdoodle has'
        Version.Main.valueOf(line)

        and: 'it is 9.0 itself or an older line'
        served[0] < 9 || (served[0] == 9 && served[1] == 0)

        and: 'standard error names the older line exactly when one stands in'
        (line == 'V9_0') == (writtenToStandardError.size() == 0)
        line == 'V9_0' || writtenToStandardError.toString('UTF-8').contains("MongoDB 9.0 line yet, so the embedded server runs $line")
    }

    void 'a version below every line flapdoodle has is refused, naming the lines it does have'() {
        when: 'a version older than the oldest line is asked for'
        EmbeddedMongoVersions.lineFor('0.9')

        then: 'it is refused with the version and the lines there are'
        IllegalStateException refused = thrown()
        refused.message.contains('0.9')
        refused.message.contains('V1_8')
    }

    @Unroll
    void "'#tag' is not a MongoDB version and is refused"() {
        when: 'something other than a version is asked for'
        EmbeddedMongoVersions.lineFor(tag)

        then: 'it is refused, naming what was asked for'
        IllegalArgumentException refused = thrown()
        refused.message.contains(String.valueOf(tag))

        where:
        tag << ['latest', 'V8_0', '8.x', '', null]
    }

}
