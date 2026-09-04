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
package grails.boot.config

import java.lang.reflect.Field

import groovy.transform.CompileStatic

import org.springframework.aop.config.AopConfigUtils
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.aot.AotDetector
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

import grails.boot.config.tools.ClassPathScanner
import grails.config.Config
import grails.core.GrailsApplication
import grails.core.GrailsApplicationClass
import org.apache.grails.core.plugins.PluginDiscovery
import org.grails.spring.aop.autoproxy.GroovyAwareAspectJAwareAdvisorAutoProxyCreator
import org.apache.grails.core.aot.ArtefactClassesBeanFactoryInitializationAotProcessor
import org.grails.spring.aop.autoproxy.GroovyAwareInfrastructureAdvisorAutoProxyCreator

/**
 * A base class for configurations that bootstrap a Grails application
 *
 * @since 3.0
 * @author Graeme Rocher
 *
 */
@CompileStatic
// WARNING: Never add logging to the source of this class, early initialization causes problems
class GrailsAutoConfiguration implements GrailsApplicationClass, ApplicationContextAware {

    private static final String APC_PRIORITY_LIST_FIELD = 'APC_PRIORITY_LIST'

    static {
        try {
            // patch AopConfigUtils if possible
            Field field = AopConfigUtils.getDeclaredField(APC_PRIORITY_LIST_FIELD)
            if (field != null) {
                field.setAccessible(true)
                Object obj = field.get(null)
                List<Class<?>> list = (List<Class<?>>) obj
                list.add(GroovyAwareInfrastructureAdvisorAutoProxyCreator)
                list.add(GroovyAwareAspectJAwareAdvisorAutoProxyCreator)
            }
        } catch (Throwable ignored) {
        }
    }

    ApplicationContext applicationContext

    /**
     * @return A post processor that uses the {@link grails.plugins.GrailsPluginManager} to configure the {@link org.springframework.context.ApplicationContext}
     */
    @Bean
    GrailsApplicationPostProcessor grailsApplicationPostProcessor(PluginDiscovery pluginDiscovery) {
        return new GrailsApplicationPostProcessor(this, applicationContext, pluginDiscovery, classes() as Class[])
    }

    /**
     * The classes that constitute the Grails application.
     *
     * <p>The context is set before this is asked, on both paths that ask it: the early registration
     * phase sets it on the instance it creates, and the {@code @Bean} method below is invoked on
     * this configuration class after Spring has applied {@link ApplicationContextAware}. It has to
     * be -- the scan below resolves its resources through the context, and did so before any of
     * this was written -- so reading a singleton from it here adds no ordering that was not
     * already relied upon.</p>
     *
     * @return The classes that constitute the Grails application
     */
    Collection<Class> classes() {
        Collection<Class> written = artefactsWrittenDownAheadOfTime()
        if (written != null) {
            return written
        }

        if (limitScanningToApplication()) {
            return ApplicationArtefactScanner.scanApplicationClasses(getClass(), packageNames())
        }

        Collection<Class> classes = new HashSet()
        classes.addAll(new ClassPathScanner().scan(new PathMatchingResourcePatternResolver(applicationContext), packageNames()))
        classes.addAll(ApplicationArtefactScanner.loadTransformedClasses(getClass().classLoader))
        return classes
    }

    /**
     * The artefacts written down while the application's code was generated, or {@code null} where
     * nothing was written down and they are to be found the usual ways.
     *
     * <p>Both usual ways need something an image does not have: one walks the classpath, the other
     * reads a list the compile-time transform builds as it goes, which is empty in anything the
     * transform did not itself compile. So an image found no artefacts at all, and an application
     * could only start by naming its own -- a list to keep in step with itself forever after.</p>
     *
     * <p>They were found while the code was generated, on an ordinary JVM where both ways work, and
     * left here.</p>
     */
    protected Collection<Class> artefactsWrittenDownAheadOfTime() {
        if (applicationContext == null || !AotDetector.useGeneratedArtifacts()) {
            return null
        }
        // Read from the bean factory rather than through getAutowireCapableBeanFactory(), which
        // refuses a context that has not been refreshed. This is asked while the definitions are
        // still being contributed, which is why the classes are left as a singleton in the first
        // place, and asking must not depend on how far the context has got.
        Object written = applicationContext instanceof ConfigurableApplicationContext
                ? ((ConfigurableApplicationContext) applicationContext).beanFactory
                        .getSingleton(ArtefactClassesBeanFactoryInitializationAotProcessor.BEAN_NAME)
                : null
        written instanceof Class[] ? Arrays.asList((Class[]) written) : null
    }

    /**
     * Whether classpath scanning should be limited to the application and not dependent JAR files. Users can override this method to enable more broad scanning
     * at the cost of startup time.
     *
     * @return True if scanning should be limited to the application and should not include dependant JAR files
     */
    protected boolean limitScanningToApplication() {
        return true
    }

    /**
     * @return The packages to scan
     */
    Collection<Package> packages() {
        def thisPackage = getClass().package
        thisPackage ? [ thisPackage ] : new ArrayList<Package>()
    }

    /**
     * @return The package names to scan. Delegates to {@link #packages()} by default
     */
    Collection<String> packageNames() {
        packages().collect { Package p -> p.name }
    }

    @Override
    Closure doWithSpring() { null }

    @Override
    void doWithDynamicMethods() {
        // no-op
    }

    @Override
    void doWithApplicationContext() {
        // no-op
    }

    @Override
    void onConfigChange(Map<String, Object> event) {
        // no-op
    }

    @Override
    void onStartup(Map<String, Object> event) {
        // no-op
    }

    @Override
    void onShutdown(Map<String, Object> event) {
        // no-op
    }

    GrailsApplication getGrailsApplication() {
        applicationContext.getBean(GrailsApplication)
    }

    Config getConfig() {
        grailsApplication.config
    }

}
