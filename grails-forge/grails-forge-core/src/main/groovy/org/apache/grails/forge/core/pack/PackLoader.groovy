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

    /** The suffix of a file of a pack that is a template and not a file copied as it is */
    public static final String TEMPLATE_SUFFIX = '.gsp'

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
     * link) is refused where the file system can tell. Templates are not read by this version of Forge:
     * a file whose name ends with {@code .gsp} is refused.</p>
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
            if (file.from.toLowerCase(Locale.ROOT).endsWith(TEMPLATE_SUFFIX)) {
                problems << "${at} is a template, and this version of Forge reads only files that are copied as they are".toString()
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
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
        } catch (CharacterCodingException ignored) {
            throw new PackDescriptorException(origin, ["${Pack.DESCRIPTOR} is not text in UTF-8".toString()])
        }
    }
}
