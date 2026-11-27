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
package org.apache.grails.forge.cli.console

import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Matcher
import java.util.regex.Pattern
import groovy.transform.CompileStatic
import picocli.CommandLine

/**
 * The result a tool of the application or of a plugin gave, besides what it printed: what the tool set
 * as {@code executionContext.result}, as the class that ran it wrote it.
 *
 * <p>The class that runs a tool writes the result, when there is one, to {@code tool-result-<n>.json}
 * in {@code .grails/run} of the project, beside the file of arguments {@code command-args-<n>.json} the
 * tool was started with. The command line removes what has that name before it starts the tool, and
 * after the tool has ended it reads the file, when it is a regular file of the user's own, of at most
 * {@link #MAX_BYTES} bytes, that is JSON in ASCII, and removes it. At the terminal nothing else is done
 * with it; a program that runs a command line in its own process, as {@code grails mcp} does, takes it
 * with {@link #take} when the command line has ended.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ToolResult {

    /** The most bytes a result has: as many as the class that runs a tool writes. */
    public static final int MAX_BYTES = 256 * 1024

    /** How deep the arrays and objects of a result go, at most: as deep as the class that runs a tool writes them. */
    public static final int MAX_DEPTH = 32

    /** What the name of the file of a result starts with, before its number. */
    public static final String PREFIX = 'tool-result-'

    /** What the name of the file of a result ends with. */
    public static final String EXTENSION = '.json'

    private static final String ARGUMENTS_OPTION = '--args-file='
    private static final Pattern ARGUMENTS_NAME = Pattern.compile('command-args-([0-9]{1,9})\\.json')

    // what the tools that ran for a command line gave, by the streams the command line wrote to, for as long as those are there
    private static final Map<CommandStreams, ToolResult> GIVEN = Collections.synchronizedMap(new WeakHashMap<CommandStreams, ToolResult>())

    /**
     * The result: a {@link Map} for an object, in the order of its members, a {@link List} for an
     * array, a {@link String}, a {@link Number} (a {@link Long} for a whole number of up to 18 digits,
     * else a {@link BigDecimal}) or a {@link Boolean}, made of these and {@code null}. Nothing of it is to
     * be changed.
     */
    final Object value

    /** The result as the class that ran the tool wrote it: JSON in ASCII, without white space. */
    final String json

    private ToolResult(Object value, String json) {
        this.value = value
        this.json = json
    }

    /**
     * Takes the result of the tool a command line ran, once: the command line is the one that was run
     * with these streams, as {@code ForgeCli.execute(context, streams, args)} runs one. A command line
     * that ran no tool, a tool that gave no result, and a result that could not be read give none.
     *
     * @param streams the streams the command line wrote to
     * @return the result, or {@code null} when there is none; asked again, {@code null}
     */
    static ToolResult take(CommandStreams streams) {
        return streams == null ? null : GIVEN.remove(streams)
    }

    /**
     * Makes way for the result of a tool that is about to be started with a file of arguments: what has
     * the name of its result is removed, a link without what it leads to.
     *
     * @param project the project
     * @param arguments the file of arguments the tool is started with
     * @return the file the result of the tool will be in, or {@code null} when none will be read: when
     * what had its name could not be removed, it is not taken for the result of this tool
     */
    static Path prepare(ConsoleProject project, CommandArgumentsFile arguments) {
        Path file = of(project, arguments)
        if (file == null) {
            return null
        }
        try {
            Files.deleteIfExists(file)
            return file
        } catch (IOException ignored) {
            return null
        }
    }

    /**
     * Reads the result of a tool that has ended, removes the file, and keeps the result for the
     * command line that runs on this thread ({@link CommandStreams#current()}), for {@link #take}.
     * Nothing is said at the terminal of a result that is read; of one that cannot be read, one warning
     * line on the error stream says why. Where the command was cancelled, the file is removed and not read.
     *
     * @param project the project
     * @param file the file of the result ({@link #prepare}), or {@code null} for none
     * @param name the name of the tool
     * @param said the error stream of the command line
     */
    static void collect(ConsoleProject project, Path file, String name, PrintWriter said) {
        if (file == null) {
            return
        }
        ToolResult result = null
        String refusal = null
        try {
            BasicFileAttributes attributes
            try {
                attributes = Files.readAttributes(file, BasicFileAttributes, LinkOption.NOFOLLOW_LINKS)
            } catch (IOException ignored) {
                // no result
                return
            }
            if (Thread.currentThread().isInterrupted()) {
                // cancelled: whoever asked for the result is gone
                return
            }
            refusal = attributes.isSymbolicLink() ? 'is a link' : !attributes.isRegularFile() ? 'is not a file' :
                    !ConsoleProject.isOwnedByUser(file, project) ? 'is not owned by the user who runs the command' :
                            attributes.size() > MAX_BYTES ? "has more than the ${MAX_BYTES} bytes a result has".toString() : null
            if (refusal == null) {
                try {
                    result = parse(file, attributes.size())
                } catch (BoundedJson.Refused e) {
                    refusal = e.getMessage()
                } catch (IOException ignored) {
                    refusal = 'cannot be read'
                }
            }
        } finally {
            remove(file)
        }
        if (result != null) {
            GIVEN.put(CommandStreams.current(), result)
        } else if (refusal != null) {
            said.println(CommandLine.Help.Ansi.AUTO.string('@|bold,red | Warning|@ ' +
                    GradleRunner.line("${name}: the result it set is not given, since ${RunDirectory.LOCATION}/${file.getFileName()} ${refusal}".toString())))
            said.flush()
        }
    }

    // the file of the result of a tool started with a file of arguments: its number, in the same directory
    private static Path of(ConsoleProject project, CommandArgumentsFile arguments) {
        String option = arguments.getOption()
        if (!option.startsWith(ARGUMENTS_OPTION)) {
            return null
        }
        Path given = project.getDirectory().resolve(option.substring(ARGUMENTS_OPTION.length()))
        Matcher matcher = ARGUMENTS_NAME.matcher(given.getFileName().toString())
        return matcher.matches() ? given.resolveSibling("${PREFIX}${matcher.group(1)}${EXTENSION}".toString()) : null
    }

    // the result, strictly as the class that ran the tool writes it, and no more bytes than the file had when its size was read
    private static ToolResult parse(Path file, long size) throws IOException, BoundedJson.Refused {
        byte[] bytes = bytesOf(file, size)
        if (bytes == null) {
            throw new BoundedJson.Refused('became longer while it was read')
        }
        for (byte each : bytes) {
            if (each < 0) {
                throw new BoundedJson.Refused('is not the ASCII a result is written in')
            }
        }
        String json = new String(bytes, StandardCharsets.US_ASCII)
        Object value = BoundedJson.parse(json, MAX_DEPTH, MAX_BYTES, MAX_BYTES)
        // a result is never null: there is none
        return value == null ? null : new ToolResult(value, json.trim())
    }

    private static byte[] bytesOf(Path file, long size) throws IOException {
        SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
        try {
            ByteBuffer buffer = ByteBuffer.allocate((int) size + 1)
            while (buffer.hasRemaining() && channel.read(buffer) != -1) {
                continue
            }
            if (!buffer.hasRemaining()) {
                return null
            }
            byte[] bytes = new byte[buffer.position()]
            buffer.flip()
            buffer.get(bytes)
            return bytes
        } finally {
            channel.close()
        }
    }

    private static void remove(Path file) {
        try {
            if (!Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                Files.deleteIfExists(file)
            }
        } catch (IOException ignored) {
            // what is left is removed before the next tool of that number is started
        }
    }
}
