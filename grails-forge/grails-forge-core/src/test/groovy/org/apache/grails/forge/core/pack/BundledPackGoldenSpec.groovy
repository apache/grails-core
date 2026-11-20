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

import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureRegistry
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.util.VersionInfo
import spock.lang.Unroll

/**
 * What each pack of Forge adds to an application, kept beside the specs: a feature that was compiled
 * once and is a pack now has to go on giving what it gave. The file of a pack under
 * {@code src/test/resources/golden/bundled} holds what the feature says of itself and every line the
 * application has with the feature that it has not without it, for each kind of application. The
 * versions of dependencies and of the documentation of Grails, which change with every release, are
 * written as {@code VERSION}.
 *
 * <p>After a change that is meant, write the files again by running this spec with the environment
 * variable {@code FORGE_UPDATE_GOLDEN} set to {@code true}, and read the difference before it is
 * committed.</p>
 */
class BundledPackGoldenSpec extends ApplicationContextSpec implements CommandOutputFixture {

    static final String GOLDEN = 'src/test/resources/golden/bundled'

    @Unroll
    void "the pack #name of Forge gives what it gave"() {
        given:
        String now = describe(name)
        File golden = new File(GOLDEN, "${name}.txt")
        if (System.getenv('FORGE_UPDATE_GOLDEN') == 'true' && !System.getenv('CI')) {
            golden.parentFile.mkdirs()
            golden.setText(now, 'UTF-8')
        }

        expect:
        golden.isFile()
        now == golden.getText('UTF-8').replace('\r\n', '\n')

        where:
        name << BundledPackFeatures.names(BundledPackGoldenSpec.classLoader)
    }

    void "there is a golden file for every pack of Forge and for nothing else"() {
        expect:
        (new File(GOLDEN).list() as List<String>).toSorted() == BundledPackFeatures.names(getClass().classLoader).collect { it + '.txt' }.toSorted()
    }

    private String describe(String name) {
        FeatureRegistry registry = beanContext.getBean(FeatureRegistry)
        StringBuilder text = new StringBuilder()
        // what the lines of an application cannot show: where a version comes from, and how the feature is selected
        PackDescriptor descriptor = new PackLoader().fromClasspath(BundledPackGoldenSpec.classLoader, name).descriptor
        text << "compatibility: grails ${descriptor.grailsCompatibility}, forge ${descriptor.forgeCompatibility}\n"
        text << "requires: ${descriptor.requires}\nconflicts: ${descriptor.conflicts}\noneOf: ${descriptor.oneOf}\n"
        descriptor.dependencies.each {
            text << "dependency: ${it.scope} ${it.groupId}:${it.artifactId} ${it.versionOfForge ? 'in the version of Forge' : it.version ?: 'without a version'}\n"
        }
        descriptor.buildPlugins.each {
            text << "build plugin: ${it.id} ${it.artifactOfForge ? 'in the version Forge has for ' + it.artifactOfForge : it.version ?: 'without a version'}\n"
        }
        descriptor.buildBlocks.each { String anchor, String block -> text << "build block: ${anchor}\n" }
        text << '\n'
        ApplicationType.values().each { ApplicationType type ->
            Feature feature = registry.find(type, name, true).orElse(null)
            text << "# ${type.name}\n"
            if (feature == null) {
                text << 'not for this kind of application\n\n'
                return
            }
            text << "title: ${feature.title}\ndescription: ${feature.description}\ncategory: ${feature.category}\n"
            text << "documentation: ${feature.documentation}\nthirdPartyDocumentation: ${feature.thirdPartyDocumentation}\n"
            text << "visible: ${feature.visible}\n"
            text << "dependentFeatures: ${feature.dependentFeatures}\n"
            Map<String, String> without = generate(type, [])
            Map<String, String> with = generate(type, [name])
            // the record of the features of the application changes with every feature the framework gains,
            // and the state under .grails repeats it and every file of the application
            with.keySet().findAll { !ProjectState.isStatePath(it) }.toSorted().each { String path ->
                List<String> before = (without[path] ?: '').readLines()
                List<String> added = []
                (with[path] ?: '').readLines().each { String line ->
                    // the date java.util.Properties writes
                    if (!before.remove((Object) line) && !(line ==~ /#[A-Z][a-z]{2} [A-Z][a-z]{2} \d.*/)) {
                        added << line
                    }
                }
                if (added || before) {
                    text << "== ${path}\n"
                    before.findAll { !(it ==~ /#[A-Z][a-z]{2} [A-Z][a-z]{2} \d.*/) }.each { text << "- ${it}\n" }
                    added.each { text << "+ ${it.replaceAll(/:\d[A-Za-z0-9_.+\-]*"/, ':VERSION"')}\n" }
                }
            }
            (without.keySet() - with.keySet()).findAll { !ProjectState.isStatePath(it) }.toSorted().each { text << "== ${it}\n(not written)\n" }
            text << '\n'
        }
        // the documentation of Grails is that of the version Forge generates for
        return text.toString().replace("https://grails.apache.org/docs/${VersionInfo.documentationVersion}/", 'https://grails.apache.org/docs/VERSION/')
    }
}
