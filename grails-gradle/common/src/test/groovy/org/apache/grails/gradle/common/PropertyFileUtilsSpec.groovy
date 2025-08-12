/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.grails.gradle.common

import spock.lang.Specification
import uk.org.webcompere.systemstubs.SystemStubs

import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneOffset

class PropertyFileUtilsSpec extends Specification {

    def 'file - replaces first timestamp comment with default SOURCE_DATE_EPOCH'() {
        given:
        File file = File.createTempFile('props', '.properties')
        file.write([
                '# comment before',
                '#Fri Jan 01 00:00:00 UTC 1970',
                'key1=value1',
                '#Sat Mar 03 15:00:00 UTC 2001'
        ].join(System.lineSeparator()), StandardCharsets.ISO_8859_1.name())

        when:
        PropertyFileUtils.makePropertiesFileReproducible(file)

        then:
        List<String> lines = file.readLines(StandardCharsets.ISO_8859_1.name())
        String expected = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay(ZoneOffset.UTC)
                .toEpochSecond()
                .toString()
        lines[1] == "# SOURCE_DATE_EPOCH = ${expected}"
        // later timestamp is untouched
        lines.contains('#Sat Mar 03 15:00:00 UTC 2001')

        cleanup:
        file?.delete()
    }

    def 'file - preserves content when no timestamp comment is present'() {
        given:
        File file = File.createTempFile('notimestamp', '.properties')
        file.deleteOnExit()
        file.write("fuz=buz${System.lineSeparator()}web=foo" as String, StandardCharsets.ISO_8859_1.name())

        when:
        PropertyFileUtils.makePropertiesFileReproducible(file)

        then:
        file.readLines(StandardCharsets.ISO_8859_1.name()) == ['fuz=buz', 'web=foo']
    }

    def 'file - uses SOURCE_DATE_EPOCH env var when set'() {
        given:
        File file = File.createTempFile('envprops', '.properties')
        file.deleteOnExit()
        file.write("#Mon Apr 04 04:04:04 UTC 2004${System.lineSeparator()}hello=world" as String, StandardCharsets.ISO_8859_1.name())

        when:
        SystemStubs.withEnvironmentVariable('SOURCE_DATE_EPOCH', '42424242').execute {
            PropertyFileUtils.makePropertiesFileReproducible(file)
        }

        then:
        file.readLines(StandardCharsets.ISO_8859_1.name())[0] == '# SOURCE_DATE_EPOCH = 42424242'
    }

    def 'outputstream - replaces first timestamp comment with default SOURCE_DATE_EPOCH'() {
        given:
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        baos.write([
                '# comment before',
                '#Fri Jan 01 00:00:00 UTC 1970',
                'key1=value1',
                '#Sat Mar 03 15:00:00 UTC 2001'
        ].join(System.lineSeparator()).getBytes(StandardCharsets.ISO_8859_1.name()))

        when:
        ByteArrayInputStream result = PropertyFileUtils.makePropertiesOutputReproducible(baos)
        String output = new String(result.readAllBytes(), StandardCharsets.ISO_8859_1.name())
        List<String> lines = output.readLines()

        then:
        String expected = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay(ZoneOffset.UTC)
                .toEpochSecond()
                .toString()
        lines[1] == "# SOURCE_DATE_EPOCH = ${expected}"
        // later timestamp is untouched
        lines.contains('#Sat Mar 03 15:00:00 UTC 2001')
    }

    def 'outputstream - uses SOURCE_DATE_EPOCH env var when set'() {
        given:
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        baos.write([
                '#Tue Apr 02 02:02:02 UTC 2002',
                'testing=another'
        ].join(System.lineSeparator()).getBytes(StandardCharsets.ISO_8859_1.name()))

        when:
        ByteArrayInputStream result = null
        SystemStubs.withEnvironmentVariable('SOURCE_DATE_EPOCH', '42424242').execute {
            result = PropertyFileUtils.makePropertiesOutputReproducible(baos)
        }
        List<String> lines = new String(
                result.readAllBytes(),
                StandardCharsets.ISO_8859_1.name()
        ).readLines()

        then:
        lines[0] == '# SOURCE_DATE_EPOCH = 42424242'
        lines[1] == 'testing=another'
    }

    def 'outputstream - leaves content unchanged when no timestamp comment present'() {
        given:
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        baos.write("alpha=one${System.lineSeparator()}beta=two${System.lineSeparator()}".getBytes(StandardCharsets.ISO_8859_1.name()))

        when:
        ByteArrayInputStream result = PropertyFileUtils.makePropertiesOutputReproducible(baos)
        String output = new String(result.readAllBytes(), StandardCharsets.ISO_8859_1.name())

        then:
        output == "alpha=one${System.lineSeparator()}beta=two${System.lineSeparator()}"
    }
}

