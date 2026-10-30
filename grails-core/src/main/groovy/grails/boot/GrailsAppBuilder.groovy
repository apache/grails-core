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

package grails.boot

import groovy.transform.CompileStatic

import org.springframework.beans.factory.support.BeanNameGenerator
import org.springframework.boot.ApplicationContextFactory
import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.bootstrap.BootstrapRegistryInitializer
import org.springframework.boot.builder.ParentContextApplicationContextInitializer
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.io.ResourceLoader
import org.springframework.core.metrics.ApplicationStartup

/**
 * Fluent API for constructing and running a {@link GrailsApp}: the Grails counterpart of Spring Boot's
 * {@link SpringApplicationBuilder}.
 *
 * <p>Every builder method of {@code SpringApplicationBuilder} is available and keeps returning a
 * {@code GrailsAppBuilder}, so a chain stays in Grails terms from start to finish and the application
 * it runs is a {@link GrailsApp} with the Grails startup defaults:</p>
 *
 * <pre><code>
 * new GrailsAppBuilder(Application)
 *         .profiles('metrics')
 *         .properties('server.port=8081')
 *         .run(args)
 * </code></pre>
 *
 * <p>Parent/child context hierarchies work as they do in Spring Boot, with every context of the
 * hierarchy started by a {@code GrailsApp}: {@link #child(Class[])}, {@link #parent(Class[])} and
 * {@link #sibling(Class[])} return Grails builders, a parent cannot host an embedded web server, and
 * closing the parent closes its children. Exactly one context of a hierarchy is the Grails application,
 * the one whose sources include the Grails application class; the others are plain Spring contexts that
 * share the Grails beans through the parent chain. Because the parent cannot serve web requests, the
 * application class belongs in the child of a two-level hierarchy:</p>
 *
 * <pre><code>
 * new GrailsAppBuilder(SharedConfiguration)
 *         .child(Application)
 *         .run(args)
 * </code></pre>
 *
 * @author Graeme Rocher
 * @since 3.0.6
 * @see GrailsApp#isContextHierarchyMember()
 */
@CompileStatic
class GrailsAppBuilder extends SpringApplicationBuilder {

    private GrailsAppBuilder parent
    private ConfigurableApplicationContext existingContext
    private final Map<String, Object> defaultProperties = new LinkedHashMap<>()
    private final Set<String> additionalProfiles = new LinkedHashSet<>()
    private ConfigurableEnvironment environment
    private boolean registerShutdownHookApplied
    private boolean configuredAsChild

    /**
     * Create a new builder for a {@link GrailsApp} that loads beans from the given sources.
     * @param sources the bean sources
     */
    GrailsAppBuilder(Class<?>... sources) {
        this(null, sources)
    }

    /**
     * Create a new builder for a {@link GrailsApp} that loads beans from the given sources through
     * the given resource loader.
     * @param resourceLoader the resource loader to use
     * @param sources the bean sources
     */
    GrailsAppBuilder(ResourceLoader resourceLoader, Class<?>... sources) {
        super(resourceLoader, sources)
    }

    @Override
    protected SpringApplication createSpringApplication(ResourceLoader resourceLoader, Class<?>... sources) {
        return new GrailsApp(resourceLoader, sources)
    }

    /**
     * @return the {@link GrailsApp} this builder configures
     */
    @Override
    GrailsApp application() {
        return (GrailsApp) super.application()
    }

    @Override
    ConfigurableApplicationContext context() {
        return existingContext ?: super.context()
    }

    @Override
    ConfigurableApplicationContext run(String... args) {
        if (existingContext != null) {
            return existingContext
        }
        configureAsChildIfNecessary(args)
        return super.run(args)
    }

    /**
     * @return the fully configured {@link GrailsApp}, ready to run
     */
    @Override
    GrailsApp build() {
        return (GrailsApp) super.build()
    }

    /**
     * Builds the {@link GrailsApp} with the given arguments, running the parent first when this
     * builder has one.
     * @param args the arguments to run the parent with, if any
     * @return the fully configured {@link GrailsApp}, ready to run
     */
    @Override
    GrailsApp build(String... args) {
        configureAsChildIfNecessary(args)
        return (GrailsApp) super.build(args)
    }

    /**
     * Creates a child of this builder whose context has the context this builder creates as its
     * parent, as {@link SpringApplicationBuilder#child(Class[])} does, except that the child is a
     * {@code GrailsAppBuilder} running a {@link GrailsApp}. The default properties, environment and
     * additional profiles configured so far are copied to the child.
     *
     * <p>As in Spring Boot, a parent cannot host an embedded web server, because the servlets could
     * not be initialized at the right point of the child's lifecycle: this builder is switched to
     * {@link WebApplicationType#NONE} and its banner is turned off. The Grails application class
     * therefore belongs in the child when the application serves web requests.</p>
     *
     * @param sources the sources of the child context
     * @return the child builder
     */
    @Override
    GrailsAppBuilder child(Class<?>... sources) {
        GrailsAppBuilder child = new GrailsAppBuilder()
        child.sources(sources)
        // copy the environment configured so far to the child, before it has a parent to propagate to
        child.properties(defaultProperties)
                .environment(environment)
                .profiles(additionalProfiles as String[])
        child.parent = this
        web(WebApplicationType.NONE)
        bannerMode(Banner.Mode.OFF)
        markHierarchyMembers(this, child)
        return child
    }

    /**
     * Creates a parent for this builder, or adds sources to the parent already created. The parent
     * runs before this application, without a web server, and its context becomes the parent of
     * this application's context. Returns the parent builder, as
     * {@link SpringApplicationBuilder#parent(Class[])} does; call {@link #run(String[])} on the
     * child to start both.
     *
     * @param sources the sources of the parent context
     * @return the parent builder
     */
    @Override
    GrailsAppBuilder parent(Class<?>... sources) {
        if (parent == null) {
            parent = new GrailsAppBuilder(sources)
                    .web(WebApplicationType.NONE)
                    .properties(defaultProperties)
                    .environment(environment)
            markHierarchyMembers(this, parent)
        }
        else {
            parent.sources(sources)
        }
        return parent
    }

    /**
     * Uses an application context that is already running as the parent of the context this
     * builder creates. Returns this builder, as
     * {@link SpringApplicationBuilder#parent(ConfigurableApplicationContext)} does.
     *
     * @param parentContext the running parent context
     * @return this builder
     */
    @Override
    GrailsAppBuilder parent(ConfigurableApplicationContext parentContext) {
        GrailsAppBuilder parentBuilder = new GrailsAppBuilder()
        parentBuilder.existingContext = parentContext
        parent = parentBuilder
        markHierarchyMembers(this)
        return this
    }

    /**
     * Runs this application if it is not running yet and creates another child of its parent.
     *
     * @param sources the sources of the sibling context
     * @return the sibling builder
     * @throws IllegalStateException if this builder has no parent
     */
    @Override
    GrailsAppBuilder sibling(Class<?>... sources) {
        return runAndExtractParent().child(sources)
    }

    /**
     * Runs this application with the given arguments if it is not running yet and creates another
     * child of its parent.
     *
     * @param sources the sources of the sibling context
     * @param args the arguments to run this application with
     * @return the sibling builder
     * @throws IllegalStateException if this builder has no parent
     */
    @Override
    GrailsAppBuilder sibling(Class<?>[] sources, String... args) {
        return runAndExtractParent(args).child(sources)
    }

    @Override
    GrailsAppBuilder contextFactory(ApplicationContextFactory factory) {
        super.contextFactory(factory)
        return this
    }

    @Override
    GrailsAppBuilder sources(Class<?>... sources) {
        super.sources(sources)
        return this
    }

    @Override
    GrailsAppBuilder web(WebApplicationType webApplicationType) {
        super.web(webApplicationType)
        return this
    }

    @Override
    GrailsAppBuilder logStartupInfo(boolean logStartupInfo) {
        super.logStartupInfo(logStartupInfo)
        return this
    }

    @Override
    GrailsAppBuilder banner(Banner banner) {
        super.banner(banner)
        return this
    }

    @Override
    GrailsAppBuilder bannerMode(Banner.Mode bannerMode) {
        super.bannerMode(bannerMode)
        return this
    }

    @Override
    GrailsAppBuilder headless(boolean headless) {
        super.headless(headless)
        return this
    }

    /**
     * Sets whether the application registers a JVM shutdown hook. A child registers none unless this
     * is called, because closing its parent closes it.
     */
    @Override
    GrailsAppBuilder registerShutdownHook(boolean registerShutdownHook) {
        registerShutdownHookApplied = true
        super.registerShutdownHook(registerShutdownHook)
        return this
    }

    @Override
    GrailsAppBuilder main(Class<?> mainApplicationClass) {
        super.main(mainApplicationClass)
        return this
    }

    @Override
    GrailsAppBuilder addCommandLineProperties(boolean addCommandLineProperties) {
        super.addCommandLineProperties(addCommandLineProperties)
        return this
    }

    @Override
    GrailsAppBuilder setAddConversionService(boolean addConversionService) {
        super.setAddConversionService(addConversionService)
        return this
    }

    @Override
    GrailsAppBuilder addBootstrapRegistryInitializer(BootstrapRegistryInitializer bootstrapRegistryInitializer) {
        super.addBootstrapRegistryInitializer(bootstrapRegistryInitializer)
        return this
    }

    @Override
    GrailsAppBuilder lazyInitialization(boolean lazyInitialization) {
        super.lazyInitialization(lazyInitialization)
        return this
    }

    @Override
    GrailsAppBuilder properties(String... defaultProperties) {
        super.properties(defaultProperties)
        return this
    }

    @Override
    GrailsAppBuilder properties(Properties defaultProperties) {
        super.properties(defaultProperties)
        return this
    }

    /**
     * Adds default properties, and shares them with the parent when this builder has one, as
     * {@link SpringApplicationBuilder#properties(Map)} does.
     */
    @Override
    GrailsAppBuilder properties(Map<String, Object> defaults) {
        defaultProperties.putAll(defaults)
        super.properties(defaults)
        if (parent != null) {
            parent.properties(defaultProperties)
            parent.environment(environment)
        }
        return this
    }

    @Override
    GrailsAppBuilder profiles(String... profiles) {
        additionalProfiles.addAll(Arrays.asList(profiles))
        super.profiles(profiles)
        return this
    }

    @Override
    GrailsAppBuilder beanNameGenerator(BeanNameGenerator beanNameGenerator) {
        super.beanNameGenerator(beanNameGenerator)
        return this
    }

    @Override
    GrailsAppBuilder environment(ConfigurableEnvironment environment) {
        this.environment = environment
        super.environment(environment)
        return this
    }

    @Override
    GrailsAppBuilder environmentPrefix(String environmentPrefix) {
        super.environmentPrefix(environmentPrefix)
        return this
    }

    @Override
    GrailsAppBuilder resourceLoader(ResourceLoader resourceLoader) {
        super.resourceLoader(resourceLoader)
        return this
    }

    @Override
    GrailsAppBuilder initializers(ApplicationContextInitializer<?>... initializers) {
        super.initializers(initializers)
        return this
    }

    @Override
    GrailsAppBuilder listeners(ApplicationListener<?>... listeners) {
        super.listeners(listeners)
        return this
    }

    @Override
    GrailsAppBuilder applicationStartup(ApplicationStartup applicationStartup) {
        super.applicationStartup(applicationStartup)
        return this
    }

    @Override
    GrailsAppBuilder allowCircularReferences(boolean allowCircularReferences) {
        super.allowCircularReferences(allowCircularReferences)
        return this
    }

    /**
     * Runs the parent first when this builder has one and makes this application its child: the
     * parent's context becomes the parent of the context about to be created, and this application
     * registers no shutdown hook of its own unless one was asked for, because closing the parent
     * closes it. The same preparation {@code SpringApplicationBuilder} performs for its own children,
     * done here because the parent is this builder's, not the superclass's.
     */
    private void configureAsChildIfNecessary(String... args) {
        if (parent != null && !configuredAsChild) {
            configuredAsChild = true
            if (!registerShutdownHookApplied) {
                application().setRegisterShutdownHook(false)
            }
            initializers(new ParentContextApplicationContextInitializer(parent.run(args)))
        }
    }

    private GrailsAppBuilder runAndExtractParent(String... args) {
        if (context() == null) {
            run(args)
        }
        if (parent != null) {
            return parent
        }
        throw new IllegalStateException('No parent defined yet (please use the other overloaded parent methods to set one)')
    }

    private static void markHierarchyMembers(GrailsAppBuilder... builders) {
        for (GrailsAppBuilder builder in builders) {
            builder.application().contextHierarchyMember = true
        }
    }

}
