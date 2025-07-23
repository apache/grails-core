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
package org.grails.spring;

import grails.spring.BeanBuilder;
import grails.util.CollectionUtils;
import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.Script;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import grails.core.GrailsApplication;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.ClassUtils;

/**
 * @since 2.4
 * @author Graeme Rocher
 */
public class RuntimeSpringConfigUtilities {

    private static final Log LOG = LogFactory.getLog(RuntimeSpringConfigUtilities.class);
    public static final String GRAILS_URL_MAPPINGS = "grailsUrlMappings";
    public static final String SPRING_RESOURCES_XML = "classpath:spring/resources.xml";
    public static final String SPRING_RESOURCES_GROOVY = "classpath:spring/resources.groovy";
    public static final String SPRING_RESOURCES_CLASS = "resources";

    private static final String DEVELOPMENT_SPRING_RESOURCES_XML = "file:./grails-app/conf/spring/resources.xml";


    private static volatile BeanBuilder springGroovyResourcesBeanBuilder = null;

    private RuntimeSpringConfigUtilities() {
    }

    /**
     * Attempt to load the beans defined by a BeanBuilder DSL closure in "resources.groovy".
     *
     * @param config
     * @param context
     */
    private static void doLoadSpringGroovyResources(RuntimeSpringConfiguration config, GrailsApplication application,
                                                    GenericApplicationContext context) {
        loadExternalSpringConfig(config, application);
        if (context != null) {
            springGroovyResourcesBeanBuilder.registerBeans(context);
        }
    }

    /**
     * Loads any external Spring configuration into the given RuntimeSpringConfiguration object.
     * @param config The config instance
     */
    public static void loadExternalSpringConfig(RuntimeSpringConfiguration config, final GrailsApplication application) {
        if (springGroovyResourcesBeanBuilder == null) {
            try {
                Class<?> groovySpringResourcesClass = null;
                try {
                    groovySpringResourcesClass = ClassUtils.forName(SPRING_RESOURCES_CLASS,
                        application.getClassLoader());
                }
                catch (ClassNotFoundException e) {
                    // ignore
                }
                if (groovySpringResourcesClass != null) {
                    reloadSpringResourcesConfig(config, application, groovySpringResourcesClass);
                }
            }
            catch (Exception ex) {
                LOG.error("[RuntimeConfiguration] Unable to load beans from resources.groovy", ex);
            }
        }
        else {
            if (!springGroovyResourcesBeanBuilder.getSpringConfig().equals(config)) {
                springGroovyResourcesBeanBuilder.registerBeans(config);
            }
        }
    }

    public static BeanBuilder reloadSpringResourcesConfig(RuntimeSpringConfiguration config, GrailsApplication application, Class<?> groovySpringResourcesClass) throws InstantiationException, IllegalAccessException {
        springGroovyResourcesBeanBuilder = new BeanBuilder(null, config,Thread.currentThread().getContextClassLoader());
        springGroovyResourcesBeanBuilder.setBinding(new Binding(CollectionUtils.newMap(
            "application", application,
            "grailsApplication", application))); // GRAILS-7550
        Script script = (Script) groovySpringResourcesClass.newInstance();
        script.run();
        Object beans = script.getProperty("beans");
        springGroovyResourcesBeanBuilder.beans((Closure<?>)beans);
        return springGroovyResourcesBeanBuilder;
    }

    public static void loadSpringGroovyResources(RuntimeSpringConfiguration config, GrailsApplication application) {
        loadExternalSpringConfig(config, application);
    }

    public static void loadSpringGroovyResourcesIntoContext(RuntimeSpringConfiguration config, GrailsApplication application,
                                                            GenericApplicationContext context) {
        loadExternalSpringConfig(config, application);
        doLoadSpringGroovyResources(config, application, context);
    }


    /**
     * Resets the GrailsRumtimeConfigurator.
     */
    public static void reset() {
        springGroovyResourcesBeanBuilder = null;
    }
}
