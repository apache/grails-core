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
import org.grails.forge.template.Template

/**
 * A file of a feature pack that is written to the application as it is. What writes it checks where,
 * since the destination is the pack's to choose: see {@link PackDescriptorParser#isPathBelowRoot(String)}.
 *
 * @since 9.0
 */
@CompileStatic
final class PackContent implements Template {

    private final String path
    private final byte[] content
    private final boolean executable
    private final boolean held

    /**
     * @param path the destination of the file, with its placeholders
     * @param content the content of the file
     */
    PackContent(String path, byte[] content) {
        this(path, content, false)
    }

    /**
     * @param path the destination of the file, with its placeholders
     * @param content the content of the file
     * @param executable whether the file is one that is run
     */
    PackContent(String path, byte[] content, boolean executable) {
        this(path, content, executable, true)
    }

    /**
     * @param path the destination of the file, with its placeholders
     * @param content the content of the file
     * @param executable whether the file is one that is run
     * @param held whether the destination is held to the paths a pack can write; that of a pack Forge has
     * in itself is not, it is a file of the framework
     */
    PackContent(String path, byte[] content, boolean executable, boolean held) {
        this.path = path
        this.content = content.clone()
        this.executable = executable
        this.held = held
    }

    /**
     * @return whether the destination is held to the paths a pack can write
     */
    boolean isHeld() {
        return held
    }

    @Override
    boolean isExecutable() {
        return executable
    }

    @Override
    String getPath() {
        return path
    }

    @Override
    void write(OutputStream outputStream) throws IOException {
        outputStream.write(content)
    }
}
