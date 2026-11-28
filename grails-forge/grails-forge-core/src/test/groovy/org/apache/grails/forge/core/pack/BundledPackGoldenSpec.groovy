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
import org.grails.forge.application.ContextFactory
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureContext
import org.grails.forge.feature.FeaturePredicate
import org.grails.forge.feature.FeatureRegistry
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.NameUtils
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
 * <p>A feature that is in an application without being named, by default or for a value of an option,
 * is compared in that application: with it, and with it taken out of the features.</p>
 *
 * <p>After a change that is meant, write the files again by running this spec with the environment
 * variable {@code FORGE_UPDATE_GOLDEN} set to {@code true}, and read the difference before it is
 * committed.</p>
 */
class BundledPackGoldenSpec extends ApplicationContextSpec implements CommandOutputFixture {

    static final String GOLDEN = 'src/test/resources/golden/bundled'
    private static final Map<String, Options> OPTION_SETS = [:]

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

    private static String when(PackCondition condition) {
        return condition == PackCondition.ALWAYS ? '' : " when ${condition}"
    }

    // the defaults first, then every other value of an option
    private static Map<String, Options> optionSets() {
        if (OPTION_SETS.isEmpty()) {
            OPTION_SETS['the default options'] = new Options()
            GormImpl.values().findAll { it != GormImpl.DEFAULT_OPTION }.each { OPTION_SETS["gorm ${it.name().toLowerCase()}".toString()] = new Options().withGormImpl(it) }
            ServletImpl.values().findAll { it != ServletImpl.DEFAULT_OPTION }.each { OPTION_SETS["servlet ${it.name().toLowerCase()}".toString()] = new Options().withServletImpl(it) }
            DevelopmentReloading.values().findAll { it != DevelopmentReloading.DEFAULT_OPTION }.each { OPTION_SETS["reloading ${it.name}".toString()] = new Options().withDevelopmentReloading(it) }
        }
        return OPTION_SETS
    }

    // an application of the defaults of the options, with one feature taken out of its features when one is named
    private Map<String, Object> generated(ApplicationType type, Options options, String without) {
        ContextFactory factory = beanContext.getBean(ContextFactory)
        FeatureContext featureContext = factory.createFeatureContext(beanContext.getBean(FeatureRegistry).availableFeatures(type), [], type,
                options, OperatingSystem.LINUX)
        if (without != null) {
            featureContext.exclude({ Feature feature -> feature.name == without } as FeaturePredicate)
        }
        GeneratorContext context = factory.createGeneratorContext(NameUtils.parse('example.grails.foo'), featureContext, ConsoleOutput.NOOP)
        return [features: new ArrayList<String>(context.features), files: generate(type, context)]
    }

    private String describe(String name) {
        FeatureRegistry registry = beanContext.getBean(FeatureRegistry)
        StringBuilder text = new StringBuilder()
        // what the lines of an application cannot show: where a version comes from, and how the feature is selected
        PackDescriptor descriptor = new PackLoader().fromClasspath(BundledPackGoldenSpec.classLoader, name).descriptor
        text << "compatibility: grails ${descriptor.grailsCompatibility}, forge ${descriptor.forgeCompatibility}\n"
        text << "requires: ${descriptor.requires}\nconflicts: ${descriptor.conflicts}\noneOf: ${descriptor.oneOf}\n"
        // how a pack takes part in the selection; a pack that says none of it has none of these lines
        PackOfForge ofForge = descriptor.ofForge
        if (ofForge.byDefault != null) {
            text << "default: ${ofForge.byDefault}\n"
        }
        if (ofForge.order != 0) {
            text << "order: ${ofForge.order}\n"
        }
        ofForge.adds.each { text << "adds: ${it.feature}${when(it.when)}\n" }
        ofForge.reliesOn.each { text << "relies on: ${it.feature}${when(it.when)}\n" }
        if (ofForge.requiresJdk != null) {
            text << "requires JDK: ${ofForge.requiresJdk}\n"
        }
        if (ofForge.dataSourceOfDriver) {
            text << 'takes the data source of the database driver\n'
        }
        descriptor.dependencies.each {
            text << "dependency: ${it.scope} ${it.groupId}:${it.artifactId} ${it.versionOfForge ? 'in the version of Forge' : it.version ?: 'without a version'}${when(it.when)}\n"
        }
        descriptor.buildPlugins.each {
            text << "build plugin: ${it.id} ${it.artifactOfForge ? 'in the version Forge has for ' + it.artifactOfForge : it.version ?: 'without a version'}${it.apply ? ', applied' : ''}${when(it.when)}\n"
        }
        descriptor.files.findAll { it.when != PackCondition.ALWAYS }.each { text << "file: ${it.to}${when(it.when)}\n" }
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
            Map<String, String> without
            Map<String, String> with
            Options byItself = optionSets().find { String label, Options options -> generated(type, options, null).features.contains(name) }?.value
            if (byItself != null) {
                // it is there without being named: the application with it, and with it taken out of the features
                text << "in an application without being named: ${optionSets().find { it.value.is(byItself) }.key}\n"
                with = generated(type, byItself, null).files
                try {
                    without = generated(type, byItself, name).files
                } catch (RuntimeException e) {
                    text << "no application without it: ${e.message}\n\n"
                    return
                }
            } else if (!feature.visible) {
                text << 'in no application of this kind\n\n'
                return
            } else {
                without = generate(type, [])
                try {
                    with = generate(type, [name])
                } catch (IllegalArgumentException e) {
                    // a feature that is refused when it is selected alone, as one that is for another feature
                    text << "no application with it alone: ${e.message}\n\n"
                    return
                }
            }
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
