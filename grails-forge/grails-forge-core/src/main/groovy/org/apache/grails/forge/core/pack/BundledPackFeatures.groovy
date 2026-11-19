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
 * @since 9.0
 */
@CompileStatic
class BundledPackFeatures implements BeanDefinitionRegistryPostProcessor {

    private static final String BEAN_PREFIX = 'forgeFeaturePack.'

    private ListableBeanFactory beanFactory

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        ClassLoader loader = BundledPackFeatures.classLoader
        PackLoader packs = new PackLoader()
        Function<String, Optional<Feature>> lookup = { String name -> find(name) } as Function<String, Optional<Feature>>
        // the pages of a template are compiled for each use, and are not shared between two generations
        Supplier<PackPages> pages = { -> new PackPages(loader) } as Supplier<PackPages>
        for (String name : names(loader)) {
            Pack pack = packs.fromClasspath(loader, name)
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
