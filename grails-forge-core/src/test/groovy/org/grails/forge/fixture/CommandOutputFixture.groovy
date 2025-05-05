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

package org.grails.forge.fixture

import groovy.transform.CompileStatic
import io.micronaut.context.BeanContext
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.MapOutputHandler
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils

@CompileStatic
trait CommandOutputFixture {
    abstract BeanContext getBeanContext()

    Map<String, String> generate(ApplicationType type, Options options, List<String> features = []) {
        def handler = new MapOutputHandler()
        beanContext.getBean(ProjectGenerator).generate(type,
                NameUtils.parse("example.grails.foo"),
                options,
                options.operatingSystem,
                features,
                handler,
                ConsoleOutput.NOOP
        )
        handler.getProject()
    }

    Map<String, String> generate(List<String> features = []) {
        generate(ApplicationType.WEB, features)
    }

    Map<String, String> generate(ApplicationType type, List<String> features = []) {
        def handler = new MapOutputHandler()
        Options options = new Options()
        beanContext.getBean(ProjectGenerator).generate(type,
                NameUtils.parse("example.grails.foo"),
                options,
                options.operatingSystem,
                features,
                handler,
                ConsoleOutput.NOOP
        )
        handler.getProject()
    }

    Map<String, String> generate(ApplicationType type, GeneratorContext generatorContext) {
        def handler = new MapOutputHandler()
        beanContext.getBean(ProjectGenerator).generate(type,
                NameUtils.parse("example.grails.foo"),
                handler,
                generatorContext
        )
        handler.getProject()
    }
}
