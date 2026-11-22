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

import groovy.transform.CompileStatic
import java.nio.charset.StandardCharsets
import java.util.function.Function
import java.util.function.Supplier
import org.grails.forge.feature.Feature
import org.grails.forge.util.VersionInfo
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.RootBeanDefinition

/**
 * Makes a feature of every pack Forge has in itself. A feature of the framework that is only
 * dependencies, settings and files is written as a pack, in the format a plugin uses for its own, and
 * is then a feature like the compiled ones: it is listed, selected and validated with them, by the
 * command line and by the web application.
 *
 * <p>The packs are named, one on a line, in the resource {@link Pack#BUNDLED_INDEX}. An index that is
 * missing or empty, a pack that cannot be read and one that is not for this Grails stop Forge from
 * starting: they are part of Forge.</p>
 *
 * <p>The templates of these packs are compiled once, the first time each is rendered, and the compiled
 * pages are used by every generation after it, at once where generations are: they are of Forge, and
 * as many as its packs have templates. A template the build of Forge compiled is not compiled then:
 * its page is taken as the build made it, see {@link PrecompiledPackPages}. With the system property
 * {@link #PRECOMPILED_PROPERTY} set to {@code false} every template is compiled, as it is for a pack
 * that is not of Forge.</p>
 *
 * @since 9.0
 */
@CompileStatic
class BundledPackFeatures implements BeanDefinitionRegistryPostProcessor {

    /**
     * The system property that, set to {@code false}, has the templates of the packs of Forge compiled
     * when they are rendered and no page of the build taken: to tell what a page of the build has to do
     * with something that goes wrong, or with the time a generation takes. Read when the first template
     * is rendered.
     */
    public static final String PRECOMPILED_PROPERTY = 'grails.forge.pack.precompiled'

    private static final String BEAN_PREFIX = 'forgeFeaturePack.'

    private final ClassLoader resources
    private ListableBeanFactory beanFactory
    private PackPages shared

    BundledPackFeatures() {
        this(BundledPackFeatures.classLoader)
    }

    /**
     * @param resources the class loader that has the index of the packs and the packs it names
     */
    BundledPackFeatures(ClassLoader resources) {
        this.resources = resources
    }

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        // the pages are compiled against Forge, wherever its packs are read from
        ClassLoader loader = BundledPackFeatures.classLoader
        PackLoader packs = new PackLoader()
        Function<String, Optional<Feature>> lookup = { String name -> find(name) } as Function<String, Optional<Feature>>
        Supplier<PackPages> pages = { -> shared(loader) } as Supplier<PackPages>
        for (String name : names(resources)) {
            Pack pack = packs.fromClasspath(resources, name)
            PackFeatures.checkCompatibility(pack, 'Grails', pack.descriptor.grailsCompatibility, VersionInfo.getGrailsVersion())
            PackFeatures.checkCompatibility(pack, 'Forge', pack.descriptor.forgeCompatibility, VersionInfo.getVersion())
            RootBeanDefinition definition = new RootBeanDefinition(PackFeature, { -> PackFeature.of(pack, lookup, pages) } as Supplier<PackFeature>)
            registry.registerBeanDefinition(BEAN_PREFIX + name, definition)
        }
    }

    @Override
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory
    }

    /**
     * @return the number of templates of the packs of Forge that have been compiled since Forge started,
     * which is at most the number of templates those packs have: one that cannot be compiled is counted
     * once too
     */
    synchronized int getCompiledTemplates() {
        return shared == null ? 0 : shared.getCompiled()
    }

    /**
     * @return the number of templates of the packs of Forge that were not compiled since Forge started,
     * because the page the build of Forge compiled for them was taken
     */
    synchronized int getPrecompiledTemplates() {
        return shared == null ? 0 : shared.getPrecompiled()
    }

    // made when the first template is rendered: many uses of Forge render none
    private synchronized PackPages shared(ClassLoader loader) {
        if (shared == null) {
            boolean precompiled = Boolean.parseBoolean(System.getProperty(PRECOMPILED_PROPERTY, 'true'))
            shared = new PackPages(loader, precompiled ? PrecompiledPackPages.ofForge() : null)
        }
        return shared
    }

    /**
     * @param loader the class loader that has the resources of Forge
     * @return the names of the packs Forge has in itself, in the order of the index
     */
    static List<String> names(ClassLoader loader) {
        InputStream index = loader.getResourceAsStream(Pack.BUNDLED_INDEX)
        if (index == null) {
            // without it features of the framework would be gone and nothing would say so
            throw new IllegalStateException("Forge is not whole: it has no ${Pack.BUNDLED_INDEX}")
        }
        List<String> names
        try (InputStream input = index) {
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8)
            names = (text.startsWith('\uFEFF') ? text.substring(1) : text).readLines()
                    .collect { String line -> line.trim() }
                    .findAll { String line -> line && !line.startsWith('#') }
        }
        if (names.isEmpty()) {
            throw new IllegalStateException("Forge is not whole: ${Pack.BUNDLED_INDEX} names no pack")
        }
        List<String> twice = names.findAll { String name -> names.count(name) > 1 }.toUnique()
        if (twice) {
            throw new IllegalStateException("Forge is not whole: ${Pack.BUNDLED_INDEX} names ${twice.join(', ')} more than once")
        }
        return names
    }

    private Optional<Feature> find(String name) {
        return beanFactory.getBeanProvider(Feature).stream()
                .filter { Feature feature -> feature.getName() == name }
                .findFirst()
    }
}
