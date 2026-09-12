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
package org.grails.gradle.plugin.core

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Writes the Groovy compiler configuration script a {@link org.gradle.api.tasks.compile.GroovyCompile}
 * task compiles with.
 *
 * <p>The script cannot be assigned from a task action: Gradle finalizes task properties before any
 * action runs, so from Gradle 9.7 on — where {@code GroovyCompileOptions} became a lazy property —
 * assigning {@code groovyOptions.configurationScript} at execution time fails. Assigning it during
 * configuration then makes it an input file that must exist before compilation starts, which is what
 * this task guarantees.</p>
 *
 * <p>The Grails half of the script is a plain {@link Input}, built entirely from configuration
 * state, so this task is up-to-date checked like any other rather than opting out of state
 * tracking. The build's own script, when it has one, is an {@link InputFile}: Gradle then fails
 * the build when the file is missing, the way it would have for the compile task itself.</p>
 *
 * @since 8.0
 */
@CompileStatic
@DisableCachingByDefault(because = 'Writing a short script from a string is cheaper than a cache round trip')
abstract class GrailsCompilerConfigScriptTask extends DefaultTask {

    /**
     * The Grails compiler configuration: the star imports, and for a plugin the metadata it
     * stamps on compiled classes. Absent when there is nothing to add.
     */
    @Input
    @Optional
    abstract Property<String> getGrailsScript()

    /**
     * The {@code configurationScript} the build configured on the compile task, if any. Its
     * content follows the Grails configuration in the combined script.
     */
    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    abstract RegularFileProperty getConfigurationScript()

    @OutputFile
    abstract RegularFileProperty getOutputFile()

    @TaskAction
    void writeScript() {
        def target = outputFile.get().asFile
        target.parentFile.mkdirs()
        target.text = """
            // Grails groovy compilation configuration to ensure ASTs are applied correctly

            ${grailsScript.orNull?.trim() ?: ''}

            ${configurationScript.asFile.orNull?.text?.trim() ?: ''}
        """
    }
}
