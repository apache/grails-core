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

import java.nio.charset.StandardCharsets

import spock.lang.Specification
import spock.lang.TempDir
import uk.org.webcompere.systemstubs.SystemStubs

class PropertyFileUtilsSpec extends Specification {

    @TempDir
    File temporaryDirectory

    def 'file - removes #timestamp and preserves other comments'() {
        given:
        File file = new File(temporaryDirectory, 'props.properties')
        file.write([
                '# comment before',
                timestamp,
                'key1=value1',
                '#Sat Mar 03 15:00:00 UTC 2001'
        ].join(System.lineSeparator()), StandardCharsets.ISO_8859_1.name())

        when:
        PropertyFileUtils.makePropertiesFileReproducible(file)

        then:
        List<String> lines = file.readLines(StandardCharsets.ISO_8859_1.name())
        lines == ['# comment before', 'key1=value1', '#Sat Mar 03 15:00:00 UTC 2001']

        where:
        timestamp << ['#Thu Jan 01 00:00:00 UTC 1970', '#Thu, 01 Jan 1970 00:00:00 +0000']
    }

    def 'file - preserves content when no timestamp comment is present'() {
        given:
        File file = new File(temporaryDirectory, 'notimestamp.properties')
        file.write("fuz=buz${System.lineSeparator()}web=foo" as String, StandardCharsets.ISO_8859_1.name())

        when:
        PropertyFileUtils.makePropertiesFileReproducible(file)

        then:
        file.readLines(StandardCharsets.ISO_8859_1.name()) == ['fuz=buz', 'web=foo']
    }

    def 'file - omits timestamp regardless of SOURCE_DATE_EPOCH being #epoch'() {
        given:
        File file = new File(temporaryDirectory, 'envprops.properties')
        file.write("#Mon Apr 04 04:04:04 UTC 2004${System.lineSeparator()}hello=world" as String, StandardCharsets.ISO_8859_1.name())

        when:
        SystemStubs.withEnvironmentVariable('SOURCE_DATE_EPOCH', epoch).execute {
            PropertyFileUtils.makePropertiesFileReproducible(file)
        }

        then:
        file.readLines(StandardCharsets.ISO_8859_1.name()) == ['hello=world']

        where:
        epoch << [null, '', '0', '1', '42424242']
    }

    def 'outputstream - removes #timestamp and preserves other comments'() {
        given:
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        baos.write([
                '# comment before',
                timestamp,
                'key1=value1',
                '#Sat Mar 03 15:00:00 UTC 2001'
        ].join(System.lineSeparator()).getBytes(StandardCharsets.ISO_8859_1.name()))

        when:
        ByteArrayInputStream result = PropertyFileUtils.makePropertiesOutputReproducible(baos)
        String output = new String(result.readAllBytes(), StandardCharsets.ISO_8859_1.name())
        List<String> lines = output.readLines()

        then:
        lines == ['# comment before', 'key1=value1', '#Sat Mar 03 15:00:00 UTC 2001']

        where:
        timestamp << ['#Thu Jan 01 00:00:00 UTC 1970', '#Thu, 01 Jan 1970 00:00:00 +0000']
    }

    def 'outputstream - omits timestamp regardless of SOURCE_DATE_EPOCH being #epoch'() {
        given:
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        baos.write([
                '#Tue Apr 02 02:02:02 UTC 2002',
                'testing=another'
        ].join(System.lineSeparator()).getBytes(StandardCharsets.ISO_8859_1.name()))

        when:
        ByteArrayInputStream result = null
        SystemStubs.withEnvironmentVariable('SOURCE_DATE_EPOCH', epoch).execute {
            result = PropertyFileUtils.makePropertiesOutputReproducible(baos)
        }
        List<String> lines = new String(
                result.readAllBytes(),
                StandardCharsets.ISO_8859_1.name()
        ).readLines()

        then:
        lines == ['testing=another']

        where:
        epoch << [null, '', '0', '1', '42424242']
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

    def 'stored properties retain values and descriptive comments through #target'() {
        given:
        Properties original = new Properties()
        original.setProperty('#special:key=', ' leading space\tline one\nline two\\end')
        original.setProperty('unicode', 'caf\u00e9 \u2603')
        original.setProperty('date', 'Fri Jan 01 00:00:00 UTC 1970')
        ByteArrayOutputStream stored = new ByteArrayOutputStream()
        original.store(stored, 'Generated properties\nDescriptive comment')
        File file = new File(temporaryDirectory, 'stored.properties')
        file.bytes = stored.toByteArray()

        when:
        byte[] output
        if (target == 'file') {
            PropertyFileUtils.makePropertiesFileReproducible(file)
            output = file.bytes
        }
        else {
            output = PropertyFileUtils.makePropertiesOutputReproducible(stored).readAllBytes()
        }
        Properties loaded = new Properties()
        loaded.load(new ByteArrayInputStream(output))

        then:
        loaded == original
        new String(output, StandardCharsets.ISO_8859_1).readLines().findAll { it.startsWith('#') } == [
                '#Generated properties', '#Descriptive comment'
        ]

        where:
        target << ['file', 'outputstream']
    }
}
