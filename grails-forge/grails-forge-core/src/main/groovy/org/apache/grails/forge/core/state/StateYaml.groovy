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
import org.apache.grails.forge.core.pack.PackDescriptorParser
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

/**
 * How the files of the state of a project are written and read. They are written the same way every
 * time: block style, the keys in the order they are given, lines that end with a line feed, UTF-8.
 * They are read as what the clone of a project is, text nobody has checked: the lines of a file are
 * counted and measured before any of it is parsed, its mappings, lists and values are counted while
 * it is scanned and before anything is built of them, and it is read without aliases, anchors or
 * tags, to a bounded depth, with every single value as text, which the reader of each file then holds
 * to what it has to be.
 */
@CompileStatic
@PackageScope
final class StateYaml {

    /** The most characters a message repeats of what a file says */
    static final int MAX_ECHO = 80

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
     * @param lines the most lines the file can have
     * @return the mapping the file holds, every single value of it text
     */
    static Map<String, Object> load(byte[] bytes, String file, int lines) {
        return load(bytes, file, lines, StateLimits.MAX_LINE)
    }

    /**
     * @param length the most characters a line, and a single value, of the file can have
     */
    static Map<String, Object> load(byte[] bytes, String file, int lines, int length) {
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
        String refused = refusedAsText(text, lines, length)
        if (refused != null) {
            throw new ProjectStateException("${file} ${refused}")
        }
        Object loaded
        try {
            LoaderOptions options = new LoaderOptions()
            options.setAllowDuplicateKeys(false)
            options.setMaxAliasesForCollections(0)
            options.setAllowRecursiveKeys(false)
            options.setNestingDepthLimit(StateLimits.MAX_DEPTH + 1)
            options.setCodePointLimit(text.length() + 1)
            DumperOptions dumperOptions = new DumperOptions()
            Yaml reader = new Yaml(new SafeConstructor(options), new Representer(dumperOptions), dumperOptions, options, new TextResolver())
            refused = refusedByScan(reader, text, 4 * lines + 64, length)
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

    // The scanner of the YAML library looks ahead in a line without taking what it has seen, and what
    // that costs grows with the square of the length of the line; and every line can open a mapping.
    // Forge writes short lines and a known number of them, so a file with more of either is not one
    // Forge wrote, and it is refused before the library sees it.
    private static String refusedAsText(String text, int lines, int length) {
        int count = 1
        int start = 0
        for (int index = 0; index < text.length(); index++) {
            char each = text.charAt(index)
            if (each == ('\n' as char) || each == ('\r' as char)) {
                start = index + 1
                if (++count > lines) {
                    return "has more than ${lines} lines".toString()
                }
            } else if (index - start >= length) {
                return "has a line of more than ${length} characters".toString()
            }
        }
        return null
    }

    // the files are written without any of these, so one that has them was not written by Forge
    private static String refusedByScan(Yaml reader, String text, int events, int length) {
        // for each mapping or list that is open: whether it is a mapping whose next entry is a key
        // (TRUE), a mapping whose next entry is a value (FALSE), or a list (empty)
        Deque<Optional<Boolean>> open = new ArrayDeque<>()
        int count = 0
        for (Event event : reader.parse(new StringReader(text))) {
            if (++count > events) {
                return 'has more mappings, lists and values than the state of a project has'
            }
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
            if (event instanceof ScalarEvent && ((ScalarEvent) event).getValue().length() > length) {
                return "has a value of more than ${length} characters".toString()
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
                if (open.size() >= StateLimits.MAX_DEPTH) {
                    return "has mappings and lists more than ${StateLimits.MAX_DEPTH} deep".toString()
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
     * not empty, without a character that is not shown or that a terminal acts on, and without the
     * start of the markup of the command line. What is not safe is what it is for a feature pack.
     */
    static boolean isLine(String text, int limit) {
        return text != null && !text.isBlank() && text.length() <= limit && !PackDescriptorParser.hasUnsafeCharacter(text) && !text.contains('@|')
    }

    /**
     * @return the text as a message can repeat it: cut, and with nothing in it that a terminal or the
     * command line would act on
     */
    static String shown(String text, int limit) {
        return PackDescriptorParser.shown(text, limit).replace('@|', '@ |')
    }

    /** Reads every single value as text: what a value has to be is for the reader of the file to say. */
    private static class TextResolver extends Resolver {

        @Override
        protected void addImplicitResolvers() {
        }
    }
}
