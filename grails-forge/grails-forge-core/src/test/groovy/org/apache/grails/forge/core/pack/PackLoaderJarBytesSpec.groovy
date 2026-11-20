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

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * A pack is read from the bytes of a jar as it is read from the jar in a file: the same pack of a jar
 * that holds one, and the same refusal of one that does not.
 */
class PackLoaderJarBytesSpec extends Specification {

    static final String HEADER = 'format: 1\nkind: feature\ntemplateApi: 1\n' +
            "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n"
    static final String DESCRIPTOR = HEADER + "name: acme\nfiles:\n  - from: a.txt\n    to: a.txt\n"
    static final String PACK = 'META-INF/grails-forge/'

    @TempDir
    Path directory

    @Unroll
    void "the bytes of a jar are read as the jar in a file is: #what"() {
        given:
        byte[] bytes = jar.call() as byte[]
        Path file = directory.resolve('a.jar')
        Files.write(file, bytes)

        when:
        String ofTheFile = outcome { new PackLoader().fromJar(file, 'the jar') }
        String ofTheBytes = outcome { new PackLoader().fromJarBytes(bytes, bytes.length, 'the jar') }

        then:
        ofTheBytes == ofTheFile
        ofTheBytes.contains(expected)

        where:
        what                                       | jar                                                                                              || expected
        'a pack'                                   | { zip([(PACK + 'feature.yml'): DESCRIPTOR, (PACK + 'templates/a.txt'): 'kept']) }                || 'acme: [a.txt:kept]'
        'a pack among other entries'               | { zip(['META-INF/MANIFEST.MF': 'Manifest-Version: 1.0\n', 'com/acme/A.class': 'x' * 5000,
                                                             (PACK + 'feature.yml'): DESCRIPTOR, (PACK + 'templates/a.txt'): 'kept']) }               || 'acme: [a.txt:kept]'
        'entries that are not compressed'          | { zip([(PACK + 'feature.yml'): DESCRIPTOR, (PACK + 'templates/a.txt'): 'kept'], true) }          || 'acme: [a.txt:kept]'
        'a jar with a comment at its end'          | { zip([(PACK + 'feature.yml'): DESCRIPTOR, (PACK + 'templates/a.txt'): 'kept'], false, 'PK end') } || 'acme: [a.txt:kept]'
        'a jar after other bytes'                  | { concat(new byte[100], zip([(PACK + 'feature.yml'): DESCRIPTOR, (PACK + 'templates/a.txt'): 'kept'])) } || 'a.txt'
        'no descriptor'                            | { zip(['META-INF/MANIFEST.MF': 'Manifest-Version: 1.0\n']) }                                     || 'There is no META-INF/grails-forge/feature.yml in the jar'
        'a file the descriptor names is not there' | { zip([(PACK + 'feature.yml'): DESCRIPTOR]) }                                                    || "'a.txt' is not a file under"
        'a directory where a file is named'        | { zip([(PACK + 'feature.yml'): DESCRIPTOR, (PACK + 'templates/a.txt/'): '']) }                   || "'a.txt' is not a file under"
        'two entries of one name for a file'       | { renamed(zip([(PACK + 'feature.yml'): DESCRIPTOR, (PACK + 'templates/a.txt'): 'first',
                                                                     (PACK + 'templates/A.txt'): 'second']), 'templates/A.txt', 'templates/a.txt') }  || "'a.txt' is in the jar more than once"
        'two descriptors'                          | { renamed(zip([(PACK + 'feature.yml'): DESCRIPTOR, (PACK + 'Feature.yml'): DESCRIPTOR,
                                                                     (PACK + 'templates/a.txt'): 'kept']), 'Feature.yml', 'feature.yml') }            || 'feature.yml is in the jar more than once'
        'a file larger than a file can be'         | { zip([(PACK + 'feature.yml'): DESCRIPTOR, (PACK + 'templates/a.txt'): 'a' * (PackLoader.MAX_FILE_BYTES + 1)]) } || 'has more than the'
        'a descriptor larger than one can be'      | { zip([(PACK + 'feature.yml'): DESCRIPTOR + '#' * (PackDescriptorParser.MAX_LENGTH * 3 + 1)]) }  || 'feature.yml is larger than a descriptor can be'
        'bytes that are no jar'                    | { 'not a jar'.bytes }                                                                            || 'It cannot be read as a jar'
        'no bytes'                                 | { new byte[0] }                                                                                  || 'It cannot be read as a jar'
        'half of a jar'                            | { byte[] whole = zip([(PACK + 'feature.yml'): DESCRIPTOR, (PACK + 'templates/a.txt'): 'kept'])
                                                       Arrays.copyOf(whole, whole.length.intdiv(2)) }                                                 || 'It cannot be read as a jar'
        'a jar whose directory is cut out'         | { byte[] whole = zip([(PACK + 'feature.yml'): DESCRIPTOR, (PACK + 'templates/a.txt'): 'kept'])
                                                       concat(Arrays.copyOf(whole, 40), Arrays.copyOfRange(whole, whole.length - 22, whole.length)) } || 'It cannot be read as a jar'
    }

    void "only as many bytes as are said to be the jar are read"() {
        given:
        byte[] jar = zip([(PACK + 'feature.yml'): DESCRIPTOR, (PACK + 'templates/a.txt'): 'kept'])
        byte[] held = concat(jar, new byte[1000])

        expect:
        new PackLoader().fromJarBytes(held, jar.length, 'the jar').content('a.txt') == 'kept'.bytes

        when:
        new PackLoader().fromJarBytes(held, held.length, 'the jar')

        then:
        PackDescriptorException e = thrown()
        e.problems[0].startsWith('It cannot be read as a jar')
    }

    private static String outcome(Closure<Pack> read) {
        try {
            Pack pack = read.call()
            return "${pack.descriptor.name}: ${pack.descriptor.files.collect { "${it.from}:${new String(pack.content(it.from), 'UTF-8')}" }}"
        } catch (PackDescriptorException e) {
            // which exception the reader of a file and the reader of bytes give up with is their own
            return e.problems.collect { String problem -> problem.replaceAll(/(It cannot be read as a jar).*/, '$1') }.toString()
        }
    }

    private static byte[] zip(Map<String, String> entries, boolean stored = false, String comment = null) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        new ZipOutputStream(bytes).withCloseable { ZipOutputStream zip ->
            if (comment != null) {
                zip.setComment(comment)
            }
            entries.each { String name, String text ->
                byte[] content = text.getBytes('UTF-8')
                ZipEntry entry = new ZipEntry(name)
                if (stored) {
                    CRC32 crc = new CRC32()
                    crc.update(content)
                    entry.method = ZipEntry.STORED
                    entry.size = content.length
                    entry.compressedSize = content.length
                    entry.crc = crc.value
                }
                zip.putNextEntry(entry)
                zip.write(content)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    // the stream refuses a second entry of one name, so the name of one is changed where the jar keeps it
    private static byte[] renamed(byte[] jar, String from, String to) {
        return new String(jar, 'ISO-8859-1').replace(from, to).getBytes('ISO-8859-1')
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] both = Arrays.copyOf(first, first.length + second.length)
        System.arraycopy(second, 0, both, first.length, second.length)
        return both
    }
}
