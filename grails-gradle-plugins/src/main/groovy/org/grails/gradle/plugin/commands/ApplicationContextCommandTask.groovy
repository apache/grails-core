/*
 * Copyright 2014-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.gradle.plugin.commands

import groovy.transform.CompileStatic
import org.gradle.api.tasks.JavaExec

/**
 *
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class ApplicationContextCommandTask extends JavaExec {

    ApplicationContextCommandTask() {
        mainClass.set("grails.ui.command.GrailsApplicationContextCommandRunner")
        dependsOn("classes", "findMainClass")
        systemProperties(System.properties.findAll { it.key.toString().startsWith('grails.') } as Map<String, Object>)
    }

    void setCommand(String commandName) {
        args(commandName)
    }
}
