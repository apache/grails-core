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

import java.nio.file.Files
import java.nio.file.Path
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureRegistry
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.MapOutputHandler
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils
import spock.lang.TempDir

class BundledPackFeaturesSpec extends ApplicationContextSpec {

    @TempDir
    Path directory

    void "every pack Forge names in its index is a feature of the framework"() {
        given:
        List<String> names = BundledPackFeatures.names(getClass().classLoader)
        FeatureRegistry registry = beanContext.getBean(FeatureRegistry)

        expect:
        names.contains('grails-web-console')
        names.unique(false) == names

        and:
        names.every { String name ->
            Feature feature = registry.all().find { it.name == name }
            feature instanceof PackFeature && ApplicationType.values().any { feature.supports(it) }
        }
    }

    void "a pack of Forge is read from its resources and is marked as one"() {
        when:
        Pack pack = new PackLoader().fromClasspath(getClass().classLoader, 'grails-web-console')

        then:
        pack.bundled
        pack.descriptor.name == 'grails-web-console'
        pack.origin == 'the features of Forge (grails-web-console)'
    }

    void "a pack Forge has not is said to be missing"() {
        when:
        new PackLoader().fromClasspath(getClass().classLoader, 'no-such-pack')

        then:
        PackDescriptorException e = thrown()
        e.problems == ['There is no META-INF/grails-forge-bundled/no-such-pack/feature.yml, or it is larger than a descriptor can be']
    }

    void "a pack that is not of Forge takes no version from what Forge knows"() {
        given:
        Files.createDirectories(directory.resolve('templates'))
        Files.writeString(directory.resolve('feature.yml'), new String(
                getClass().classLoader.getResourceAsStream('META-INF/grails-forge-bundled/grails-web-console/feature.yml').readAllBytes(), 'UTF-8')
                .replace('name: grails-web-console', 'name: acme-console'))
        Pack pack = new PackLoader().fromDirectory(directory)
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('example.grails.foo')
        MapOutputHandler output = new MapOutputHandler()

        when:
        generator.generate(ApplicationType.WEB, project, output, generator.createGeneratorContext(ApplicationType.WEB, project,
                new Options(), OperatingSystem.LINUX, ['acme-console'], ConsoleOutput.NOOP, [pack]))

        then:
        !pack.bundled
        output.getProject()['build.gradle'].contains('    runtimeOnly "org.grails.plugins:grails-web-console"\n')
    }
}
