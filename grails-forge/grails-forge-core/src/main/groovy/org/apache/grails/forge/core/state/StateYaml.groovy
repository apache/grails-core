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
package org.apache.grails.forge.core.state

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.events.AliasEvent
import org.yaml.snakeyaml.events.CollectionEndEvent
import org.yaml.snakeyaml.events.CollectionStartEvent
import org.yaml.snakeyaml.events.Event
import org.yaml.snakeyaml.events.MappingStartEvent
import org.yaml.snakeyaml.events.NodeEvent
import org.yaml.snakeyaml.events.ScalarEvent
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.regex.Pattern

/**
 * How the files of the state of a project are written and read. They are written the same way every
 * time: block style, the keys in the order they are given, lines that end with a line feed, UTF-8.
 * They are read as what the clone of a project is, text nobody has checked: without aliases, anchors
 * or tags, to a bounded depth, and every single value as text, which the reader of each file then
 * holds to what it has to be.
 */
@CompileStatic
@PackageScope
final class StateYaml {

    static final int MAX_DEPTH = 6

    /** The most characters a message repeats of what a file says */
    static final int MAX_ECHO = 80

    private static final Pattern UNSAFE = Pattern.compile(/[\p{Cc}\p{Cs}\p{Co}\p{Zl}\p{Zp}\x{202A}-\x{202E}\x{2066}-\x{2069}]/)

    private StateYaml() {
    }

    static byte[] dump(Map<String, Object> content) {
        DumperOptions options = new DumperOptions()
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
        options.setLineBreak(DumperOptions.LineBreak.UNIX)
        options.setSplitLines(false)
        options.setIndent(2)
        return new Yaml(options).dump(content).getBytes(StandardCharsets.UTF_8)
    }

    /**
     * @param bytes the content of a file of the state
     * @param file the name of the file, for messages
     * @return the mapping the file holds, every single value of it text
     */
    static Map<String, Object> load(byte[] bytes, String file) {
        String text
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (CharacterCodingException ignored) {
            throw new ProjectStateException("${file} is not text in UTF-8")
        }
        if (text.isBlank()) {
            throw new ProjectStateException("${file} is empty")
        }
        Object loaded
        try {
            LoaderOptions options = new LoaderOptions()
            options.setAllowDuplicateKeys(false)
            options.setMaxAliasesForCollections(0)
            options.setAllowRecursiveKeys(false)
            options.setNestingDepthLimit(MAX_DEPTH + 1)
            options.setCodePointLimit(text.length() + 1)
            DumperOptions dumperOptions = new DumperOptions()
            Yaml reader = new Yaml(new SafeConstructor(options), new Representer(dumperOptions), dumperOptions, options, new TextResolver())
            String refused = refusedByScan(reader, text)
            if (refused != null) {
                throw new ProjectStateException("${file} ${refused}")
            }
            loaded = reader.load(text)
        } catch (ProjectStateException e) {
            throw e
        } catch (RuntimeException e) {
            // SnakeYAML says where in the text it stopped, over several lines
            String reason = (e.message ?: e.class.simpleName).readLines().join(' ')
            throw new ProjectStateException("${file} is not YAML that can be read: ${shown(reason, 200)}")
        }
        if (!(loaded instanceof Map)) {
            throw new ProjectStateException("${file} must be a mapping of keys to values")
        }
        return (Map<String, Object>) loaded
    }

    // the files are written without any of these, so one that has them was not written by Forge
    private static String refusedByScan(Yaml reader, String text) {
        // for each mapping or list that is open: whether it is a mapping whose next entry is a key
        // (TRUE), a mapping whose next entry is a value (FALSE), or a list (empty)
        Deque<Optional<Boolean>> open = new ArrayDeque<>()
        for (Event event : reader.parse(new StringReader(text))) {
            if (event instanceof AliasEvent) {
                return 'repeats a value with an alias (*), which the state of a project does not use'
            }
            if (event instanceof NodeEvent && ((NodeEvent) event).getAnchor() != null) {
                return 'names a value with an anchor (&), which the state of a project does not use'
            }
            String tag = event instanceof ScalarEvent ? ((ScalarEvent) event).getTag() :
                    event instanceof CollectionStartEvent ? ((CollectionStartEvent) event).getTag() : null
            if (tag != null) {
                return "gives a value the tag '${shown(tag, MAX_ECHO)}', and the state of a project does not use tags".toString()
            }
            if (event instanceof CollectionEndEvent) {
                open.pop()
                continue
            }
            if (!(event instanceof NodeEvent)) {
                continue
            }
            boolean key = !open.isEmpty() && open.peek().orElse(null) == Boolean.TRUE
            if (!open.isEmpty() && open.peek().isPresent()) {
                open.push(Optional.of(!open.pop().get()))
            }
            if (event instanceof CollectionStartEvent) {
                if (key) {
                    return 'has a mapping or a list as a key, and a key is text'
                }
                if (open.size() >= MAX_DEPTH) {
                    return "has mappings and lists more than ${MAX_DEPTH} deep".toString()
                }
                open.push(event instanceof MappingStartEvent ? Optional.of(Boolean.TRUE) : Optional.<Boolean>empty())
            }
        }
        return null
    }

    /**
     * @return whether the bytes are text in UTF-8 without a character no text has
     */
    static boolean isText(byte[] bytes) {
        for (byte each : bytes) {
            if (each == (byte) 0) {
                return false
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
            return true
        } catch (CharacterCodingException ignored) {
            return false
        }
    }

    static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(MessageDigest.getInstance('SHA-256').digest(bytes))
    }

    /**
     * @return whether a text is one line that a message can repeat and a file of the state can hold:
     * not empty, without control characters and without the start of the markup of the command line
     */
    static boolean isLine(String text, int limit) {
        return text != null && !text.isBlank() && text.length() <= limit && !UNSAFE.matcher(text).find() && !text.contains('@|')
    }

    /**
     * @return the text as a message can repeat it: cut, and with nothing in it that a terminal or the
     * command line would act on
     */
    static String shown(String text, int limit) {
        String start = text.length() > limit * 2 ? text.substring(0, limit * 2) : text
        String safe = UNSAFE.matcher(start).replaceAll('?').replace('@|', '@ |')
        int count = safe.codePointCount(0, safe.length())
        if (start.length() == text.length() && count <= limit) {
            return safe
        }
        return safe.substring(0, safe.offsetByCodePoints(0, Math.min(limit, count))) + '...'
    }

    /** Reads every single value as text: what a value has to be is for the reader of the file to say. */
    private static class TextResolver extends Resolver {

        @Override
        protected void addImplicitResolvers() {
        }
    }
}
