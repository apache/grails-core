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

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * The bytes of a jar, taken only when they are a jar in the one form that leaves nothing to whoever
 * reads it.
 *
 * <p>A jar can be read from its end, by its directory, or from its start, entry by entry, and the
 * format lets the two say different things: a second directory in a comment, entries the directory
 * does not count, bytes no entry covers, a header in front of an entry that disagrees with the
 * directory. One file can so be two jars, and which one it is depends on the program that opens it.
 * The pack of a jar has to be the one every program finds in it, so the jar is taken only when all of
 * it is accounted for and both ways of reading give the same entries:</p>
 * <ul>
 * <li>the jar is its entries from its first byte, one directly after the other, then the directory,
 * then the end record of 22 bytes, with which it ends: no comment, nothing in front, between or
 * after;</li>
 * <li>the end record names one disk, gives the same count twice, and where it says the directory is
 * and how long it is, is where the entries end and the end record begins; there is nothing of ZIP64,
 * so fewer than 65535 entries and no field that stands for a larger one;</li>
 * <li>the directory is exactly the entries it counts, in the order the entries have in the jar;</li>
 * <li>every entry, not only those of the pack: it is stored or deflated, not encrypted, with no flag
 * but those of the deflate options, of the sizes after the entry and of the name in UTF-8; its name
 * is UTF-8 (and says so when it is not ASCII), has no control character and no backslash, is no
 * absolute path and has no empty step, {@code .} or {@code ..}; no two entries have one name, and
 * none is a file where another needs a directory; its extra fields are whole and of the few kinds
 * that give the times and the owner of a file, so none is of ZIP64 and none is a second name for the
 * entry, which some programs take for its name; it has no comment;</li>
 * <li>the header in front of each entry says what the directory says: name, flags, way of
 * compressing, and the checksum and sizes. When these follow the entry, as a jar written to a stream
 * has them, they have to be the same there, and in the header they are those again or all zero;</li>
 * <li>the content of every entry is what was promised: a deflated entry ends exactly where its
 * compressed length ends, and every entry has the length and the CRC-32 the directory gives.</li>
 * </ul>
 *
 * <p>Nothing is kept but the bytes that were given and a few numbers for each entry: no name is made
 * a string. What is read is bounded: the entries by what a jar without ZIP64 can count, their names
 * by {@link #MAX_NAME_BYTES} together, their content by {@link #MAX_CONTENT_BYTES} together.</p>
 */
@CompileStatic
@PackageScope
final class PackJar {

    /** The most bytes the names of the entries of a jar can have together */
    static final int MAX_NAME_BYTES = 4 * 1024 * 1024

    /** The most bytes the entries of a jar can have together when they are not compressed */
    static final long MAX_CONTENT_BYTES = 256L * 1024 * 1024

    private static final long END_SIGNATURE = 0x06054b50L
    private static final long CENTRAL_SIGNATURE = 0x02014b50L
    private static final long LOCAL_SIGNATURE = 0x04034b50L
    private static final long AFTER_SIGNATURE = 0x08074b50L
    private static final int END_LENGTH = 22
    private static final int CENTRAL_LENGTH = 46
    private static final int LOCAL_LENGTH = 30
    private static final int AFTER_LENGTH = 16
    private static final int ZIP64_COUNT = 0xFFFF
    private static final long ZIP64 = 0xFFFFFFFFL
    private static final int ZIP64_EXTRA = 1
    // the extra fields that say nothing of the name or the content of an entry: the mark of a jar, and
    // the times and the owner of a file as the JDK and the zip tools write them
    private static final Set<Integer> EXTRAS_READ = Set.of(0xCAFE, 0x5455, 0x000A, 0x5855, 0x7855, 0x7875)
    private static final int NEWEST_VERSION = 20
    private static final int ENCRYPTED = 1
    private static final int SIZES_AFTER = 8
    private static final int UTF8 = 0x800
    // the options of deflate, the sizes after the entry, the name in UTF-8
    private static final int FLAGS_READ = 0x6 | SIZES_AFTER | UTF8
    private static final int STORED = 0
    private static final int DEFLATED = 8
    private static final int SLASH = 0x2F

    private final byte[] bytes
    private final int count
    private final int[] nameAt
    private final int[] nameLength
    private final int[] dataAt
    private final int[] compressedLength
    private final int[] contentLength
    private final boolean[] deflated
    // the entries in the order of their names, for which a slash comes before every other character
    private final Integer[] byName

    /**
     * @param bytes holds the jar from its start
     * @param length how many of the bytes are the jar
     * @throws NotRead when the bytes are not a jar in the form that is read, with what is wrong
     */
    @SuppressWarnings(['CyclomaticComplexity', 'AbcMetric', 'MethodSize'])
    PackJar(byte[] bytes, int length) throws NotRead {
        this.bytes = bytes
        int end = length - END_LENGTH
        if (length > bytes.length || end < 0 || u32(end) != END_SIGNATURE) {
            throw new NotRead('it does not end with the end record of a jar; a jar with a comment or with bytes after it is not read')
        }
        if (u16(end + 20) != 0) {
            throw new NotRead('its end record says a comment follows it')
        }
        count = u16(end + 10)
        long size = u32(end + 12)
        long start = u32(end + 16)
        if (u16(end + 4) != 0 || u16(end + 6) != 0) {
            throw new NotRead('it is one of several disks')
        }
        if (u16(end + 8) != count) {
            throw new NotRead('its end record gives two counts of its entries')
        }
        if (count == ZIP64_COUNT || size == ZIP64 || start == ZIP64) {
            throw new NotRead('it is in the ZIP64 form')
        }
        if (start + size != end) {
            throw new NotRead('its directory is not where its end record says: there are bytes in front of the jar or between its ' +
                    'directory and its end, or the directory has another length')
        }
        nameAt = new int[count]
        nameLength = new int[count]
        dataAt = new int[count]
        compressedLength = new int[count]
        contentLength = new int[count]
        deflated = new boolean[count]
        Inflater inflater = new Inflater(true)
        try {
            Checks checks = new Checks(inflater)
            int at = (int) start
            // where the entry has to begin: where the one before it ended
            int local = 0
            long names = 0
            long content = 0
            for (int index = 0; index < count; index++) {
                String entry = "entry ${index + 1} of its directory"
                if (at + CENTRAL_LENGTH > end || u32(at) != CENTRAL_SIGNATURE) {
                    throw new NotRead("its directory has fewer entries than the ${count} its end record counts")
                }
                int flags = u16(at + 8)
                int method = u16(at + 10)
                long crc = u32(at + 16)
                long compressed = u32(at + 20)
                long plain = u32(at + 24)
                int nameBytes = u16(at + 28)
                int extraBytes = u16(at + 30)
                long offset = u32(at + 42)
                int name = at + CENTRAL_LENGTH
                int next = name + nameBytes + extraBytes
                checkHeader(entry, u16(at + 6), flags, method)
                if (compressed == ZIP64 || plain == ZIP64 || offset == ZIP64) {
                    throw new NotRead("${entry} is in the ZIP64 form")
                }
                if (u16(at + 34) != 0) {
                    throw new NotRead("${entry} is on another disk")
                }
                if (u16(at + 32) != 0) {
                    throw new NotRead("${entry} has a comment")
                }
                if (next > end) {
                    throw new NotRead("${entry} does not end in the directory")
                }
                names += nameBytes
                if (names > MAX_NAME_BYTES) {
                    throw new NotRead("the names of its entries have more than the ${MAX_NAME_BYTES} bytes they can have together")
                }
                boolean directory = checks.name(entry, name, nameBytes, flags)
                checkExtra(entry, next - extraBytes, extraBytes)
                if (method == STORED && (compressed != plain || (flags & SIZES_AFTER) != 0)) {
                    throw new NotRead("${entry} is stored and has two lengths, or has them after it")
                }
                if (directory && plain != 0) {
                    throw new NotRead("${entry} is a directory with content")
                }
                content += plain
                if (content > MAX_CONTENT_BYTES) {
                    throw new NotRead("its entries have more than the ${MAX_CONTENT_BYTES} bytes they can have together")
                }
                if (offset != local) {
                    throw new NotRead("${entry} does not begin where the one before it ends: there are bytes that are of no entry, " +
                            'or entries over one another or in another order than the directory has them')
                }
                int headerEnd = local + LOCAL_LENGTH
                if (headerEnd > start || u32(local) != LOCAL_SIGNATURE) {
                    throw new NotRead("${entry} has no header in front of it")
                }
                int localExtra = u16(local + 28)
                long data = (long) headerEnd + nameBytes + localExtra
                boolean after = (flags & SIZES_AFTER) != 0
                long entryEnd = data + compressed + (after ? AFTER_LENGTH : 0)
                if (entryEnd > start) {
                    throw new NotRead("${entry} does not end before the directory")
                }
                checkHeader("the header of ${entry}", u16(local + 4), u16(local + 6), u16(local + 8))
                if (u16(local + 6) != flags || u16(local + 8) != method) {
                    throw new NotRead("the header of ${entry} has other flags or another way of compressing than the directory")
                }
                if (u16(local + 26) != nameBytes || !Arrays.equals(bytes, headerEnd, headerEnd + nameBytes, bytes, name, name + nameBytes)) {
                    throw new NotRead("the header of ${entry} has another name than the directory")
                }
                checkExtra("the header of ${entry}", headerEnd + nameBytes, localExtra)
                boolean said = u32(local + 14) == crc && u32(local + 18) == compressed && u32(local + 22) == plain
                // a jar that was written to a stream has them after the entry, and none in front of it: the
                // header was written before the entry was known. Some tools write them in both places
                boolean left = after && u32(local + 14) == 0 && u32(local + 18) == 0 && u32(local + 22) == 0
                if (!said && !left) {
                    throw new NotRead("the header of ${entry} has another checksum or other lengths than the directory")
                }
                int tail = (int) (data + compressed)
                if (after && (u32(tail) != AFTER_SIGNATURE || u32(tail + 4) != crc || u32(tail + 8) != compressed || u32(tail + 12) != plain)) {
                    throw new NotRead("what follows ${entry} has another checksum or other lengths than the directory, or is not there")
                }
                checks.content(entry, (int) data, (int) compressed, (int) plain, crc, method == DEFLATED)
                nameAt[index] = name
                nameLength[index] = nameBytes
                dataAt[index] = (int) data
                compressedLength[index] = (int) compressed
                contentLength[index] = (int) plain
                deflated[index] = method == DEFLATED
                local = (int) entryEnd
                at = next
            }
            if (at != end) {
                throw new NotRead("its directory has more in it than the ${count} entries its end record counts")
            }
            if (local != start) {
                throw new NotRead('there are bytes between its last entry and its directory')
            }
            checkNoEnd((int) start, end)
        } finally {
            inflater.end()
        }
        byName = new Integer[count]
        for (int index = 0; index < count; index++) {
            byName[index] = index
        }
        Arrays.sort(byName, { Integer first, Integer second ->
            compare(nameAt[first], nameLength[first], bytes, nameAt[second], nameLength[second])
        } as Comparator<Integer>)
        for (int index = 1; index < count; index++) {
            int first = byName[index - 1]
            int second = byName[index]
            int firstLength = nameLength[first]
            int secondLength = nameLength[second]
            boolean begins = secondLength >= firstLength &&
                    Arrays.equals(bytes, nameAt[first], nameAt[first] + firstLength, bytes, nameAt[second], nameAt[second] + firstLength)
            if (begins && secondLength == firstLength) {
                throw new NotRead("entries ${Math.min(first, second) + 1} and ${Math.max(first, second) + 1} of its directory have one name")
            }
            // the names are in an order in which what is below a name follows it directly
            if (begins && bytes[nameAt[first] + firstLength - 1] != SLASH && bytes[nameAt[second] + firstLength] == SLASH) {
                throw new NotRead("entry ${first + 1} of its directory is a file, and entry ${second + 1} has it for a directory")
            }
        }
    }

    /**
     * @return whether the jar has an entry of exactly this name that is a file
     */
    boolean hasFile(String name) {
        return !name.endsWith('/') && find(name) >= 0
    }

    /**
     * @return the content of the entry of this name, or {@code null} when it has more than the limit
     * @throws NotRead when the jar has no such entry
     */
    byte[] read(String name, long limit) throws NotRead {
        int index = find(name)
        if (index < 0) {
            throw new NotRead('it has no such entry')
        }
        int length = contentLength[index]
        if (length > limit) {
            return null
        }
        if (!deflated[index]) {
            return Arrays.copyOfRange(bytes, dataAt[index], dataAt[index] + length)
        }
        // it was inflated once already, and is what it was then
        byte[] content = new byte[length]
        Inflater inflater = new Inflater(true)
        try {
            inflater.setInput(bytes, dataAt[index], compressedLength[index])
            int done = 0
            while (done < length) {
                int more = inflater.inflate(content, done, length - done)
                if (more == 0) {
                    throw new NotRead('an entry changed while it was read')
                }
                done += more
            }
        } catch (DataFormatException ignored) {
            throw new NotRead('an entry changed while it was read')
        } finally {
            inflater.end()
        }
        return content
    }

    private int find(String name) {
        byte[] sought = name.getBytes(StandardCharsets.UTF_8)
        int low = 0
        int high = count - 1
        while (low <= high) {
            int middle = (low + high) >>> 1
            int entry = byName[middle]
            int order = compare(nameAt[entry], nameLength[entry], sought, 0, sought.length)
            if (order == 0) {
                return entry
            }
            if (order < 0) {
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return -1
    }

    private int compare(int first, int firstLength, byte[] other, int second, int secondLength) {
        int shared = Math.min(firstLength, secondLength)
        for (int index = 0; index < shared; index++) {
            int a = bytes[first + index] & 0xFF
            int b = other[second + index] & 0xFF
            if (a != b) {
                // no name has a character below a space, so a slash made zero comes before all of them
                return (a == SLASH ? 0 : a) - (b == SLASH ? 0 : b)
            }
        }
        return firstLength - secondLength
    }

    private static void checkHeader(String what, int version, int flags, int method) throws NotRead {
        if ((flags & ENCRYPTED) != 0) {
            throw new NotRead("${what} is encrypted")
        }
        if ((flags & ~FLAGS_READ) != 0) {
            throw new NotRead("${what} has flags a jar has no use for")
        }
        if (method != STORED && method != DEFLATED) {
            throw new NotRead("${what} is compressed in another way than deflate")
        }
        if (version > NEWEST_VERSION) {
            throw new NotRead("${what} asks for more than a jar has")
        }
    }

    private void checkExtra(String what, int from, int length) throws NotRead {
        int at = from
        int end = from + length
        while (at < end) {
            if (at + 4 > end || at + 4 + u16(at + 2) > end) {
                throw new NotRead("${what} has extra fields that are not whole")
            }
            int kind = u16(at)
            if (kind == ZIP64_EXTRA) {
                throw new NotRead("${what} has an extra field of the ZIP64 form")
            }
            if (!EXTRAS_READ.contains(kind)) {
                // one of them is another name for the entry, which some programs take and others do not
                throw new NotRead("${what} has an extra field of a kind a jar has no use for (0x${Integer.toHexString(kind)})")
            }
            at += 4 + u16(at + 2)
        }
    }

    /**
     * Refuses a directory with the mark of an end record in it. Whoever reads a jar from its end looks
     * in front of the end record for the one of ZIP64, and takes the directory that one names.
     */
    private void checkNoEnd(int from, int to) throws NotRead {
        for (int at = from; at + 4 <= to; at++) {
            if (bytes[at] == (byte) 0x50 && bytes[at + 1] == (byte) 0x4B) {
                int third = bytes[at + 2]
                int fourth = bytes[at + 3]
                if ((third == 5 && fourth == 6) || (third == 6 && (fourth == 6 || fourth == 7))) {
                    throw new NotRead('its directory has the mark of a second end record in it')
                }
            }
        }
    }

    private int u16(int at) {
        return (bytes[at] & 0xFF) | ((bytes[at + 1] & 0xFF) << 8)
    }

    private long u32(int at) {
        return (bytes[at] & 0xFFL) | ((bytes[at + 1] & 0xFFL) << 8) | ((bytes[at + 2] & 0xFFL) << 16) | ((bytes[at + 3] & 0xFFL) << 24)
    }

    /**
     * The checks of an entry that need something to work with, which is made once for a jar.
     */
    private final class Checks {

        private final Inflater inflater
        private final CRC32 checksum = new CRC32()
        private final byte[] buffer = new byte[64 * 1024]
        private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        // as many characters as a name can have bytes
        private CharBuffer characters

        Checks(Inflater inflater) {
            this.inflater = inflater
        }

        /**
         * @return whether the name is that of a directory
         */
        @SuppressWarnings('CyclomaticComplexity')
        boolean name(String entry, int from, int length, int flags) throws NotRead {
            if (length == 0) {
                throw new NotRead("${entry} has no name")
            }
            boolean ascii = true
            int step = from
            for (int at = from; at <= from + length; at++) {
                int b = at < from + length ? bytes[at] & 0xFF : SLASH
                if (b < 0x20 || b == 0x7F) {
                    throw new NotRead("${entry} has a control character in its name")
                }
                if (b == 0x5C) {
                    throw new NotRead("${entry} has a backslash in its name")
                }
                ascii &= b < 0x80
                if (b == SLASH) {
                    int stepLength = at - step
                    boolean dots = stepLength > 0 && stepLength <= 2 && bytes[step] == (byte) 0x2E && bytes[at - 1] == (byte) 0x2E
                    // the name of a directory ends with a slash, which is no empty step
                    if (dots || (stepLength == 0 && (at < from + length || length == 1))) {
                        throw new NotRead("${entry} has a name that is an absolute path, or that has an empty step, '.' or '..'")
                    }
                    step = at + 1
                }
            }
            if (!ascii) {
                if ((flags & UTF8) == 0) {
                    throw new NotRead("${entry} has a name that is not ASCII and is not said to be UTF-8")
                }
                if (characters == null) {
                    characters = CharBuffer.allocate(0xFFFF)
                }
                characters.clear()
                decoder.reset()
                if (decoder.decode(ByteBuffer.wrap(bytes, from, length), characters, true).isError() || decoder.flush(characters).isError()) {
                    throw new NotRead("${entry} has a name that is not UTF-8")
                }
            }
            return bytes[from + length - 1] == (byte) SLASH
        }

        void content(String entry, int from, int compressed, int plain, long crc, boolean deflate) throws NotRead {
            checksum.reset()
            if (!deflate) {
                checksum.update(bytes, from, compressed)
            } else {
                inflater.reset()
                inflater.setInput(bytes, from, compressed)
                long written = 0
                try {
                    while (!inflater.finished()) {
                        int more = inflater.inflate(buffer)
                        written += more
                        // nothing more without more to read or a dictionary: the entry does not end in itself
                        if ((more == 0 && !inflater.finished()) || written > plain) {
                            throw new NotRead("${entry} does not have the content the directory says: it is longer or does not end")
                        }
                        checksum.update(buffer, 0, more)
                    }
                } catch (DataFormatException ignored) {
                    throw new NotRead("${entry} is not deflated as it says")
                }
                // read from its start, the entry ends where the deflated bytes end, and not where the directory says
                if (inflater.bytesRead != compressed || written != plain) {
                    throw new NotRead("${entry} does not have the content the directory says: it ends before its length")
                }
            }
            if (checksum.value != crc) {
                throw new NotRead("${entry} does not have the CRC-32 the directory says")
            }
        }
    }

    /**
     * The bytes are not a jar in the form that is read. The message says what is wrong, in words that
     * have nothing of the jar in them but numbers.
     */
    static final class NotRead extends IOException {

        NotRead(String message) {
            super(message)
        }
    }
}
