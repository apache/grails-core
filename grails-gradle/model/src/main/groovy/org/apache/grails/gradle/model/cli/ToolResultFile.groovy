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
package org.apache.grails.gradle.model.cli

import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.CompileStatic

/**
 * The file a tool's result is given in, besides what the tool prints: what the tool set as
 * {@code executionContext.result}, written as JSON by the class that runs the tool when the tool has
 * ended, for the program that started it.
 *
 * <p>The file is beside the file of arguments the tool was started with ({@link CommandArgumentsFile}),
 * and has its number: the arguments {@code .grails/run/command-args-<n>.json} give the result
 * {@code .grails/run/tool-result-<n>.json}. A tool started without such a file of arguments gives its
 * result to nobody, and no file is written. Before the tool runs, a file an earlier tool of that number
 * left is removed; after the tool has returned, the result, when it has one that can be written, is
 * written to a new file that only the user can read. A tool that throws gives no result.</p>
 *
 * <p>The file is one JSON value of at most {@link #MAX_BYTES} bytes, in ASCII, nested at most
 * {@link #MAX_DEPTH} deep: an object, an array, a string, a number or a boolean, made of these and
 * {@code null}. Numbers are written as Java writes them; {@code NaN} and the infinities are no JSON,
 * and a result with one is not written.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ToolResultFile {

    /** What the name of the file of arguments a result goes beside starts with, before its number. */
    public static final String ARGUMENTS_PREFIX = 'command-args-'

    /** What the name of the file of a result starts with, before its number. */
    public static final String PREFIX = 'tool-result-'

    /** What the names of both files end with. */
    public static final String EXTENSION = '.json'

    /** The most bytes the file has. */
    public static final int MAX_BYTES = 256 * 1024

    /** How deep the arrays and objects of a result go at most. */
    public static final int MAX_DEPTH = 32

    private static final Pattern ARGUMENTS_NAME = Pattern.compile(Pattern.quote(ARGUMENTS_PREFIX) + '([0-9]{1,9})' + Pattern.quote(EXTENSION))

    private ToolResultFile() {
    }

    /**
     * Says why a result is not written. The message finishes the sentence "The result ..." and
     * repeats nothing of the result.
     */
    static final class Refused extends Exception {

        Refused(String message) {
            super(message)
        }
    }

    /**
     * @param argumentsFile the file of arguments a tool was started with
     * @return the file its result is given in, beside it and with its number; {@code null} when the
     * file of arguments is not named {@code command-args-<n>.json}, whose tool gives its result to nobody
     */
    static File of(File argumentsFile) {
        if (argumentsFile == null) {
            return null
        }
        Matcher matcher = ARGUMENTS_NAME.matcher(argumentsFile.name)
        matcher.matches() ? new File(argumentsFile.absoluteFile.parentFile, "${PREFIX}${matcher.group(1)}${EXTENSION}") : null
    }

    /**
     * Removes the file of a result, and only it: a link of that name is removed itself, and nothing it
     * leads to.
     *
     * @param file the file of a result
     * @return {@code false} when there was something that could not be removed
     */
    static boolean remove(File file) {
        try {
            Files.deleteIfExists(file.toPath())
            return true
        } catch (IOException ignored) {
            return false
        }
    }

    /**
     * @param result what a tool set as its result
     * @return the result as JSON, in ASCII, without white space
     * @throws Refused when it is not made of maps with text for keys, lists, texts, numbers that JSON has,
     * booleans and {@code null}, when it is nested more than {@link #MAX_DEPTH} deep, or when it has
     * more than {@link #MAX_BYTES} bytes as JSON
     */
    static String toJson(Object result) throws Refused {
        String json = StrictJson.write(jsonValue(result, 1))
        if (json.length() > MAX_BYTES) {
            throw new Refused("has more than ${MAX_BYTES} bytes as JSON".toString())
        }
        json
    }

    /**
     * Writes a result to a new file that only the user can read and write, where the file system has
     * such permissions: what is there of that name is not written over, and no link is followed.
     *
     * @param file the file of the result
     * @param result what the tool set as its result, not {@code null}
     * @throws Refused when the result cannot be written as JSON ({@link #toJson})
     * @throws IOException when the file cannot be written, also when something of its name is there
     */
    static void write(File file, Object result) throws Refused, IOException {
        ByteBuffer bytes = ByteBuffer.wrap(toJson(result).getBytes(StandardCharsets.US_ASCII))
        Path path = file.toPath()
        // a new file, made in one step: what is of that name, a link too, is not opened
        Set<StandardOpenOption> options = EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        SeekableByteChannel channel
        try {
            channel = Files.newByteChannel(path, options, PosixFilePermissions.asFileAttribute(
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)))
        } catch (UnsupportedOperationException ignored) {
            channel = Files.newByteChannel(path, options)
        }
        channel.withCloseable { SeekableByteChannel out ->
            while (bytes.hasRemaining()) {
                out.write(bytes)
            }
        }
    }

    // the result as StrictJson writes it: numbers as Numeral, keys and texts as strings
    private static Object jsonValue(Object value, int depth) throws Refused {
        if (value == null || value instanceof Boolean || value instanceof String) {
            return value
        }
        if (value instanceof CharSequence) {
            return value.toString()
        }
        if (value instanceof Number) {
            return number((Number) value)
        }
        if (depth > MAX_DEPTH) {
            throw new Refused("is nested more than ${MAX_DEPTH} deep".toString())
        }
        if (value instanceof Map) {
            Map<String, Object> object = new LinkedHashMap<String, Object>()
            for (Map.Entry entry : ((Map) value).entrySet()) {
                if (!(entry.key instanceof CharSequence)) {
                    throw new Refused('has a map with a key that is not text')
                }
                String key = entry.key.toString()
                if (object.containsKey(key)) {
                    throw new Refused('has a map with two keys of the same text')
                }
                object.put(key, jsonValue(entry.value, depth + 1))
            }
            return object
        }
        if (value instanceof List) {
            List<Object> array = []
            for (Object element : (List) value) {
                array << jsonValue(element, depth + 1)
            }
            return array
        }
        throw new Refused('is not made of maps, lists, texts, numbers, booleans and null')
    }

    private static Object number(Number number) throws Refused {
        if (number instanceof Integer || number instanceof Long || number instanceof Short || number instanceof Byte) {
            return new StrictJson.Numeral(Long.toString(number.longValue()))
        }
        if (number instanceof BigInteger) {
            return new StrictJson.Numeral(number.toString())
        }
        if (number instanceof BigDecimal) {
            return new StrictJson.Numeral(((BigDecimal) number).toString())
        }
        if (number instanceof Double || number instanceof Float) {
            double value = number.doubleValue()
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new Refused('has a number that JSON does not have: NaN or an infinity')
            }
            return new StrictJson.Numeral(number instanceof Float ? Float.toString(number.floatValue()) : Double.toString(value))
        }
        throw new Refused('is not made of maps, lists, texts, numbers, booleans and null')
    }
}
