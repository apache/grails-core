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

import groovy.transform.CompileStatic

import org.asciidoctor.Asciidoctor
import org.asciidoctor.Attributes
import org.asciidoctor.Options
import org.asciidoctor.SafeMode
import org.asciidoctor.ast.Cursor
import org.asciidoctor.log.LogHandler
import org.asciidoctor.log.LogRecord
import org.asciidoctor.log.Severity

/**
 * Converts AsciiDoc documents to standalone HTML pages, each with its own directory as the
 * base directory, written under the output directory at the path of the document relative to
 * the source directory. The warnings and errors Asciidoctor reports are written to the standard
 * error stream, as the Asciidoctor command line does.
 *
 * @since 8.0
 */
@CompileStatic
class AsciidocConverter {

    /**
     * @param sourceDir the directory the documents are read from
     * @param outputDir the directory the pages are written to
     * @param documents the documents to convert, as paths relative to {@code sourceDir}
     * @param attributes the document attributes every conversion starts from
     */
    static void convert(File sourceDir, File outputDir, Collection<String> documents, Map<String, String> attributes) {
        Attributes documentAttributes = Attributes.builder().build()
        documentAttributes.setAttributes(new LinkedHashMap<String, Object>(attributes))
        Asciidoctor asciidoctor = Asciidoctor.Factory.create()
        asciidoctor.registerLogHandler({ LogRecord record -> report(record) } as LogHandler)
        try {
            for (String document : documents.toSorted()) {
                File source = new File(sourceDir, document)
                File toDir = source.parentFile.toPath().equals(sourceDir.toPath())
                        ? outputDir
                        : new File(outputDir, sourceDir.toPath().relativize(source.parentFile.toPath()).toString())
                Options options = Options.builder()
                        .backend('html5')
                        .safe(SafeMode.UNSAFE)
                        .standalone(true)
                        .baseDir(source.parentFile)
                        .toDir(toDir)
                        .mkDirs(true)
                        .attributes(documentAttributes)
                        .build()
                asciidoctor.convertFile(source, options)
            }
        }
        finally {
            asciidoctor.shutdown()
        }
    }

    private static void report(LogRecord record) {
        if (record.severity < Severity.WARN) {
            return
        }
        Cursor cursor = record.cursor
        String location = cursor?.file ? "${cursor.file}: line ${cursor.lineNumber}: " : ''
        String severity = record.severity == Severity.WARN ? 'WARNING' : record.severity.name()
        System.err.println("asciidoctor: ${severity}: ${location}${record.message}")
    }
}
