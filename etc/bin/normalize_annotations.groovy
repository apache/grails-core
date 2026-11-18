#!/usr/bin/env groovy
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
import java.nio.file.*
import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.Field

/*
 * Sorts annotation member lists in decompiled .java sources, in place.
 *
 * The Groovy compiler writes annotation members copied from precompiled classes
 * (e.g. @DelegatesTo on trait methods woven into controllers and GORM entities)
 * in Class.getDeclaredMethods() order, which varies between JVM runs. Member
 * order carries no semantic meaning, so the reproducible-build comparison
 * normalizes it before diffing decompiled sources.
 */

// ---------------------------------------------------------------------------
@Field final Pattern ANNOTATION = ~/@[A-Za-z_][\w.]*\(/
@Field final Pattern MEMBER = ~/(?s)\s*\w+\s*=.*/

// Index of the ')' matching the '(' at openIndex, or -1
int findClose(String text, int openIndex) {
    int depth = 0
    Character quote = null
    for (int i = openIndex; i < text.length(); i++) {
        char c = text.charAt(i)
        if (quote != null) {
            if (c == '\\' as char) {
                i++
            } else if (c == quote) {
                quote = null
            }
        } else if (c == '"' as char || c == "'" as char) {
            quote = c
        } else if (c == '(' as char) {
            depth++
        } else if (c == ')' as char) {
            depth--
            if (depth == 0) {
                return i
            }
        }
    }
    return -1
}

// Split on commas not nested inside brackets or string literals
List<String> splitTopLevel(String text) {
    List<String> parts = []
    StringBuilder current = new StringBuilder()
    int depth = 0
    Character quote = null
    for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i)
        if (quote != null) {
            current.append(c)
            if (c == '\\' as char && i + 1 < text.length()) {
                current.append(text.charAt(i + 1))
                i++
            } else if (c == quote) {
                quote = null
            }
        } else if (c == '"' as char || c == "'" as char) {
            quote = c
            current.append(c)
        } else if (c == '(' as char || c == '{' as char || c == '[' as char) {
            depth++
            current.append(c)
        } else if (c == ')' as char || c == '}' as char || c == ']' as char) {
            depth--
            current.append(c)
        } else if (c == ',' as char && depth == 0) {
            parts << current.toString()
            // new instance instead of setLength(0): setLength is only declared on the
            // package-private AbstractStringBuilder, which old Groovy runtimes cannot invoke
            current = new StringBuilder()
        } else {
            current.append(c)
        }
    }
    parts << current.toString()
    return parts
}

String normalize(String text) {
    StringBuilder out = new StringBuilder()
    int index = 0
    Matcher matcher = ANNOTATION.matcher(text)
    while (matcher.find(index)) {
        int openIndex = matcher.end() - 1
        int closeIndex = findClose(text, openIndex)
        if (closeIndex < 0) {
            out.append(text, index, matcher.end())
            index = matcher.end()
            continue
        }
        String inner = normalize(text.substring(openIndex + 1, closeIndex))
        List<String> parts = splitTopLevel(inner)
        if (parts.size() > 1 && parts.every { it ==~ MEMBER }) {
            inner = parts
                    .collect { it.trim() }
                    .sort { it.split('=', 2)[0].trim() }
                    .join(', ')
        }
        out.append(text, index, matcher.end())
        out.append(inner)
        out.append(')')
        index = closeIndex + 1
    }
    out.append(text.substring(index))
    return out.toString()
}

// ---------------------------------------------------------------------------
if (!args) {
    System.err.println "Usage: normalize_annotations.groovy <source-dir> [<source-dir> ...]"
    System.exit 1
}

args.each { dir ->
    // try/finally instead of withCloseable: Stream is AutoCloseable, not Closeable,
    // and older Groovy runtimes lack withCloseable(AutoCloseable)
    def stream = Files.walk(Paths.get(dir))
    try {
        stream.filter { it.toString().endsWith('.java') && Files.isRegularFile(it) }.forEach { Path path ->
            String original = new String(Files.readAllBytes(path), 'UTF-8')
            String normalized = normalize(original)
            if (normalized != original) {
                Files.write(path, normalized.getBytes('UTF-8'))
            }
        }
    } finally {
        stream.close()
    }
}
