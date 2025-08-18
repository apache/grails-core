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
import java.security.MessageDigest

// ---------------------------------------------------------------------------
String sha512(Path file) {
    MessageDigest md = MessageDigest.getInstance('SHA-512')
    file.withInputStream { is ->
        byte[] buf = new byte[8192]
        for (int r = is.read(buf); r > 0; r = is.read(buf))
            md.update(buf, 0, r)
    }
    md.digest().collect { String.format('%02x', it) }.join()
}

Path scriptDir = Paths.get(getClass()
        .protectionDomain
        .codeSource
        .location
        .toURI())
        .toAbsolutePath()
        .parent


Path root = scriptDir.resolve('..').resolve('..').normalize()
if(args && args.length > 0) {
    System.out.println("Finding jars in: ${args[0]}" as String)
    root = Paths.get(args[0]).toAbsolutePath().normalize()
}

// ---------------------------------------------------------------------------
// Decide where to search: project root by default, or user-supplied path
// (absolute or relative to project root) when an argument is given.
Path scanRoot
if (this.args && this.args.length > 0) {
    Path argPath = Paths.get(this.args[0])
    scanRoot = argPath.isAbsolute() ? argPath : root.resolve(argPath).normalize()
    if (!Files.exists(scanRoot)) {
        System.err.println "❌  Path '${scanRoot}' does not exist."
        System.exit(1)
    }
} else {
    scanRoot = root
}
List<Path> artifacts = []
Files.walk(scanRoot)
            .filter {
                Files.isRegularFile(it) &&
                        !it.toString().contains("buildSrc") &&
                        !it.toString().contains("etc") &&
                        it.toString().endsWith('.jar') &&
                        it.toString().contains("${File.separator}build${File.separator}libs${File.separator}" as String)
            }
            .forEach { artifacts << it }

artifacts.findAll {
    !it.toString().contains("${File.separator}buildSrc${File.separator}" as String) // build src jars aren't published
    !it.toString().contains("${File.separator}grails-test-examples${File.separator}" as String) // test examples aren't published
}.sort { a, b -> a.toString() <=> b.toString()
}.collect { Path jar ->
    String hash = sha512(jar)
    String relative = root.relativize(jar).toString()
    "${relative} ${hash}"
}.sort().each {
    println it
}