/*
 * Copyright 2004-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.web.servlet.context;

import grails.core.ApplicationAttributes;
import grails.core.GrailsApplication;
import grails.core.GrailsClass;
import grails.persistence.support.PersistenceContextInterceptor;
import grails.plugins.GrailsPluginManager;
import grails.web.servlet.bootstrap.GrailsBootstrapClass;
import org.grails.web.servlet.boostrap.BootstrapArtefactHandler;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.ServletContext;

/**
 * A common class where shared configurational methods can reside.
 *
 * @author Graeme Rocher
 */
public class GrailsConfigUtils {


    /**
     * Executes Grails bootstrap classes
     *
     * @param application The Grails ApplicationContext instance
     * @param webContext The WebApplicationContext instance
     * @param servletContext The ServletContext instance
     */
    @Deprecated
    public static void executeGrailsBootstraps(GrailsApplication application, WebApplicationContext webContext,
                                               ServletContext servletContext) {
        executeGrailsBootstraps(application, webContext, servletContext, null);
    }
    /**
     * Executes Grails bootstrap classes
     *
     * @param application The Grails ApplicationContext instance
     * @param webContext The WebApplicationContext instance
     * @param servletContext The ServletContext instance
     */
    public static void executeGrailsBootstraps(GrailsApplication application, WebApplicationContext webContext,
            ServletContext servletContext, GrailsPluginManager grailsPluginManager) {
        configureServletContextAttributes(
                servletContext,
                application,
                grailsPluginManager,
                webContext
        );

        PersistenceContextInterceptor interceptor = null;
        String[] beanNames = webContext.getBeanNamesForType(PersistenceContextInterceptor.class);
        if (beanNames.length > 0) {
            interceptor = (PersistenceContextInterceptor)webContext.getBean(beanNames[0]);
        }

        if (interceptor != null) {
            interceptor.init();
        }
        // init the Grails application
        try {
            GrailsClass[] bootstraps = application.getArtefacts(BootstrapArtefactHandler.TYPE);
            for (GrailsClass bootstrap : bootstraps) {
                final GrailsBootstrapClass bootstrapClass = (GrailsBootstrapClass) bootstrap;
                final Object instance = bootstrapClass.getReferenceInstance();
                webContext.getAutowireCapableBeanFactory().autowireBeanProperties(
                        instance, AutowireCapableBeanFactory.AUTOWIRE_BY_NAME, false);
                bootstrapClass.callInit();
            }
            if (interceptor != null) {
                interceptor.flush();
            }
        }
        finally {
            if (interceptor != null) {
                interceptor.destroy();
            }
        }
    }



    public static void configureServletContextAttributes(ServletContext servletContext, GrailsApplication application, GrailsPluginManager pluginManager, WebApplicationContext webContext) {
        servletContext.setAttribute(ApplicationAttributes.PLUGIN_MANAGER, pluginManager);
        // use config file locations if available
        servletContext.setAttribute(ApplicationAttributes.PARENT_APPLICATION_CONTEXT, webContext.getParent());
        servletContext.setAttribute(GrailsApplication.APPLICATION_ID, application);

        servletContext.setAttribute(ApplicationAttributes.APPLICATION_CONTEXT, webContext);
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webContext);
    }



    /**
     * Checks if a Config parameter is true or a System property with the same name is true
     *
     * @param application
     * @param propertyName
     * @return true if the Config parameter is true or the System property with the same name is true
     */
    public static boolean isConfigTrue(GrailsApplication application, String propertyName) {
        return application.getConfig().getProperty(propertyName, Boolean.class, false);
    }

    // support GrailsApplication mocking, see ControllersGrailsPluginTests
    public static boolean isConfigTrue(Object application, String propertyName) {
        return false;
    }
}
