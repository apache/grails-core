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
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.apache.grails.forge.core.pack.PackDescriptor.PackFile

/**
 * Reads a feature pack from where it is kept.
 *
 * @since 9.0
 */
@CompileStatic
class PackLoader {

    /** The most bytes one file of a pack can have */
    public static final int MAX_FILE_BYTES = 1024 * 1024

    /** The most bytes the files of a pack can have together */
    public static final int MAX_PACK_BYTES = 16 * 1024 * 1024

    // a character of a descriptor is at most three bytes of UTF-8, or four for the two characters of a pair
    private static final long MAX_DESCRIPTOR_BYTES = PackDescriptorParser.MAX_LENGTH * 3L

    private final PackDescriptorParser parser = new PackDescriptorParser()

    /**
     * Reads the pack of a directory: its {@code feature.yml}, and under {@code templates} the files the
     * descriptor names. This is how a pack is tried out before it is published.
     *
     * <p>The pack is read once, here: the content of its files is kept as it is now, so what is written
     * to an application later is what was checked. The descriptor, {@code templates} and every file and
     * directory on the way to a file the descriptor names have to be what they look like, not links, so
     * a pack cannot name a file outside itself; a file that has a second name somewhere else (a hard
     * link) is refused where the file system can tell. A file whose name ends with {@code .gsp} is a
     * template, see {@link PackPages}, and has to be text in UTF-8.</p>
     *
     * @param directory the directory of the pack
     * @return the pack
     * @throws PackDescriptorException when the directory holds no pack that can be used
     */
    Pack fromDirectory(Path directory) {
        String origin = directory.toString()
        try {
            return read(directory, origin)
        } catch (IOException | InvalidPathException e) {
            throw new PackDescriptorException(origin, ["It cannot be read: ${e.class.simpleName}".toString()])
        }
    }

    /**
     * Reads the pack a jar holds under {@code META-INF/grails-forge}, which is where the Grails Gradle
     * plugin puts the pack of a plugin: its {@code feature.yml}, and under {@code templates} the files
     * the descriptor names. Nothing else of the jar is read and nothing of it is loaded as code here.
     *
     * <p>The limits are those of a pack in a directory, and they are on what is read, not on the sizes
     * the jar gives for its entries. A jar with two entries of one name for a file of the pack is refused.
     * The signature of a signed jar is not checked.</p>
     *
     * @param jar the jar
     * @return the pack
     * @throws PackDescriptorException when the jar holds no pack that can be used
     */
    Pack fromJar(Path jar) {
        return fromJar(jar, jar.toString())
    }

    /**
     * Reads the pack of a jar as {@link #fromJar(Path)} does, for a jar that is a copy of what the pack
     * is known by.
     *
     * @param jar the jar
     * @param origin where the pack came from, for messages, such as the coordinates of the plugin
     * @return the pack
     * @throws PackDescriptorException when the jar holds no pack that can be used
     */
    Pack fromJar(Path jar, String origin) {
        if (!Files.isRegularFile(jar)) {
            throw new PackDescriptorException(origin, ['It is not a file'])
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return read(new FileJar(zip), origin)
        } catch (IOException | InvalidPathException | IllegalArgumentException | UnsupportedOperationException e) {
            throw new PackDescriptorException(origin, ["It cannot be read as a jar: ${e.class.simpleName}".toString()])
        }
    }

    /**
     * Reads the pack of a jar as {@link #fromJar(Path)} does, from the bytes of the jar and not from a
     * file of them: the pack that is read is then one of exactly the bytes whoever calls this has, which
     * no file between the two can be changed in. The same entries are read under the same limits, and
     * a jar the other way of reading refuses is refused.
     *
     * @param jar holds the bytes of the jar from its start
     * @param length how many of them are the jar
     * @param origin where the pack came from, for messages, such as the coordinates of the plugin
     * @return the pack
     * @throws PackDescriptorException when the bytes hold no pack that can be used
     */
    Pack fromJarBytes(byte[] jar, int length, String origin) {
        try {
            return read(new MemoryJar(jar, length), origin)
        } catch (PackDescriptorException e) {
            throw e
        } catch (IOException | RuntimeException e) {
            throw new PackDescriptorException(origin, ["It cannot be read as a jar: ${e.class.simpleName}".toString()])
        }
    }

    /**
     * Reads a pack Forge has in itself, from the directory of its name under
     * {@code META-INF/grails-forge-bundled} of the resources.
     *
     * @param loader the class loader that has the resources of Forge
     * @param name the name of the pack, which its descriptor has to have too
     * @return the pack
     * @throws PackDescriptorException when there is no such pack or it cannot be used
     */
    Pack fromClasspath(ClassLoader loader, String name) {
        String origin = "the features of Forge (${name})"
        String base = "${Pack.BUNDLED_DIRECTORY}/${name}/"
        byte[] descriptorBytes = resource(loader, base + Pack.DESCRIPTOR, MAX_DESCRIPTOR_BYTES)
        if (descriptorBytes == null) {
            throw new PackDescriptorException(origin, ["There is no ${base}${Pack.DESCRIPTOR}, or it is larger than a descriptor can be".toString()])
        }
        PackDescriptor descriptor = parser.parseOfForge(text(descriptorBytes, origin), origin)
        List<String> problems = []
        if (descriptor.name != name) {
            problems << "name: is ${descriptor.name}, and the pack is in the directory ${name}".toString()
        }
        Map<String, byte[]> files = new LinkedHashMap<>()
        descriptor.files.eachWithIndex { PackFile file, int index ->
            byte[] content = resource(loader, "${base}${Pack.TEMPLATES}/${file.from}", MAX_FILE_BYTES)
            if (content == null) {
                problems << "files[${index}].from: '${file.from}' is not a resource under ${base}${Pack.TEMPLATES}, or is larger than a file can be".toString()
            } else if (Pack.isTemplate(file.from) && !isText(content)) {
                problems << "files[${index}].from: '${file.from}' is a template and is not text in UTF-8".toString()
            } else {
                files.put(file.from, content)
            }
        }
        if (problems) {
            throw new PackDescriptorException(origin, problems)
        }
        return new Pack(descriptor, origin, files, true)
    }

    private static byte[] resource(ClassLoader loader, String path, long limit) {
        InputStream input = loader.getResourceAsStream(path)
        if (input == null) {
            return null
        }
        try (InputStream stream = input) {
            byte[] bytes = stream.readNBytes((int) limit + 1)
            return bytes.length > limit ? null : bytes
        }
    }

    private Pack read(Jar jar, String origin) {
        String descriptorPath = "${Pack.JAR_DIRECTORY}/${Pack.DESCRIPTOR}"
        if (!jar.hasFile(descriptorPath)) {
            throw new PackDescriptorException(origin, ["There is no ${Pack.JAR_DIRECTORY}/${Pack.DESCRIPTOR} in the jar".toString()])
        }
        // a jar can hold two entries of one name, and which of them is read is not for the jar to choose
        Set<String> repeated = jar.repeated(Pack.JAR_DIRECTORY + '/')
        if (repeated.contains(descriptorPath)) {
            throw new PackDescriptorException(origin, ["${Pack.JAR_DIRECTORY}/${Pack.DESCRIPTOR} is in the jar more than once".toString()])
        }
        byte[] descriptorBytes = jar.read(descriptorPath, MAX_DESCRIPTOR_BYTES)
        if (descriptorBytes == null) {
            throw new PackDescriptorException(origin, ["${Pack.DESCRIPTOR} is larger than a descriptor can be".toString()])
        }
        PackDescriptor descriptor = parser.parse(text(descriptorBytes, origin), origin)
        List<String> problems = []
        Map<String, byte[]> files = new LinkedHashMap<>()
        long total = 0
        descriptor.files.eachWithIndex { PackFile file, int index ->
            String at = "files[${index}].from: '${file.from}'"
            // enough was said, or the pack is too large whatever the rest of it is
            if (problems.size() >= PackDescriptorParser.MAX_PROBLEMS || total > MAX_PACK_BYTES) {
                return
            }
            String path = "${Pack.JAR_DIRECTORY}/${Pack.TEMPLATES}/${file.from}"
            if (repeated.contains(path)) {
                problems << "${at} is in the jar more than once".toString()
                return
            }
            if (!jar.hasFile(path)) {
                problems << "${at} is not a file under ${Pack.JAR_DIRECTORY}/${Pack.TEMPLATES} of the jar".toString()
                return
            }
            byte[] content = jar.read(path, MAX_FILE_BYTES)
            total += content == null ? 0 : content.length
            if (content == null) {
                problems << "${at} has more than the ${MAX_FILE_BYTES} bytes a file of a pack can have".toString()
            } else if (total > MAX_PACK_BYTES) {
                problems << "${at} takes the files of the pack over the ${MAX_PACK_BYTES} bytes they can have together".toString()
            } else if (Pack.isTemplate(file.from) && !isText(content)) {
                problems << "${at} is a template and is not text in UTF-8".toString()
            } else {
                files.put(file.from, content)
            }
        }
        if (problems) {
            throw new PackDescriptorException(origin, problems)
        }
        return new Pack(descriptor, origin, files)
    }

    /**
     * What is asked of a jar to read a pack from it, whether the jar is a file or bytes that are held.
     */
    private interface Jar {

        /**
         * @return whether the jar has an entry of exactly this name that is a file
         */
        boolean hasFile(String name)

        /**
         * @return the names that begin so and that more than one entry of the jar has
         */
        Set<String> repeated(String prefix)

        /**
         * @return the bytes of the entry, or {@code null} when it has more than the limit, however
         * large the jar says it is
         */
        byte[] read(String name, long limit) throws IOException
    }

    private static final class FileJar implements Jar {

        private final ZipFile zip

        FileJar(ZipFile zip) {
            this.zip = zip
        }

        @Override
        boolean hasFile(String name) {
            ZipEntry entry = zip.getEntry(name)
            return entry != null && !entry.isDirectory()
        }

        @Override
        Set<String> repeated(String prefix) {
            Set<String> seen = new HashSet<>()
            Set<String> repeated = new HashSet<>()
            Enumeration<? extends ZipEntry> entries = zip.entries()
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().name
                if (name.startsWith(prefix) && !seen.add(name)) {
                    repeated << name
                }
            }
            return repeated
        }

        @Override
        byte[] read(String name, long limit) throws IOException {
            try (InputStream input = zip.getInputStream(zip.getEntry(name))) {
                byte[] bytes = input.readNBytes((int) limit + 1)
                return bytes.length > limit ? null : bytes
            }
        }
    }

    /**
     * A jar that is bytes in memory, read the way a jar in a file is: by the directory at its end, which
     * names each entry once and says where it is. The JDK reads a jar that way only from a file. What it
     * would refuse is refused here, and so is what it has other ways for that a jar of a plugin does not
     * need: more than 65534 entries or 4 GiB (ZIP64), several disks, encryption, and a way of
     * compressing other than deflate.
     */
    private static final class MemoryJar implements Jar {

        private static final long END_SIGNATURE = 0x06054b50L
        private static final long CENTRAL_SIGNATURE = 0x02014b50L
        private static final long LOCAL_SIGNATURE = 0x04034b50L
        private static final int END_LENGTH = 22
        private static final int CENTRAL_LENGTH = 46
        private static final int LOCAL_LENGTH = 30
        private static final int MAX_COMMENT = 0xFFFF
        private static final long ZIP64 = 0xFFFFFFFFL
        private static final int ENCRYPTED = 1
        private static final int STORED = 0
        private static final int DEFLATED = 8

        private final byte[] bytes
        private final int length
        // of each name: the flags, the way it is compressed, its length as it is in the jar, and where it begins
        private final Map<String, long[]> entries = new HashMap<>()
        private final Set<String> twice = new HashSet<>()
        private long shift

        MemoryJar(byte[] bytes, int length) throws IOException {
            this.bytes = bytes
            this.length = length
            int end = -1
            for (int at = length - END_LENGTH; at >= 0 && at >= length - END_LENGTH - MAX_COMMENT; at--) {
                // the end of the directory is where what it says of its comment takes it to the end of the jar
                if (u32(at) == END_SIGNATURE && at + END_LENGTH + u16(at + 20) == length) {
                    end = at
                    break
                }
            }
            if (end < 0) {
                throw new IOException('no directory')
            }
            int count = u16(end + 10)
            long size = u32(end + 12)
            long start = u32(end + 16)
            if (u16(end + 4) != 0 || u16(end + 6) != 0 || u16(end + 8) != count || count == MAX_COMMENT || size == ZIP64 || start == ZIP64) {
                throw new IOException('several disks or ZIP64')
            }
            // as the JDK reads it: the directory ends where its end is, and what is before the jar in
            // the file moves every place the directory names
            shift = end - size - start
            if (size > end || shift < 0) {
                throw new IOException('directory out of the jar')
            }
            int at = (int) (end - size)
            for (int index = 0; index < count; index++) {
                if (at + CENTRAL_LENGTH > end || u32(at) != CENTRAL_SIGNATURE) {
                    throw new IOException('directory entry')
                }
                int nameLength = u16(at + 28)
                int next = at + CENTRAL_LENGTH + nameLength + u16(at + 30) + u16(at + 32)
                if (next > end) {
                    throw new IOException('directory entry out of the directory')
                }
                String name = new String(bytes, at + CENTRAL_LENGTH, nameLength, StandardCharsets.UTF_8)
                if (entries.put(name, [u16(at + 8), u16(at + 10), u32(at + 20), u32(at + 42)] as long[]) != null) {
                    twice << name
                }
                at = next
            }
        }

        @Override
        boolean hasFile(String name) {
            return !name.endsWith('/') && entries.containsKey(name)
        }

        @Override
        Set<String> repeated(String prefix) {
            return twice.findAll { String name -> name.startsWith(prefix) } as Set<String>
        }

        @Override
        byte[] read(String name, long limit) throws IOException {
            long[] entry = entries.get(name)
            if (entry == null) {
                throw new IOException('no such entry')
            }
            long compressed = entry[2]
            if ((entry[0] & ENCRYPTED) != 0 || compressed == ZIP64 || entry[3] == ZIP64) {
                throw new IOException('encrypted or ZIP64 entry')
            }
            long local = entry[3] + shift
            if (local + LOCAL_LENGTH > length || u32((int) local) != LOCAL_SIGNATURE) {
                throw new IOException('entry out of the jar')
            }
            long data = local + LOCAL_LENGTH + u16((int) local + 26) + u16((int) local + 28)
            if (data + compressed > length) {
                throw new IOException('entry out of the jar')
            }
            if (entry[1] == STORED) {
                return compressed > limit ? null : Arrays.copyOfRange(bytes, (int) data, (int) (data + compressed))
            }
            if (entry[1] != DEFLATED) {
                throw new IOException('way of compressing')
            }
            Inflater inflater = new Inflater(true)
            try (InputStream input = new InflaterInputStream(new ByteArrayInputStream(bytes, (int) data, (int) compressed), inflater)) {
                byte[] content = input.readNBytes((int) limit + 1)
                return content.length > limit ? null : content
            } finally {
                inflater.end()
            }
        }

        private int u16(int at) throws IOException {
            if (at < 0 || at + 2 > length) {
                throw new IOException('out of the jar')
            }
            return (bytes[at] & 0xFF) | ((bytes[at + 1] & 0xFF) << 8)
        }

        private long u32(int at) throws IOException {
            if (at < 0 || at + 4 > length) {
                throw new IOException('out of the jar')
            }
            return (bytes[at] & 0xFFL) | ((bytes[at + 1] & 0xFFL) << 8) | ((bytes[at + 2] & 0xFFL) << 16) | ((bytes[at + 3] & 0xFFL) << 24)
        }
    }

    private Pack read(Path directory, String origin) {
        Path descriptorFile = directory.resolve(Pack.DESCRIPTOR)
        if (!Files.isRegularFile(descriptorFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new PackDescriptorException(origin, ["There is no ${Pack.DESCRIPTOR} in the directory, or it is not a file".toString()])
        }
        byte[] descriptorBytes = read(descriptorFile, MAX_DESCRIPTOR_BYTES)
        if (descriptorBytes == null) {
            throw new PackDescriptorException(origin, ["${Pack.DESCRIPTOR} is larger than a descriptor can be".toString()])
        }
        PackDescriptor descriptor = parser.parse(text(descriptorBytes, origin), origin)
        Path templates = directory.resolve(Pack.TEMPLATES)
        List<String> problems = []
        Map<String, byte[]> files = new LinkedHashMap<>()
        long total = 0
        descriptor.files.eachWithIndex { PackFile file, int index ->
            String at = "files[${index}].from: '${file.from}'"
            // enough was said, or the pack is too large whatever the rest of it is
            if (problems.size() >= PackDescriptorParser.MAX_PROBLEMS || total > MAX_PACK_BYTES) {
                return
            }
            if (!isPlainFileBelow(templates, file.from)) {
                problems << "${at} is not a file under ${Pack.TEMPLATES} of the pack, or is a link or is reached through one".toString()
                return
            }
            byte[] content = read(templates.resolve(file.from), MAX_FILE_BYTES)
            // what was read is only the file that was checked if nothing on the way to it was changed
            // into a link meanwhile, which is looked for again; it narrows that chance and does not end it
            if (!isPlainFileBelow(templates, file.from)) {
                problems << "${at} changed while the pack was read".toString()
                return
            }
            total += content == null ? 0 : content.length
            if (content == null) {
                problems << "${at} has more than the ${MAX_FILE_BYTES} bytes a file of a pack can have".toString()
            } else if (total > MAX_PACK_BYTES) {
                problems << "${at} takes the files of the pack over the ${MAX_PACK_BYTES} bytes they can have together".toString()
            } else if (Pack.isTemplate(file.from) && !isText(content)) {
                problems << "${at} is a template and is not text in UTF-8".toString()
            } else {
                files.put(file.from, content)
            }
        }
        if (problems) {
            throw new PackDescriptorException(origin, problems)
        }
        return new Pack(descriptor, origin, files)
    }

    /**
     * Whether the path names a file below the directory with nothing but real directories on the way:
     * neither the directory, nor a step of the path, nor the file is a link, and the file has no other
     * name where the file system counts the names of a file.
     */
    private static boolean isPlainFileBelow(Path directory, String path) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return false
        }
        Path current = directory
        List<String> steps = path.split('/').toList()
        for (int index = 0; index < steps.size(); index++) {
            current = current.resolve(steps[index])
            boolean last = index == steps.size() - 1
            if (last ? !Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS) : !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                return false
            }
        }
        try {
            Object names = Files.getAttribute(current, 'unix:nlink', LinkOption.NOFOLLOW_LINKS)
            return !(names instanceof Number) || ((Number) names).intValue() == 1
        } catch (UnsupportedOperationException | IllegalArgumentException ignored) {
            // a file system that does not count the names of a file
            return true
        }
    }

    /**
     * The bytes of a file, or {@code null} when it has more than the limit, however large the file says
     * it is.
     */
    private static byte[] read(Path file, long limit) {
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes((int) limit + 1)
            return bytes.length > limit ? null : bytes
        }
    }

    private static String text(byte[] bytes, String origin) {
        try {
            return decode(bytes)
        } catch (CharacterCodingException ignored) {
            throw new PackDescriptorException(origin, ["${Pack.DESCRIPTOR} is not text in UTF-8".toString()])
        }
    }

    private static boolean isText(byte[] bytes) {
        try {
            decode(bytes)
            return true
        } catch (CharacterCodingException ignored) {
            return false
        }
    }

    private static String decode(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
    }
}
