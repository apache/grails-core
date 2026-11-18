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
package org.apache.grails.core.plugins

import java.nio.file.Files
import java.nio.file.Path

import groovy.transform.CompileStatic

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration

/**
 * Plugin classes compiled into a directory of their own, laid out as a plugin's build lays them out, so that
 * what is found relative to a plugin class can be arranged per test.
 */
@CompileStatic
class PluginClassFixtures {

    /**
     * Compiles a plugin class with the given name into {@code root}.
     */
    static void compilePlugin(Path root, String className) {
        int dot = className.lastIndexOf('.')
        String simpleName = className.substring(dot + 1)
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.targetDirectory = root.toFile()
        CompilationUnit unit = new CompilationUnit(configuration)
        unit.addSource("${simpleName}.groovy",
                "package ${className.substring(0, dot)}\nclass ${simpleName} {\n    def version = '1.0'\n}\n")
        unit.compile()
    }

    /**
     * Writes {@code content} to {@code path} under {@code root}, creating the directories it needs.
     */
    static void write(Path root, String path, String content) {
        Path file = root.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }

    /**
     * A class loader over {@code roots} whose classes are loaded from them, the plugin classes included.
     */
    static URLClassLoader loaderOver(Path... roots) {
        new URLClassLoader(roots.collect { Path root -> root.toUri().toURL() } as URL[],
                PluginClassFixtures.classLoader)
    }
}
