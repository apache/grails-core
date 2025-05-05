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

package org.grails.forge.cli

import io.micronaut.context.BeanContext
import io.micronaut.core.util.functional.ThrowingSupplier
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.io.OutputHandler
import org.grails.forge.options.BuildTool
import org.grails.forge.options.Language
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework
import org.grails.forge.util.NameUtils

trait CommandFixture {

    abstract BeanContext getBeanContext()

    abstract File getDir()

    void generateProject(ApplicationType applicationType = ApplicationType.DEFAULT_OPTION,
                         String[] features) {
        beanContext.getBean(ProjectGenerator).generate(applicationType,
                NameUtils.parse("example.grails.foo"),
                new Options(),
                OperatingSystem.MACOS_ARCH64,
                features.toList(),
                new FileSystemOutputHandler(dir, ConsoleOutput.NOOP),
                ConsoleOutput.NOOP)
    }

    ThrowingSupplier<OutputHandler, IOException> getOutputHandler(ConsoleOutput consoleOutput) {
        return () -> new FileSystemOutputHandler(dir, consoleOutput)
    }

}