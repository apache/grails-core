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
package org.apache.grails.forge.core.change

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.grails.forge.io.OutputHandler
import org.grails.forge.template.Template

/**
 * A project as the generator writes it, kept in memory: every file with its bytes, binary or not, and
 * whether it can be executed, the files of its state under {@code .grails} among them.
 */
@CompileStatic
@PackageScope
final class RenderedProject implements OutputHandler {

    private final Map<String, byte[]> files = new LinkedHashMap<>()
    private final Set<String> executable = new HashSet<>()

    @Override
    boolean exists(String path) {
        return files.containsKey(steps(path))
    }

    @Override
    void write(String path, Template contents) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        contents.write(out)
        String file = steps(path)
        files.put(file, out.toByteArray())
        if (contents.isExecutable()) {
            executable.add(file)
        } else {
            executable.remove(file)
        }
    }

    @Override
    String getOutputLocation() {
        return null
    }

    @Override
    void close() {
    }

    // a/b, a//b and a/./b are one file, as they are to the index of the baseline
    private static String steps(String path) {
        return path.split('/').findAll { String step -> !step.isEmpty() && step != '.' }.join('/')
    }

    List<String> paths() {
        return files.keySet().toList()
    }

    byte[] content(String path) {
        return files.get(path)
    }

    // the content of a file that was written, as it is to be instead
    void replace(String path, byte[] content) {
        if (!files.containsKey(path)) {
            throw new IllegalArgumentException("${path} was not written")
        }
        files.put(path, content)
    }

    boolean isExecutable(String path) {
        return executable.contains(path)
    }
}
