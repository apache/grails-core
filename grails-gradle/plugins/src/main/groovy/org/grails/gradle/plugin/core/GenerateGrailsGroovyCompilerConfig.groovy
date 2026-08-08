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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CompileStatic
@CacheableTask
abstract class GenerateGrailsGroovyCompilerConfig extends DefaultTask {

    @Classpath
    abstract ConfigurableFileCollection getCompileClasspath()

    @Input
    abstract Property<String> getGrailsScript()

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    abstract RegularFileProperty getBaseScript()

    @OutputFile
    abstract RegularFileProperty getOutputFile()

    @TaskAction
    void generate() {
        File baseScriptFile = baseScript.orNull?.asFile
        String configuredScript = baseScriptFile?.text?.trim() ?: null
        RegularFile generatedScript = outputFile.get()
        File output = generatedScript.asFile
        output.parentFile.mkdirs()

        String combinedScripts = """
            // Grails groovy compilation configuration to ensure ASTs are applied correctly

            ${grailsScript.get().trim() ?: ''}

            ${configuredScript?.trim() ?: ''}
        """
        output.write(combinedScripts)
    }
}
