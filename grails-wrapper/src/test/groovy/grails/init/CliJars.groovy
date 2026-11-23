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
package grails.init

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.CRC32
import java.util.zip.ZipEntry

import javax.tools.ToolProvider

/**
 * Jars of a command line for the repositories of the tests, and the checksum files a repository has beside
 * them. The jar has the start class the wrapper starts, which prints the line {@link #STARTED} with its
 * arguments, and an entry that tells one jar from another.
 */
class CliJars {

    static final String STARTED = 'the command line was started with: '

    private static final String MARKER_ENTRY = 'marker.txt'
    private static final String START_CLASS_ENTRY = StartClass.NAME.replace('.', '/') + '.class'
    private static final Map<String, String> ALGORITHMS = [sha512: 'SHA-512', sha256: 'SHA-256', sha1: 'SHA-1', md5: 'MD5']

    private static byte[] startClass

    /**
     * @param marker what tells this jar from another; it is stored as it is, so a test finds it in the bytes
     * @param padding bytes of an entry that makes the jar as large as a test needs it
     */
    static byte[] jar(String marker, byte[] padding = null) {
        Manifest manifest = new Manifest()
        manifest.mainAttributes.put(Attributes.Name.MANIFEST_VERSION, '1.0')
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        JarOutputStream out = new JarOutputStream(bytes, manifest)
        out.putNextEntry(new JarEntry(START_CLASS_ENTRY))
        out.write(compiledStartClass())
        out.closeEntry()
        stored(out, MARKER_ENTRY, marker.getBytes(StandardCharsets.UTF_8))
        if (padding != null) {
            stored(out, 'padding.bin', padding)
        }
        out.close()
        bytes.toByteArray()
    }

    /**
     * @return the marker of a jar that {@link #jar} made
     */
    static String marker(File jar) {
        new JarFile(jar).withCloseable { JarFile file ->
            file.getInputStream(file.getEntry(MARKER_ENTRY)).getText('UTF-8')
        }
    }

    /**
     * Writes the checksum files of a repository beside a file.
     *
     * @param kinds the extensions of the checksum files: sha512, sha256, sha1, md5
     */
    static void writeChecksums(File file, List<String> kinds) {
        kinds.each { String kind ->
            new File(file.parentFile, "${file.name}.${kind}").text = checksum(file.bytes, kind)
        }
    }

    static String checksum(byte[] content, String kind) {
        MessageDigest.getInstance(ALGORITHMS[kind]).digest(content).encodeHex().toString()
    }

    private static void stored(JarOutputStream out, String name, byte[] content) {
        JarEntry entry = new JarEntry(name)
        entry.method = ZipEntry.STORED
        entry.size = content.length
        CRC32 crc = new CRC32()
        crc.update(content)
        entry.crc = crc.value
        out.putNextEntry(entry)
        out.write(content)
        out.closeEntry()
    }

    private static synchronized byte[] compiledStartClass() {
        if (startClass == null) {
            File directory = Files.createTempDirectory('cli-jars').toFile()
            try {
                File source = new File(directory, 'org/apache/grails/cli/DelegatingShellApplication.java')
                source.parentFile.mkdirs()
                source.text = """\
                    package org.apache.grails.cli;
                    public class DelegatingShellApplication {
                        public static void main(String[] args) {
                            System.out.println("${STARTED}" + String.join(",", args));
                        }
                    }
                    """.stripIndent()
                assert ToolProvider.systemJavaCompiler.run(null, null, null, '-d', directory.path, source.path) == 0
                startClass = new File(directory, START_CLASS_ENTRY).bytes
            } finally {
                directory.deleteDir()
            }
        }
        startClass
    }
}
