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

    /** The most bytes a jar can have that a pack is read from */
    public static final int MAX_JAR_BYTES = 64 * 1024 * 1024

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
     * the descriptor names. Nothing of the jar is loaded as code here.
     *
     * <p>The jar is read as {@link #fromJarBytes(byte[], int, String)} reads its bytes, and is taken
     * only in the form that is taken there. The limits of the pack are those of a pack in a directory.
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
        byte[] bytes
        int length
        try (InputStream input = Files.newInputStream(jar)) {
            long size = Files.size(jar)
            if (size > MAX_JAR_BYTES) {
                throw new PackDescriptorException(origin, [tooLarge()])
            }
            // the jar is read once, and what is checked and read of it then is these bytes
            bytes = new byte[(int) size]
            length = input.readNBytes(bytes, 0, bytes.length)
            if (input.read() != -1) {
                throw new PackDescriptorException(origin, ['It cannot be read as a jar: it changed while it was read'])
            }
        } catch (IOException | InvalidPathException | UnsupportedOperationException e) {
            throw new PackDescriptorException(origin, ["It cannot be read: ${e.class.simpleName}".toString()])
        } catch (OutOfMemoryError ignored) {
            throw new PackDescriptorException(origin, ['It cannot be read: it does not fit into the memory there is'])
        }
        return fromJarBytes(bytes, length, origin)
    }

    /**
     * Reads the pack of a jar from the bytes of the jar: the pack that is read is then one of exactly
     * the bytes whoever calls this has, which no file between the two can be changed in.
     *
     * <p>A jar can be made so that two programs find two different jars in the same bytes. The pack has
     * to be the one every program finds, so the bytes are taken only as a jar in the one form that
     * leaves no such choice, which is the form the tools that build a jar write: the entries from the
     * first byte on, one directly after the other, then the directory of exactly these entries in
     * their order, then the end record, with no comment and nothing in front, between or after; no
     * ZIP64, no encryption, no way of compressing but deflate; names in UTF-8 that are relative paths,
     * each once, none a file where another has a directory; the header in front of every entry saying
     * what the directory says; and every entry, of the pack or not, of the length and the CRC-32 the
     * directory gives. Anything else is refused with what is wrong. All of a jar that is taken was
     * checked, and it can have {@link #MAX_JAR_BYTES} bytes, fewer than 65535 entries, 4 MiB of
     * names and 256 MiB of content that is not compressed.</p>
     *
     * @param jar holds the bytes of the jar from its start
     * @param length how many of them are the jar
     * @param origin where the pack came from, for messages, such as the coordinates of the plugin
     * @return the pack
     * @throws PackDescriptorException when the bytes hold no pack that can be used
     */
    Pack fromJarBytes(byte[] jar, int length, String origin) {
        if (length > MAX_JAR_BYTES) {
            throw new PackDescriptorException(origin, [tooLarge()])
        }
        try {
            return read(new PackJar(jar, length), origin)
        } catch (PackJar.NotRead e) {
            throw new PackDescriptorException(origin, ["It cannot be read as a jar: ${e.message}".toString()])
        } catch (PackDescriptorException e) {
            throw e
        } catch (IOException | RuntimeException e) {
            throw new PackDescriptorException(origin, ["It cannot be read as a jar: ${e.class.simpleName}".toString()])
        }
    }

    private static String tooLarge() {
        return "It has more than the ${MAX_JAR_BYTES} bytes a jar of a pack can have".toString()
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
            } else if (file.isTemplate() && !isText(content)) {
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

    private Pack read(PackJar jar, String origin) {
        String descriptorPath = "${Pack.JAR_DIRECTORY}/${Pack.DESCRIPTOR}"
        if (!jar.hasFile(descriptorPath)) {
            throw new PackDescriptorException(origin, ["There is no ${Pack.JAR_DIRECTORY}/${Pack.DESCRIPTOR} in the jar".toString()])
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
            } else if (file.isTemplate() && !isText(content)) {
                problems << "${at} is a template and is not text in UTF-8".toString()
            } else {
                files.put(file.from, content)
            }
        }
        if (problems) {
            throw new PackDescriptorException(origin, problems)
        }
        return new Pack(descriptor, origin, files, descriptorBytes)
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
            } else if (file.isTemplate() && !isText(content)) {
                problems << "${at} is a template and is not text in UTF-8".toString()
            } else {
                files.put(file.from, content)
            }
        }
        if (problems) {
            throw new PackDescriptorException(origin, problems)
        }
        return new Pack(descriptor, origin, files, descriptorBytes)
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
