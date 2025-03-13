/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.boot.config

import grails.config.Config
import grails.core.GrailsApplication
import grails.boot.config.tools.ClassPathScanner
import grails.core.GrailsApplicationClass
import groovy.transform.CompileStatic
import org.grails.compiler.injection.AbstractGrailsArtefactTransformer
import org.grails.spring.aop.autoproxy.GroovyAwareAspectJAwareAdvisorAutoProxyCreator
import org.grails.spring.aop.autoproxy.GroovyAwareInfrastructureAdvisorAutoProxyCreator
import org.springframework.aop.config.AopConfigUtils
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.annotation.Bean
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

import java.lang.reflect.Field

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

    private static final String APC_PRIORITY_LIST_FIELD = "APC_PRIORITY_LIST"

    static {
        try {
            // patch AopConfigUtils if possible
            Field field = AopConfigUtils.class.getDeclaredField(APC_PRIORITY_LIST_FIELD)
            if(field != null) {
                field.setAccessible(true)
                Object obj = field.get(null)
                List<Class<?>> list = (List<Class<?>>) obj
                list.add(GroovyAwareInfrastructureAdvisorAutoProxyCreator.class)
                list.add(GroovyAwareAspectJAwareAdvisorAutoProxyCreator.class)
            }
        } catch (Throwable e) {
            // ignore
        }
    }

    ApplicationContext applicationContext

    /**
     * @return A post processor that uses the {@link grails.plugins.GrailsPluginManager} to configure the {@link org.springframework.context.ApplicationContext}
     */
    @Bean
    GrailsApplicationPostProcessor grailsApplicationPostProcessor() {
        return new GrailsApplicationPostProcessor( this, applicationContext, classes() as Class[])
    }

    /**
     * @return The classes that constitute the Grails application
     */
    Collection<Class> classes() {
        Collection<Class> classes = new HashSet()

        ClassPathScanner scanner = new ClassPathScanner()
        if(limitScanningToApplication()) {
            classes.addAll scanner.scan(getClass(), packageNames())
        }
        else {
            classes.addAll scanner.scan(new PathMatchingResourcePatternResolver(applicationContext), packageNames())
        }

        ClassLoader classLoader = getClass().getClassLoader()
        for(cls in AbstractGrailsArtefactTransformer.transformedClassNames) {
            try {
                classes << classLoader.loadClass(cls)
            } catch (ClassNotFoundException cnfe) {
                // ignore
            }
        }

        return classes
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

