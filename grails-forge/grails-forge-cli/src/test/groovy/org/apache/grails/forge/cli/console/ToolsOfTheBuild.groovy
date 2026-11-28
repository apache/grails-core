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

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import org.apache.grails.gradle.model.cli.ToolCatalogFreshness

/**
 * What the framework of this checkout builds of tools, for the specifications whose builds need more
 * of it than the command line does: the Gradle plugin that compiles {@code src/cli} and
 * {@code src/standalone} and has the task {@code runTool}, and the framework modules with tools in
 * {@code src/standalone}. Both are asked of the checkout itself, by what it does and by what it has,
 * so that such a specification runs wherever they are there.
 */
final class ToolsOfTheBuild {

    private ToolsOfTheBuild() {
    }

    /**
     * @return whether the model of the Gradle plugin on the class path takes a source of {@code src/cli}
     * for a change of the catalog of the tools, which it does once the plugin compiles that folder
     */
    static boolean hasToolFolders() {
        File project = Files.createTempDirectory('tool-folders').toFile()
        try {
            File source = new File(project, 'src/cli/groovy/ExampleTool.groovy')
            source.parentFile.mkdirs()
            source.text = 'class ExampleTool { }\n'
            return ToolCatalogFreshness.changedSince(project, [], FileTime.fromMillis(0L)) != null
        } finally {
            project.deleteDir()
        }
    }

    /**
     * @return whether the scaffolding plugin of the checkout has its tools in {@code src/standalone}, from
     * which the build publishes {@code grails-scaffolding-cli-standalone}
     */
    static boolean hasStandaloneScaffolding() {
        File root = root()
        return root != null && new File(root, 'grails-scaffolding/src/standalone').isDirectory()
    }

    // the directory of the checkout: above the classes of the model of the Gradle plugin, the one that has grails-forge
    private static File root() {
        File each = new File(ToolCatalogFreshness.protectionDomain.codeSource.location.toURI())
        while (each != null && !new File(each, 'grails-forge').isDirectory()) {
            each = each.parentFile
        }
        return each
    }
}
