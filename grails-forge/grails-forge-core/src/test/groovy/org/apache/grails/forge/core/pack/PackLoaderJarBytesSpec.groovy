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
import java.security.MessageDigest
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.spi.ToolProvider
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Timeout
import spock.lang.Unroll

/**
 * A pack is read from a jar only when the jar is one that every program reads alike: the bytes of a
 * jar can be made so that what reads it by its directory and what reads it from its start find two
 * different jars in it. The jars that the tools write are read, from their bytes and from a file
 * alike, and every way of making one file two jars is refused, from the bytes and from a file alike.
 */
class PackLoaderJarBytesSpec extends Specification {

    static final String HEADER = 'format: 1\nkind: feature\ntemplateApi: 1\n' +
            "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n"
    static final String DESCRIPTOR = HEADER + "name: acme\nfiles:\n  - from: a.txt\n    to: a.txt\n"
    static final String OTHER_DESCRIPTOR = HEADER + "name: evil\nfiles:\n  - from: a.txt\n    to: a.txt\n"
    static final String PACK = 'META-INF/grails-forge/'
    static final String NOT_A_JAR = 'It cannot be read as a jar: '

    @TempDir
    Path directory

    @Unroll
    void "a jar is read as #what writes it"() {
        expect:
        outcome(bytes.call(directory) as byte[]) == 'acme: [a.txt:kept]'

        where:
        what                                                 | bytes
        'a stream of the JDK, sizes after the entries'       | { written(pack()) }
        'a stream of the JDK, entries not compressed'        | { written(pack(), true) }
        'a jar stream of the JDK, manifest and directories'  | { written(['META-INF/': '', (PACK): '', (PACK + 'templates/'): '', 'com/': '', 'com/acme/': '',
                                                                         'com/acme/A.class': 'x' * 5000] + pack(), false, true) }
        'a signing tool, with its entries'                   | { written(pack() + ['META-INF/ACME.SF': 'Signature-Version: 1.0\n', 'META-INF/ACME.RSA': 'not checked',
                                                                                  'com/acme/A.class': 'x' * 5000], false, true) }
        'the jar tool of the JDK'                            | { Path at -> byTheJarTool(at, []) }
        'the jar tool of the JDK, not compressed'            | { Path at -> byTheJarTool(at, ['--no-compress']) }
        'a tool that knows the sizes before it writes'       | { jar(entries(pack())) }
        'a tool that writes them before and after an entry'  | { jar(entries(pack()).each { it.sizesAfter = true; it.sizesInFront = true }) }
        'such a tool, entries not compressed'                | { jar(entries(pack()).each { it.method = 0 }) }
        'a tool that marks nothing as UTF-8'                 | { jar(entries(pack()).each { it.flags = 0 }) }
        'any tool, a name that is not ASCII'                 | { jar(entries(pack() + ['com/acme/Ünï/': '', 'com/acme/Ünï/ö.txt': 'x'])) }
        'a tool that adds extra fields'                      | { jar(entries(pack()).each { it.extra = [0xFE, 0xCA, 0, 0] as byte[]
                                                                     it.listedExtra = [0x55, 0x54, 5, 0, 1, 0, 0, 0, 0] as byte[] }) }
        'any tool, a jar in the jar and marks in content'    | { jar(entries(pack() + ['lib/inner.jar': new String(jar(entries(pack(OTHER_DESCRIPTOR, 'EVIL'))), 'ISO-8859-1'),
                                                                                       'marks.bin': 'PK\u0005\u0006PK\u0006\u0007PK\u0006\u0006PK\u0001\u0002PK\u0003\u0004']).each { it.method = 0 }) }
    }

    @Unroll
    void "a jar that can be read as two jars is refused: #what"() {
        expect:
        outcome(bytes.call() as byte[]) == "[${NOT_A_JAR}${refusal}]"

        where:
        what                                                    | bytes                                                                       || refusal
        'a comment at its end'                                  | { jar(entries(pack()), 'a comment'.bytes) }                                 || 'it does not end with the end record of a jar; a jar with a comment or with bytes after it is not read'
        'a comment that holds a second directory and end'       | { twoEnds() }                                                               || 'it does not end with the end record of a jar; a jar with a comment or with bytes after it is not read'
        'a count of a comment that is not there'                | { patched(jar(entries(pack())), -2, 2, 5) }                                 || 'its end record says a comment follows it'
        'bytes after it'                                        | { concat(jar(entries(pack())), new byte[7]) }                               || 'it does not end with the end record of a jar; a jar with a comment or with bytes after it is not read'
        'bytes in front of it'                                  | { concat(new byte[100], jar(entries(pack()))) }                             || NOT_WHERE
        'another jar in front of it'                            | { concat(jar(entries(pack(OTHER_DESCRIPTOR, 'EVIL'))), jar(entries(pack()))) } || NOT_WHERE
        'bytes in front of it that its directory allows for'    | { assembled(entries(pack()), [prefix: new byte[100]]) }                      || 'entry 1 of its directory does not begin where the one before it ends: there are bytes that are of no entry, or entries over one another or in another order than the directory has them'
        'another jar between two entries'                       | { assembled(entries(pack()), [gap: jar(entries(pack(OTHER_DESCRIPTOR, 'EVIL')))]) } || 'entry 2 of its directory does not begin where the one before it ends: there are bytes that are of no entry, or entries over one another or in another order than the directory has them'
        'bytes between the last entry and the directory'        | { assembled(entries(pack()), [beforeDirectory: new byte[9]]) }               || 'there are bytes between its last entry and its directory'
        'bytes between the directory and its end'               | { assembled(entries(pack()), [afterDirectory: new byte[10]]) }               || NOT_WHERE
        'a second entry of a name that the end does not count'  | { assembled(entries(pack() + ['again': 'EVIL']).tap { it[2].name = it[1].name }, [count: 2]) } || 'its directory has more in it than the 2 entries its end record counts'
        'an entry the end does not count'                       | { assembled(entries(pack()), [count: 1]) }                                  || 'its directory has more in it than the 1 entries its end record counts'
        'a count of more entries than there are'                | { assembled(entries(pack()), [count: 3]) }                                  || 'its directory has fewer entries than the 3 its end record counts'
        'two counts'                                            | { patched(jar(entries(pack())), -14, 2, 1) }                                || 'its end record gives two counts of its entries'
        'bytes in the directory after its entries'              | { assembled(entries(pack()), [inDirectory: new byte[60]]) }                 || 'its directory has more in it than the 2 entries its end record counts'
        'a ZIP64 end with the values of the end'                | { assembled(entries(pack()), [zip64: true]) }                               || NOT_WHERE
        'a ZIP64 end where an extra field is'                   | { zip64InExtra() }                                                          || 'its directory has the mark of a second end record in it'
        'the count of ZIP64'                                    | { patched(patched(jar(entries(pack())), -14, 2, 0xFFFF), -12, 2, 0xFFFF) }  || 'it is in the ZIP64 form'
        'a second disk'                                         | { patched(jar(entries(pack())), -18, 2, 1) }                                || 'it is one of several disks'
        'a directory on another disk'                           | { patched(jar(entries(pack())), -16, 2, 1) }                                || 'it is one of several disks'
        'a directory in another order than the entries'         | { assembled(entries(pack()), [reversed: true]) }                            || 'entry 1 of its directory does not begin where the one before it ends: there are bytes that are of no entry, or entries over one another or in another order than the directory has them'
        'two entries of the directory for one in the jar'       | { assembled(entries(pack() + ['b.txt': 'x']).tap { it[2].listed.offset = 0L }, [:]) } || 'entry 3 of its directory does not begin where the one before it ends: there are bytes that are of no entry, or entries over one another or in another order than the directory has them'
    }

    @Unroll
    void "a jar with an entry that is not read alike by all is refused, whether the entry is of the pack or not: #what"() {
        expect:
        outcome(jar(entries(pack() + ['other/entry.bin': 'content']).tap { change.call(it[2]) })) == "[${NOT_A_JAR}${refusal}]"
        outcome(jar(entries(pack()).tap { change.call(it[1]) })) == "[${NOT_A_JAR}${refusal.replaceAll(/\b3\b/, '2')}]"

        where:
        what                                             | change                                                                  || refusal
        'encrypted'                                      | { it.flags = 0x801 }                                                    || 'entry 3 of its directory is encrypted'
        'encrypted, says the header only'                | { it.header.flags = 0x801 }                                             || 'the header of entry 3 of its directory is encrypted'
        'strongly encrypted'                             | { it.flags = 0x840 }                                                    || 'entry 3 of its directory has flags a jar has no use for'
        'patched data'                                   | { it.flags = 0x820 }                                                    || 'entry 3 of its directory has flags a jar has no use for'
        'a hidden directory'                             | { it.flags = 0x2800 }                                                   || 'entry 3 of its directory has flags a jar has no use for'
        'compressed in another way'                      | { it.header.method = 14; it.listed.method = 14 }                        || 'entry 3 of its directory is compressed in another way than deflate'
        'needing a newer form'                           | { it.listed.version = 45 }                                              || 'entry 3 of its directory asks for more than a jar has'
        'a name that is not UTF-8'                       | { it.name = [0x6f, 0xff, 0xfe, 0x2f, 0x78] as byte[] }                  || 'entry 3 of its directory has a name that is not UTF-8'
        'a name in UTF-8 that does not say so'           | { it.name = 'ö.txt'.getBytes('UTF-8'); it.flags = 0 }                   || 'entry 3 of its directory has a name that is not ASCII and is not said to be UTF-8'
        'a name with a NUL'                              | { it.name = 'a\u0000.txt'.bytes }                                       || 'entry 3 of its directory has a control character in its name'
        'a name with a line break'                       | { it.name = 'a\n.txt'.bytes }                                           || 'entry 3 of its directory has a control character in its name'
        'a name with a backslash'                        | { it.name = 'a\\b.txt'.bytes }                                          || 'entry 3 of its directory has a backslash in its name'
        'a name that goes up'                            | { it.name = 'a/../../b.txt'.bytes }                                     || "entry 3 of its directory has a name that is an absolute path, or that has an empty step, '.' or '..'"
        'a name that is an absolute path'                | { it.name = '/etc/b.txt'.bytes }                                        || "entry 3 of its directory has a name that is an absolute path, or that has an empty step, '.' or '..'"
        'a name with an empty step'                      | { it.name = 'a//b.txt'.bytes }                                          || "entry 3 of its directory has a name that is an absolute path, or that has an empty step, '.' or '..'"
        'no name'                                        | { it.name = new byte[0] }                                               || 'entry 3 of its directory has no name'
        'the name of the entry before it'                | { it.name = (PACK + 'feature.yml').bytes }                              || 'entries 1 and 3 of its directory have one name'
        'the name of the entry before it as a directory' | { it.name = (PACK + 'feature.yml/').bytes; it.content = new byte[0] }   || 'entry 1 of its directory is a file, and entry 3 has it for a directory'
        'a name below that of the entry before it'       | { it.name = (PACK + 'feature.yml/x').bytes }                            || 'entry 1 of its directory is a file, and entry 3 has it for a directory'
        'a directory with content'                       | { it.name = 'other/'.bytes }                                            || 'entry 3 of its directory is a directory with content'
        'an extra field that is cut'                     | { it.listedExtra = [0x55, 0x54, 99, 0, 1, 2] as byte[] }                      || 'entry 3 of its directory has extra fields that are not whole'
        'an extra field of ZIP64'                        | { it.listedExtra = [1, 0, 0, 0] as byte[] }                             || 'entry 3 of its directory has an extra field of the ZIP64 form'
        'an extra field of ZIP64 in front of it'         | { it.extra = [1, 0, 3, 0, 1, 2, 3] as byte[] }                          || 'the header of entry 3 of its directory has an extra field of the ZIP64 form'
        'an extra field that is another name for it'     | { it.listedExtra = secondName(it.name, PACK + 'feature.yml') }          || 'entry 3 of its directory has an extra field of a kind a jar has no use for (0x7075)'
        'such an extra field in front of it'             | { it.extra = secondName(it.name, PACK + 'feature.yml') }                || 'the header of entry 3 of its directory has an extra field of a kind a jar has no use for (0x7075)'
        'an extra field of a kind that is not known'     | { it.listedExtra = [0x99, 0x99, 0, 0] as byte[] }                       || 'entry 3 of its directory has an extra field of a kind a jar has no use for (0x9999)'
        'an extra field in front of it that is cut'      | { it.extra = [0x55, 0x54] as byte[] }                                   || 'the header of entry 3 of its directory has extra fields that are not whole'
        'a length of ZIP64'                              | { it.listed.length = 0xFFFFFFFFL }                                      || 'entry 3 of its directory is in the ZIP64 form'
        'a comment'                                      | { it.comment = 'c'.bytes }                                              || 'entry 3 of its directory has a comment'
        'another disk'                                   | { it.listed.disk = 1 }                                                  || 'entry 3 of its directory is on another disk'
        'another name in front of it'                    | { it.header.name = it.name.clone().tap { it[0] = (byte) 0x58 } }        || 'the header of entry 3 of its directory has another name than the directory'
        'a longer name in front of it'                   | { it.header.name = concat(it.name, 'x'.bytes) }                         || 'the header of entry 3 of its directory has another name than the directory'
        'another way of compressing in front of it'      | { it.header.method = 0 }                                                || 'the header of entry 3 of its directory has other flags or another way of compressing than the directory'
        'other flags in front of it'                     | { it.header.flags = 0 }                                                 || 'the header of entry 3 of its directory has other flags or another way of compressing than the directory'
        'another length in front of it'                  | { it.header.length = 1L }                                               || 'the header of entry 3 of its directory has another checksum or other lengths than the directory'
        'another checksum in front of it'                | { it.header.crc = 1L }                                                  || 'the header of entry 3 of its directory has another checksum or other lengths than the directory'
        'sizes after it and one of them in front of it'  | { it.sizesAfter = true; it.header.length = (long) it.content.length }   || 'the header of entry 3 of its directory has another checksum or other lengths than the directory'
        'sizes after it and others in front of it'       | { it.sizesAfter = true; it.sizesInFront = true; it.header.length = 1L } || 'the header of entry 3 of its directory has another checksum or other lengths than the directory'
        'other sizes after it'                           | { it.sizesAfter = true; it.after.length = 1L }                          || 'what follows entry 3 of its directory has another checksum or other lengths than the directory, or is not there'
        'sizes after it without their mark'              | { it.sizesAfter = true; it.after.signature = 0L }                       || 'what follows entry 3 of its directory has another checksum or other lengths than the directory, or is not there'
        'sizes after it, not compressed'                 | { it.sizesAfter = true; it.method = 0 }                                 || 'entry 3 of its directory is stored and has two lengths, or has them after it'
        'not compressed, with two lengths'               | { it.method = 0; it.listed.length = 1L; it.header.length = 1L }         || 'entry 3 of its directory is stored and has two lengths, or has them after it'
        'another checksum than its content'              | { it.listed.crc = 1L; it.header.crc = 1L }                              || 'entry 3 of its directory does not have the CRC-32 the directory says'
        'not compressed, another checksum'               | { it.method = 0; it.listed.crc = 1L; it.header.crc = 1L }               || 'entry 3 of its directory does not have the CRC-32 the directory says'
        'more content than it says'                      | { it.listed.length = 1L; it.header.length = 1L }                        || 'entry 3 of its directory does not have the content the directory says: it is longer or does not end'
        'less content than it says'                      | { it.listed.length = 4000L; it.header.length = 4000L }                  || 'entry 3 of its directory does not have the content the directory says: it ends before its length'
        'bytes after the end of what is deflated'        | { it.data = concat(deflated(it.content), 'HIDDEN'.bytes) }              || 'entry 3 of its directory does not have the content the directory says: it ends before its length'
        'deflated bytes that are cut'                    | { it.data = Arrays.copyOf(deflated(it.content), deflated(it.content).length - 2) } || 'entry 3 of its directory does not have the content the directory says: it is longer or does not end'
        'bytes that are not deflated'                    | { it.data = [0xff, 0xff, 0xff, 0xff, 0xff] as byte[] }                  || 'entry 3 of its directory is not deflated as it says'
        'not said to be compressed, and deflated'        | { it.header.method = 0; it.listed.method = 0 }                          || 'entry 3 of its directory is stored and has two lengths, or has them after it'
    }

    @Unroll
    void "a jar that holds no pack that can be used is refused: #what"() {
        expect:
        outcome(bytes.call() as byte[]).contains(expected)

        where:
        what                                       | bytes                                                                                            || expected
        'no descriptor'                            | { written(['META-INF/MANIFEST.MF': 'Manifest-Version: 1.0\n']) }                                 || 'There is no META-INF/grails-forge/feature.yml in the jar'
        'no entries'                               | { jar([]) }                                                                                      || 'There is no META-INF/grails-forge/feature.yml in the jar'
        'a file the descriptor names is not there' | { written([(PACK + 'feature.yml'): DESCRIPTOR]) }                                                || "'a.txt' is not a file under"
        'a directory where a file is named'        | { written([(PACK + 'feature.yml'): DESCRIPTOR, (PACK + 'templates/a.txt/'): '']) }               || "'a.txt' is not a file under"
        'a file larger than a file can be'         | { written([(PACK + 'feature.yml'): DESCRIPTOR, (PACK + 'templates/a.txt'): 'a' * (PackLoader.MAX_FILE_BYTES + 1)]) } || 'has more than the'
        'a descriptor larger than one can be'      | { written([(PACK + 'feature.yml'): DESCRIPTOR + '#' * (PackDescriptorParser.MAX_LENGTH * 3 + 1)]) } || 'feature.yml is larger than a descriptor can be'
        'bytes that are no jar'                    | { 'not a jar'.bytes }                                                                            || NOT_A_JAR
        'no bytes'                                 | { new byte[0] }                                                                                  || NOT_A_JAR
        'half of a jar'                            | { byte[] whole = written(pack())
                                                       Arrays.copyOf(whole, whole.length.intdiv(2)) }                                                 || NOT_A_JAR
        'a jar whose directory is cut out'         | { byte[] whole = written(pack())
                                                       concat(Arrays.copyOf(whole, 40), Arrays.copyOfRange(whole, whole.length - 22, whole.length)) } || NOT_A_JAR
    }

    void "only as many bytes as are said to be the jar are read"() {
        given:
        byte[] jar = written(pack())
        byte[] held = concat(jar, new byte[1000])

        expect:
        new PackLoader().fromJarBytes(held, jar.length, 'the jar').content('a.txt') == 'kept'.bytes

        when:
        new PackLoader().fromJarBytes(held, held.length, 'the jar')

        then:
        PackDescriptorException e = thrown()
        e.problems[0].startsWith(NOT_A_JAR)
    }

    void "what is read of a jar is bounded, whatever the jar says"() {
        given: 'names that are longer together than the names of a jar can be'
        byte[] name = new byte[0xFFFF]
        Arrays.fill(name, (byte) 0x61)
        List<Entry> named = entries(pack()) + (1..70).collect { int index -> new Entry(name: name.clone().tap { it[0] = (byte) (0x30 + index.intdiv(10)); it[1] = (byte) (0x30 + index % 10) }, content: new byte[0], method: 0) }

        expect:
        outcome(jar(named)) == "[${NOT_A_JAR}the names of its entries have more than the 4194304 bytes they can have together]"

        and: 'an entry that says how large it is, and one that does not'
        byte[] zeros = deflated(new byte[8 * 1024 * 1024])
        outcome(jar(entries(pack() + ['zeros.bin': '']).tap { it[2].data = zeros; it[2].listed.length = 268435457L; it[2].header.length = 268435457L })) ==
                "[${NOT_A_JAR}its entries have more than the 268435456 bytes they can have together]"
        outcome(jar(entries(pack() + ['zeros.bin': '']).tap { it[2].data = zeros; it[2].listed.length = 4L; it[2].header.length = 4L })) ==
                "[${NOT_A_JAR}entry 3 of its directory does not have the content the directory says: it is longer or does not end]"

        when: 'a file larger than a jar can be, of which nothing is read'
        Path large = directory.resolve('large.jar')
        new RandomAccessFile(large.toFile(), 'rw').withCloseable { it.setLength(PackLoader.MAX_JAR_BYTES + 1L) }
        new PackLoader().fromJar(large)

        then:
        PackDescriptorException e = thrown()
        e.problems == ["It has more than the ${PackLoader.MAX_JAR_BYTES} bytes a jar of a pack can have".toString()]
    }

    void "the jar with a second directory in its comment was two packs, and is none"() {
        given: 'a jar that ends with the end record of the pack evil, with a comment that holds the directory and the end record of the pack acme'
        byte[] bytes = twoEnds()
        Path file = directory.resolve('two.jar')
        Files.write(file, bytes)

        expect: 'the JDK reads the pack acme from the file; what looks for the comment that ends the file read the pack evil'
        new ZipFile(file.toFile()).withCloseable { ZipFile zip -> new String(zip.getInputStream(zip.getEntry(PACK + 'templates/a.txt')).readAllBytes()) } == 'GOOD'

        and:
        outcome(bytes).startsWith("[${NOT_A_JAR}")
    }

    @Timeout(600)
    void "a jar that is taken is one that is read alike by its directory and from its start"() {
        given: 'jars that are right, jars with one of the things in them that make a jar two, and both with a byte or a number in them changed'
        Random random = new Random(16348)
        List<byte[]> right = [jar(entries(pack())), jar(entries(pack()).each { it.method = 0 }), written(pack()), written(pack(), true),
                              written(['META-INF/': '', 'com/acme/A.class': 'x' * 300] + pack() + [(PACK + 'templates/b.txt'): 'other'], false, true),
                              jar(entries(pack() + ['a': '1', 'a.b': '2', 'b/': '', 'b/c': '3']).each { it.sizesAfter = it.content.length > 0 })]
        int taken = 0
        int refused = 0

        expect:
        1500.times { int number ->
            byte[] bytes = number % 3 == 0 ? variedJar(random, true) : number % 3 == 1 ? variedJar(random, false) : right[random.nextInt(right.size())].clone()
            (1 + random.nextInt(2)).times {
                int kind = number % 3 == 2 ? random.nextInt(4) : random.nextInt(24)
                if (kind == 0) {
                    int at = random.nextInt(bytes.length)
                    bytes[at] = (byte) (bytes[at] ^ (1 << random.nextInt(8)))
                } else if (kind == 1) {
                    // a number of two bytes somewhere, which the places of the lengths, counts and flags are among
                    int at = random.nextInt(bytes.length - 1)
                    int value = [0, 1, 8, 0xFFFF, bytes.length & 0xFFFF, random.nextInt(0x10000), (bytes[at] & 0xFF) + 1, (bytes[at] & 0xFF) ^ 8][random.nextInt(8)]
                    bytes[at] = (byte) value
                    bytes[at + 1] = (byte) (value >> 8)
                } else if (kind == 2) {
                    int at = random.nextInt(bytes.length + 1)
                    bytes = concat(concat(Arrays.copyOf(bytes, at), new byte[1 + random.nextInt(30)]), Arrays.copyOfRange(bytes, at, bytes.length))
                } else if (kind == 3) {
                    byte[] other = right[random.nextInt(right.size())]
                    bytes = random.nextBoolean() ? concat(bytes, other) : concat(Arrays.copyOf(bytes, random.nextInt(bytes.length + 1)), other)
                }
            }
            String outcome = outcome(bytes)
            if (outcome.startsWith("[${NOT_A_JAR}")) {
                refused++
            } else {
                taken++
                List<String> byTheDirectory = readByTheDirectory(bytes)
                assert byTheDirectory == readFromTheStart(bytes)
                assert byTheDirectory*.split(' ')*.first().toSet().size() == byTheDirectory.size()
            }
        }

        and: 'both happened, often'
        taken > 250
        refused > 400
    }

    private static final String NOT_WHERE = 'its directory is not where its end record says: there are bytes in front of the jar or between its ' +
            'directory and its end, or the directory has another length'

    /**
     * What is read from the bytes, which is what is read from a file of them, or how both are refused.
     */
    private String outcome(byte[] bytes) {
        Path file = directory.resolve('read.jar')
        Files.write(file, bytes)
        String ofTheFile = outcomeOf { new PackLoader().fromJar(file, 'the jar') }
        String ofTheBytes = outcomeOf { new PackLoader().fromJarBytes(bytes, bytes.length, 'the jar') }
        assert ofTheBytes == ofTheFile
        return ofTheBytes
    }

    private static String outcomeOf(Closure<Pack> read) {
        try {
            Pack pack = read.call()
            return "${pack.descriptor.name}: ${pack.descriptor.files.collect { "${it.from}:${new String(pack.content(it.from), 'UTF-8')}" }}"
        } catch (PackDescriptorException e) {
            assert e.problems.every { String problem -> !problem.contains('@|') && problem.chars.every { char c -> c >= (char) ' ' && c != (char) 0x7F } }
            return e.problems.toString()
        }
    }

    private List<String> readByTheDirectory(byte[] bytes) {
        Path file = directory.resolve('directory.jar')
        Files.write(file, bytes)
        new ZipFile(file.toFile()).withCloseable { ZipFile zip ->
            zip.entries().toList().collect { ZipEntry entry -> "${entry.name.bytes.encodeHex()} ${digest(zip.getInputStream(entry).readAllBytes())}".toString() }
        }
    }

    private static List<String> readFromTheStart(byte[] bytes) {
        List<String> read = []
        new ZipInputStream(new ByteArrayInputStream(bytes)).withCloseable { ZipInputStream zip ->
            ZipEntry entry
            while ((entry = zip.nextEntry) != null) {
                read << "${entry.name.bytes.encodeHex()} ${digest(zip.readAllBytes())}".toString()
            }
        }
        return read
    }

    private static String digest(byte[] bytes) {
        return MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
    }

    private static Map<String, String> pack(String descriptor = DESCRIPTOR, String content = 'kept') {
        return [(PACK + 'feature.yml'): descriptor, (PACK + 'templates/a.txt'): content]
    }

    /**
     * A jar as the streams of the JDK write it: an entry that is deflated has its checksum and its
     * lengths after it, and not in the header in front of it.
     */
    private static byte[] written(Map<String, String> entries, boolean stored = false, boolean manifest = false) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        ZipOutputStream stream = manifest ? new JarOutputStream(bytes, new Manifest().tap { mainAttributes.putValue('Manifest-Version', '1.0') }) : new ZipOutputStream(bytes)
        stream.withCloseable { ZipOutputStream zip ->
            entries.each { String name, String text ->
                byte[] content = text.getBytes('UTF-8')
                ZipEntry entry = new ZipEntry(name)
                if (stored || name.endsWith('/')) {
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

    private static byte[] byTheJarTool(Path directory, List<String> options) {
        Path content = Files.createDirectories(directory.resolve('content'))
        Files.createDirectories(content.resolve(PACK + 'templates'))
        Files.createDirectories(content.resolve('com/acme'))
        Files.writeString(content.resolve(PACK + 'feature.yml'), DESCRIPTOR)
        Files.writeString(content.resolve(PACK + 'templates/a.txt'), 'kept')
        Files.writeString(content.resolve('com/acme/A.class'), 'x' * 5000)
        Path jar = directory.resolve('tool.jar')
        assert ToolProvider.findFirst('jar').get().run(System.out, System.err, (['--create', '--file', jar.toString()] + options + ['-C', content.toString(), '.']) as String[]) == 0
        return Files.readAllBytes(jar)
    }

    /**
     * An entry of a jar that is put together here byte for byte, with what the directory, the header
     * in front of the entry and what follows the entry say of it when that is not what is so.
     */
    private static class Entry {
        byte[] name
        byte[] content
        int method = 8
        int flags = 0x800
        boolean sizesAfter
        // with the sizes after the entry: whether they are in the header in front of it too, as some tools write them
        boolean sizesInFront
        byte[] extra = new byte[0]
        byte[] listedExtra = new byte[0]
        byte[] comment = new byte[0]
        // the bytes of the entry in the jar when they are not its content, deflated or not
        byte[] data
        Map<String, Object> header = [:]
        Map<String, Object> listed = [:]
        Map<String, Object> after = [:]

        byte[] bytes() {
            return data != null ? data : method == 8 ? deflated(content) : content
        }

        int flagsWritten() {
            return flags | (sizesAfter ? 8 : 0)
        }
    }

    private static List<Entry> entries(Map<String, String> entries) {
        return entries.collect { String name, String text ->
            new Entry(name: name.getBytes('UTF-8'), content: text.getBytes('ISO-8859-1'), method: name.endsWith('/') ? 0 : 8)
        }
    }

    private static byte[] jar(List<Entry> entries, byte[] comment = new byte[0]) {
        return assembled(entries, [comment: comment])
    }

    /**
     * @param odd what is put into the jar that a jar does not have: bytes in front ({@code prefix}),
     * after the first entry ({@code gap}), before, in and after the directory, a ZIP64 end, another
     * count, the directory in the other order, a comment
     */
    private static byte[] assembled(List<Entry> entries, Map<String, Object> odd) {
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        out.write((odd.prefix ?: new byte[0]) as byte[])
        List<Long> offsets = []
        entries.eachWithIndex { Entry entry, int index ->
            if (index == 1) {
                out.write((odd.gap ?: new byte[0]) as byte[])
            }
            offsets << (long) out.size()
            byte[] data = entry.bytes()
            byte[] name = (entry.header.name ?: entry.name) as byte[]
            u32(out, 0x04034b50L)
            u16(out, 20)
            u16(out, said(entry.header, 'flags', entry.flagsWritten()))
            u16(out, said(entry.header, 'method', entry.method))
            u32(out, 0x21000000L)
            boolean none = entry.sizesAfter && !entry.sizesInFront
            u32(out, said(entry.header, 'crc', none ? 0L : crc(entry.content)))
            u32(out, (none ? 0L : data.length) as long)
            u32(out, said(entry.header, 'length', none ? 0L : entry.content.length))
            u16(out, name.length)
            u16(out, entry.extra.length)
            out.write(name)
            out.write(entry.extra)
            out.write(data)
            if (entry.sizesAfter) {
                u32(out, said(entry.after, 'signature', 0x08074b50L))
                u32(out, crc(entry.content))
                u32(out, data.length)
                u32(out, said(entry.after, 'length', entry.content.length))
            }
        }
        out.write((odd.beforeDirectory ?: new byte[0]) as byte[])
        long start = out.size()
        ByteArrayOutputStream listing = new ByteArrayOutputStream()
        List<Integer> order = (0..<entries.size()).toList()
        (odd.reversed ? order.reverse() : order).each { int index ->
            Entry entry = entries[index]
            byte[] data = entry.bytes()
            u32(listing, 0x02014b50L)
            u16(listing, 20)
            u16(listing, said(entry.listed, 'version', 20))
            u16(listing, entry.flagsWritten())
            u16(listing, said(entry.listed, 'method', entry.method))
            u32(listing, 0x21000000L)
            u32(listing, said(entry.listed, 'crc', crc(entry.content)))
            u32(listing, data.length)
            u32(listing, said(entry.listed, 'length', entry.content.length))
            u16(listing, entry.name.length)
            u16(listing, entry.listedExtra.length)
            u16(listing, entry.comment.length)
            u16(listing, said(entry.listed, 'disk', 0))
            u16(listing, 0)
            u32(listing, 0)
            u32(listing, said(entry.listed, 'offset', offsets[index]))
            listing.write(entry.name)
            listing.write(entry.listedExtra)
            listing.write(entry.comment)
        }
        listing.write((odd.inDirectory ?: new byte[0]) as byte[])
        out.write(listing.toByteArray())
        if (odd.zip64) {
            // the end of ZIP64 and what points to it, with the values the end has, as some tools write them
            long at = out.size()
            u32(out, 0x06064b50L)
            u64(out, 44)
            u16(out, 45)
            u16(out, 45)
            u32(out, 0)
            u32(out, 0)
            u64(out, entries.size())
            u64(out, entries.size())
            u64(out, listing.size())
            u64(out, start)
            u32(out, 0x07064b50L)
            u32(out, 0)
            u64(out, at)
            u32(out, 1)
        }
        out.write((odd.afterDirectory ?: new byte[0]) as byte[])
        out.write(end((int) said(odd, 'count', entries.size()), listing.size(), start, (odd.comment ?: new byte[0]) as byte[]))
        return out.toByteArray()
    }

    private static long said(Map<String, Object> instead, String of, Number what) {
        return ((instead.containsKey(of) ? instead[of] : what) as Number).longValue()
    }

    private static byte[] end(int count, long size, long start, byte[] comment = new byte[0]) {
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        u32(out, 0x06054b50L)
        u16(out, 0)
        u16(out, 0)
        u16(out, count)
        u16(out, count)
        u32(out, size)
        u32(out, start)
        u16(out, comment.length)
        out.write(comment)
        return out.toByteArray()
    }

    /**
     * One file that was two jars. It ends with the end record of the pack evil, whose comment is as
     * long as what follows it: the directory and the end record of the pack acme, and one byte more.
     * The entries of both are in the jar. What takes the end record whose comment ends the file reads
     * the pack evil; the JDK and the tools take the last end record that names a directory, and read
     * the pack acme.
     */
    private static byte[] twoEnds() {
        List<Entry> byTheComment = entries(pack(OTHER_DESCRIPTOR, 'EVIL'))
        List<Entry> byTheLastEnd = entries(pack(DESCRIPTOR, 'GOOD'))
        byte[] both = assembled(byTheComment + byTheLastEnd, [:])
        byte[] first = assembled(byTheComment, [:])
        byte[] second = assembled(byTheLastEnd, [:])
        int entriesEnd = (int) number(both, both.length - 6, 4)
        int firstEntries = (int) number(first, first.length - 6, 4)
        byte[] firstDirectory = Arrays.copyOfRange(first, firstEntries, first.length - 22)
        byte[] secondDirectory = Arrays.copyOfRange(second, (int) number(second, second.length - 6, 4), second.length - 22)
        // the entries of the second are after those of the first
        for (int at = 0; at + 46 <= secondDirectory.length; at += 46 + (int) number(secondDirectory, at + 28, 2)) {
            put(secondDirectory, at + 42, 4, number(secondDirectory, at + 42, 4) + firstEntries)
        }
        byte[] secondEnd = end(2, secondDirectory.length, entriesEnd + firstDirectory.length + 22)
        byte[] comment = concat(concat(secondDirectory, secondEnd), [0x0a] as byte[])
        return concat(concat(Arrays.copyOf(both, entriesEnd), firstDirectory), end(2, firstDirectory.length, entriesEnd, comment))
    }

    /**
     * A jar that ends as a jar has to, in which the bytes in front of the end record, where the end
     * of ZIP64 is looked for, are an extra field of its last entry that holds one.
     */
    private static byte[] zip64InExtra() {
        ByteArrayOutputStream field = new ByteArrayOutputStream()
        u16(field, 0x5455)
        u16(field, 20)
        u32(field, 0x07064b50L)
        u32(field, 0)
        u64(field, 0)
        u32(field, 1)
        return jar(entries(pack()).tap { it[1].listedExtra = field.toByteArray() })
    }

    /**
     * A jar put together from entries of every kind a jar has, and, unless it is to be a right one,
     * with now and then one of the things in it that make a jar two.
     */
    private static byte[] variedJar(Random random, boolean right) {
        List<String> names = ['a', 'a.b', 'b/', 'b/c', 'ü.txt', 'META-INF/MANIFEST.MF', 'com/acme/A.class', 'com/', PACK + 'templates/b.txt'] +
                (right ? [] : ['a/', 'a/b', '../x', 'b\\c', 'd//e', PACK + 'templates/a.txt', PACK + 'feature.yml'])
        List<byte[]> extras = [new byte[0], [0xFE, 0xCA, 0, 0] as byte[], [0x55, 0x54, 5, 0, 1, 0, 0, 0, 0] as byte[]] +
                (right ? [] : [[1, 0, 0, 0] as byte[], [0x55, 0x54, 9] as byte[], [0x55, 0x54, 4, 0, 0x50, 0x4B, 6, 7] as byte[], [0x99, 0x99, 0, 0] as byte[], secondName('a'.bytes, 'b')])
        List<Integer> flags = [0x800, 0x806] + (right ? [] : [0, 1, 0x20, 0x40, 0x2000])
        Map<String, String> chosen = pack(random.nextInt(5) == 0 ? OTHER_DESCRIPTOR : DESCRIPTOR)
        random.nextInt(5).times {
            String name = names[random.nextInt(names.size())]
            chosen[name] = name.endsWith('/') ? '' : 'c' * random.nextInt(400)
        }
        List<Entry> made = right ? entries(chosen) : chosen.collect { String name, String text -> new Entry(name: name.getBytes('UTF-8'), content: text.bytes) }
        Collections.shuffle(made, random)
        made.each { Entry entry ->
            entry.method = random.nextInt(3) == 0 || entry.content.length == 0 ? 0 : 8
            entry.sizesAfter = entry.method == 8 && random.nextInt(3) == 0
            entry.sizesInFront = random.nextInt(3) == 0
            entry.flags = flags[random.nextInt(flags.size())]
            if (random.nextInt(4) == 0) {
                entry.extra = extras[random.nextInt(extras.size())]
            }
            if (random.nextInt(4) == 0) {
                entry.listedExtra = extras[random.nextInt(extras.size())]
            }
            switch (right ? -1 : random.nextInt(60)) {
                case 0: entry.listed.length = (long) random.nextInt(30); break
                case 1: entry.header.name = 'other'.bytes; break
                case 2: entry.header.method = entry.method == 8 ? 0 : 8; break
                case 3: entry.data = concat(entry.bytes(), 'TRAIL'.bytes); break
                case 4: entry.listed.offset = (long) random.nextInt(200); break
                case 5: entry.comment = 'c'.bytes; break
                case 6: entry.header.flags = entry.flagsWritten() ^ 8; break
                case 7: entry.listed.crc = 7L; break
            }
        }
        Map<String, Object> odd = [:]
        switch (right ? -1 : random.nextInt(40)) {
            case 0: odd.prefix = new byte[1 + random.nextInt(40)]; break
            case 1: odd.gap = jar(entries(pack(OTHER_DESCRIPTOR, 'EVIL'))); break
            case 2: odd.beforeDirectory = new byte[1 + random.nextInt(8)]; break
            case 3: odd.inDirectory = new byte[1 + random.nextInt(60)]; break
            case 4: odd.afterDirectory = new byte[1 + random.nextInt(8)]; break
            case 5: odd.zip64 = true; break
            case 6: odd.count = Math.max(1, made.size() + random.nextInt(3) - 1); break
            case 7: odd.reversed = true; break
            case 8: odd.comment = 'hello'.bytes; break
        }
        return assembled(made, odd)
    }

    /**
     * The extra field that gives an entry a name in UTF-8 besides the one it has, which is taken for
     * the name of the entry by some programs when it says, as here, that it is of that name.
     */
    private static byte[] secondName(byte[] name, String other) {
        ByteArrayOutputStream field = new ByteArrayOutputStream()
        u16(field, 0x7075)
        u16(field, 5 + other.length())
        field.write(1)
        u32(field, crc(name))
        field.write(other.bytes)
        return field.toByteArray()
    }

    private static byte[] deflated(byte[] content) {
        Deflater deflater = new Deflater(9, true)
        deflater.setInput(content)
        deflater.finish()
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        byte[] buffer = new byte[65536]
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer))
        }
        deflater.end()
        return out.toByteArray()
    }

    private static long crc(byte[] content) {
        CRC32 crc = new CRC32()
        crc.update(content)
        return crc.value
    }

    private static void u16(ByteArrayOutputStream out, long value) {
        out.write((int) (value & 0xFF))
        out.write((int) ((value >> 8) & 0xFF))
    }

    private static void u32(ByteArrayOutputStream out, long value) {
        u16(out, value & 0xFFFF)
        u16(out, (value >> 16) & 0xFFFF)
    }

    private static void u64(ByteArrayOutputStream out, long value) {
        u32(out, value & 0xFFFFFFFFL)
        u32(out, (value >>> 32) & 0xFFFFFFFFL)
    }

    private static long number(byte[] bytes, int at, int width) {
        long value = 0
        for (int index = width - 1; index >= 0; index--) {
            value = (value << 8) | (bytes[at + index] & 0xFF)
        }
        return value
    }

    private static void put(byte[] bytes, int at, int width, long value) {
        for (int index = 0; index < width; index++) {
            bytes[at + index] = (byte) (value >> (8 * index))
        }
    }

    /**
     * @param at where the number is, from the end of the bytes when it is negative
     */
    private static byte[] patched(byte[] bytes, int at, int width, long value) {
        byte[] changed = bytes.clone()
        put(changed, at < 0 ? bytes.length + at : at, width, value)
        return changed
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] both = Arrays.copyOf(first, first.length + second.length)
        System.arraycopy(second, 0, both, first.length, second.length)
        return both
    }
}
