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
String sha256(Path file) {
    MessageDigest md = MessageDigest.getInstance('SHA-256')
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
List<Path> artifacts = []
Files.walk(root)
        .filter {
            Files.isRegularFile(it) &&
                    !it.toString().contains('buildSrc') &&  // build src jars aren't published
                    it.toString().endsWith('.jar') &&
                    it.toString().contains("${File.separator}build${File.separator}libs${File.separator}")
        }
        .forEach { artifacts << it }

artifacts.sort { a, b -> a.toString() <=> b.toString() }
        .each { Path jar ->
            String hash = sha256(jar)
            String relative = root.relativize(jar).toString()
            println "${hash} ${relative}"
        }