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
package grails.doc.asciidoc

import spock.lang.Specification
import spock.lang.TempDir

class AsciidocConverterSpec extends Specification {

    @TempDir
    File sourceDir

    @TempDir
    File outputDir

    private void write(String path, String content) {
        File file = new File(sourceDir, path)
        file.parentFile.mkdirs()
        file.text = content.stripMargin()
    }

    void 'each document is written as a standalone page at its path relative to the source directory'() {
        given:
        write('index.adoc', '''
            |= Guide
            |
            |Welcome to version {version}.
            |''')
        write('querying/criteria.adoc', '''
            |= Criteria
            |
            |Criteria queries.
            |''')

        when:
        AsciidocConverter.convert(sourceDir, outputDir, ['index.adoc', 'querying/criteria.adoc'], [version: '1.2.3'])

        then: 'the pages keep the layout of the sources'
        File index = new File(outputDir, 'index.html')
        File criteria = new File(outputDir, 'querying/criteria.html')
        index.isFile()
        criteria.isFile()

        and: 'they are complete html documents'
        index.text.contains('<html')
        index.text.contains('<title>Guide</title>')

        and: 'the attributes reach the documents'
        index.text.contains('Welcome to version 1.2.3.')
        criteria.text.contains('Criteria queries.')
    }

    void 'includes resolve against the directory of the document that includes them'() {
        given: 'a document in a subdirectory that includes a sibling by a path relative to itself'
        write('querying/index.adoc', '''
            |= Querying
            |
            |include::_shared.adoc[]
            |''')
        write('querying/_shared.adoc', 'Shared content.')

        when:
        AsciidocConverter.convert(sourceDir, outputDir, ['querying/index.adoc'], [:])

        then:
        new File(outputDir, 'querying/index.html').text.contains('Shared content.')

        and: 'only the requested documents are written'
        !new File(outputDir, 'querying/_shared.html').exists()
    }

    void 'documents may include files from outside the source directory'() {
        given: 'a source file outside the documentation, as the guides include example code'
        File example = new File(outputDir, 'Example.groovy')
        example.text = 'class Example {}'
        write('index.adoc', """
            |= Guide
            |
            |----
            |include::${example.absolutePath}[]
            |----
            |""")

        when:
        AsciidocConverter.convert(sourceDir, outputDir, ['index.adoc'], [:])

        then:
        new File(outputDir, 'index.html').text.contains('class Example {}')
    }

    void 'the warnings asciidoctor reports name the document and the line'() {
        given: 'a document whose section skips a level'
        write('index.adoc', '''
            |= Guide
            |
            |=== Skipped a level
            |
            |Content
            |''')
        PrintStream originalErr = System.err
        ByteArrayOutputStream err = new ByteArrayOutputStream()
        System.err = new PrintStream(err, true, 'UTF-8')

        when:
        try {
            AsciidocConverter.convert(sourceDir, outputDir, ['index.adoc'], [:])
        }
        finally {
            System.err = originalErr
        }

        then:
        String reported = err.toString('UTF-8')
        reported.contains('asciidoctor: WARNING: ')
        reported.contains("${new File(sourceDir, 'index.adoc')}: line 4: section title out of sequence")
    }
}
