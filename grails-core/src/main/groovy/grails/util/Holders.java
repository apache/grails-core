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
package grails.util;

import grails.config.Config;
import grails.core.GrailsApplication;
import grails.plugins.GrailsPluginManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.grails.core.io.support.GrailsFactoriesLoader;
import org.grails.core.support.GrailsApplicationDiscoveryStrategy;
import org.springframework.context.ApplicationContext;
import org.springframework.context.Lifecycle;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Allows looking up key classes in a static context
 *
 *
 * @author Burt Beckwith
 * @author Graeme Rocher
 *
 * @since 2.0
 */
public class Holders {

    private static final Log LOG = LogFactory.getLog(Holders.class);
    private static Holder<GrailsPluginManager> pluginManagers = new Holder<GrailsPluginManager>("PluginManager");
    private static Holder<Boolean> pluginManagersInCreation = new Holder<Boolean>("PluginManagers in creation");
    private static Holder<Config> configs = new Holder<Config>("config");
    private static Holder<Map<?, ?>> flatConfigs = new Holder<Map<?, ?>>("flat config");

    private static List<GrailsApplicationDiscoveryStrategy> applicationDiscoveryStrategies = GrailsFactoriesLoader.loadFactories(GrailsApplicationDiscoveryStrategy.class, Holders.class.getClassLoader());
    private static Holder servletContexts;
    static {

        createServletContextsHolder();
    }

    private static GrailsApplication applicationSingleton; // TODO remove

    private Holders() {
        // static only
    }

    public static void addApplicationDiscoveryStrategy(GrailsApplicationDiscoveryStrategy strategy) {
        applicationDiscoveryStrategies.add(strategy);
    }

    public static void clear() {
        pluginManagers.set(null);
        pluginManagersInCreation.set(null);
        configs.set(null);
        flatConfigs.set(null);
        if (servletContexts != null) {
            servletContexts.set(null);
        }
        applicationDiscoveryStrategies.clear();
        applicationSingleton = null;
    }

    public static void setServletContext(final Object servletContext) {
        servletContexts.set(servletContext);
    }

    public static Object getServletContext() {
        return get(servletContexts, "servletContext");
    }

    public static ApplicationContext getApplicationContext() {
        for(GrailsApplicationDiscoveryStrategy strategy : applicationDiscoveryStrategies) {
            ApplicationContext applicationContext = strategy.findApplicationContext();
            if(applicationContext != null ) {
                boolean running = ((Lifecycle) applicationContext).isRunning();
                if(running) {
                    return applicationContext;
                }
            }
        }
        throw new IllegalStateException("Could not find ApplicationContext, configure Grails correctly first");
    }

    /**
     *
     * @return The ApplicationContext or null if it doesn't exist
     */
    public static ApplicationContext findApplicationContext() {
        for(GrailsApplicationDiscoveryStrategy strategy : applicationDiscoveryStrategies) {
            ApplicationContext applicationContext = strategy.findApplicationContext();
            if(applicationContext != null) {
                return applicationContext;
            }
        }
        return null;
    }
    /**
     *
     * @return The ApplicationContext or null if it doesn't exist
     */
    public static GrailsApplication findApplication() {
        for(GrailsApplicationDiscoveryStrategy strategy : applicationDiscoveryStrategies) {
            GrailsApplication grailsApplication = strategy.findGrailsApplication();
            if(grailsApplication != null) {
                return grailsApplication;
            }
        }
        return applicationSingleton;
    }

    public static GrailsApplication getGrailsApplication() {
        GrailsApplication grailsApplication = findApplication();
        Assert.notNull(grailsApplication, "GrailsApplication not found");
        return grailsApplication;
    }

    public static void setGrailsApplication(GrailsApplication application) {
        applicationSingleton = application;
    }

    public static void setConfig(Config config) {
        configs.set(config);

        // reset flat config
        flatConfigs.set(config == null ? null : config);
    }

    public static Config getConfig() {
        return get(configs, "config");
    }

    public static Map<?, ?> getFlatConfig() {
        Map<?, ?> flatConfig = get(flatConfigs, "flatConfig");
        return flatConfig == null ? Collections.emptyMap() : flatConfig;
    }

    public static void setPluginManagerInCreation(boolean inCreation) {
        pluginManagersInCreation.set(inCreation);
    }

    public static void setPluginManager(GrailsPluginManager pluginManager) {
        if (pluginManager != null) {
            pluginManagersInCreation.set(false);
        }
        pluginManagers.set(pluginManager);
    }

    public static GrailsPluginManager getPluginManager() {
        return getPluginManager(false);
    }

    public static GrailsPluginManager getPluginManager(boolean mappedOnly) {
        while (true) {
            Boolean inCreation = get(pluginManagersInCreation, "PluginManager in creation", mappedOnly);
            if (inCreation == null) {
                inCreation = false;
            }
            if (!inCreation) {
                break;
            }

            try {
                Thread.sleep(100);
            }
            catch (InterruptedException e) {
                break;
            }
        }

        return get(pluginManagers, "PluginManager", mappedOnly);
    }

    public static GrailsPluginManager currentPluginManager() {
        GrailsPluginManager current = getPluginManager();
        Assert.notNull(current, "No PluginManager set");
        return current;
    }

    public static void reset() {
        setPluginManager(null);
        setGrailsApplication(null);
        setServletContext(null);
        setPluginManager(null);
        setPluginManagerInCreation(false);
        setConfig(null);
    }

    private static <T> T get(Holder<T> holder, String type) {
        return get(holder, type, false);
    }

    private static <T> T get(Holder<T> holder, String type, boolean mappedOnly) {
        return holder.get(mappedOnly);
    }

    @SuppressWarnings("unchecked")
    private static void createServletContextsHolder() {
        try {
            Class<?> clazz = Holders.class.getClassLoader().loadClass("grails.web.context.WebRequestServletHolder");
            servletContexts = (Holder)clazz.newInstance();
        }
        catch (ClassNotFoundException e) {
            // shouldn't happen
            LOG.debug("Error initializing servlet context holder, not running in Servlet environment: " + e.getMessage(), e);
        }
        catch (InstantiationException e) {
            // shouldn't happen
            LOG.debug("Error initializing servlet context holder, not running in Servlet environment: " + e.getMessage(), e);
        }
        catch (IllegalAccessException e) {
            // shouldn't happen
            LOG.debug("Error initializing servlet context holder, not running in Servlet environment: " + e.getMessage(), e);
        }
    }
}
