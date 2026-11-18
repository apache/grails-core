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
package org.apache.grails.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.boot.bootstrap.BootstrapRegistry;
import org.springframework.boot.bootstrap.BootstrapRegistryInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import grails.config.Settings;
import grails.util.GrailsUtil;
import org.apache.grails.core.plugins.DefaultPluginDiscovery;
import org.apache.grails.core.plugins.PluginDiscovery;
import org.grails.exceptions.reporting.DefaultStackTraceFilterer;
import org.grails.exceptions.reporting.StackTraceFilterer;

/**
 * Registers the {@link PluginDiscovery} in the Spring Boot Bootstrap context so it can be accessed during
 * the early lifecycle of the application & later promoted to actual bean.
 *
 * <p>This ensures that both the early-lifecycle
 * {@link grails.boot.config.GrailsEnvironmentPostProcessor} and the
 * later-lifecycle {@link grails.plugins.DefaultGrailsPluginManager} can
 * access the same discovered, filtered, and sorted set of plugins.</p>
 *
 * <p>Also resolves the configured {@link StackTraceFilterer} from the environment and installs it in
 * {@link GrailsUtil} before the {@code ApplicationContext} refreshes, then promotes the same instance
 * as an {@code ApplicationContext} singleton bean. Doing this here — rather than from a bean's setter,
 * such as {@code GrailsExceptionResolver.setGrailsApplication()} — means every app type is covered
 * (not just apps that wire an exception resolver), and a startup failure honours the configured
 * filterer too.</p>
 *
 * <p>This class is registered via {@code META-INF/spring.factories} under the
 * {@code org.springframework.boot.bootstrap.BootstrapRegistryInitializer} key.</p>
 *
 * @since 7.1
 */
public class GrailsBootstrapRegistryInitializer implements BootstrapRegistryInitializer {

    /**
     * Name under which the config-resolved {@link StackTraceFilterer} is promoted as an
     * {@code ApplicationContext} singleton bean, so later-lifecycle consumers (e.g.
     * {@code GrailsExceptionResolver}) reuse that instance instead of instantiating a second copy
     * from config. Declared here — next to the code that registers the bean — for the same reason
     * {@link PluginDiscovery#BEAN_NAME} is.
     *
     * <p>An application that registers its own bean definition under this name replaces the
     * promoted singleton in the context, but not the instance already installed in
     * {@link GrailsUtil}; see {@code GrailsExceptionResolver.resolvePromotedStackTraceFilterer()}.
     *
     * @since 8.0
     */
    public static final String STACK_TRACE_FILTERER_BEAN_NAME = "stackTraceFilterer";

    private static final Logger LOG = LoggerFactory.getLogger(GrailsBootstrapRegistryInitializer.class);

    @Override
    public void initialize(BootstrapRegistry registry) {
        LOG.debug("Registering GrailsPluginDiscovery in BootstrapRegistry");
        registry.register(PluginDiscovery.class, context -> new DefaultPluginDiscovery());

        // Promote the GrailsPluginDiscovery singleton to the ApplicationContext
        // so that later-lifecycle components (e.g., DefaultGrailsPluginManager)
        // can access it. This fires after the context is prepared but before
        // refresh(), so the bean is available during the full Spring lifecycle.
        registry.addCloseListener(event -> {
            PluginDiscovery discovery = event.getBootstrapContext()
                    .get(PluginDiscovery.class);
            event.getApplicationContext()
                    .getBeanFactory()
                    .registerSingleton(PluginDiscovery.BEAN_NAME, discovery);
            LOG.debug("Promoted GrailsPluginDiscovery to ApplicationContext as '{}'", PluginDiscovery.BEAN_NAME);
        });

        // Resolve the configured StackTraceFilterer from the environment (same two keys
        // GrailsExceptionResolver honours) and install + promote it the same way, before refresh().
        registry.addCloseListener(event -> {
            ConfigurableApplicationContext applicationContext = event.getApplicationContext();
            StackTraceFilterer filterer = resolveConfiguredStackTraceFilterer(applicationContext);
            GrailsUtil.initializeStackFilterer(filterer);
            applicationContext.getBeanFactory()
                    .registerSingleton(STACK_TRACE_FILTERER_BEAN_NAME, filterer);
            LOG.debug("Promoted StackTraceFilterer to ApplicationContext as '{}'", STACK_TRACE_FILTERER_BEAN_NAME);
        });
    }

    /**
     * Resolves a {@link StackTraceFilterer} from the given context's environment, honouring
     * {@link Settings#SETTING_LOGGING_STACKTRACE_FILTER_CLASS} and
     * {@link Settings#SETTING_LOG_FULL_STACKTRACE_ON_FILTER}.
     *
     * <p>Defensive throughout: a misconfigured filterer must never fail application startup, so
     * every config read, class load and instantiation degrades to a plain
     * {@link DefaultStackTraceFilterer} with a logged warning.
     */
    private StackTraceFilterer resolveConfiguredStackTraceFilterer(ConfigurableApplicationContext applicationContext) {
        Environment environment = applicationContext.getEnvironment();
        Class<? extends StackTraceFilterer> filtererClass =
                resolveFiltererClass(environment, applicationContext.getClassLoader());
        boolean logFullStackTraceOnFilter = resolveLogFullStackTraceOnFilter(environment);

        StackTraceFilterer filterer;
        try {
            filterer = BeanUtils.instantiateClass(filtererClass, StackTraceFilterer.class);
        }
        catch (Throwable t) {
            LOG.warn("Problem instantiating configured StackTraceFilterer [{}], falling back to default: {}",
                    filtererClass.getName(), t.getMessage());
            filterer = new DefaultStackTraceFilterer();
        }
        if (filterer instanceof DefaultStackTraceFilterer) {
            ((DefaultStackTraceFilterer) filterer).setLogFullStackTraceOnFilter(logFullStackTraceOnFilter);
        }
        return filterer;
    }

    /**
     * Reads {@link Settings#SETTING_LOGGING_STACKTRACE_FILTER_CLASS} as a raw value and accepts both
     * shapes the key can take. {@code application.groovy} is loaded into a property source that
     * preserves value types, so a class literal
     * ({@code grails.logging.stackTraceFiltererClass = com.example.MyFilterer.class}) arrives as a
     * {@link Class}; YAML and {@code application.properties} supply the class name as a String.
     * Requesting the property as a String would stringify the former to {@code "class com.example.MyFilterer"},
     * and requesting it as {@code Class.class} would fail on the latter — neither Spring's default
     * conversion service nor Spring Boot's {@code ApplicationConversionService} registers a
     * String-to-Class converter.
     *
     * <p>The class name is resolved against the context's own {@code ClassLoader} rather than this
     * class's: under {@code spring-boot-devtools} the application's classes live in a
     * {@code RestartClassLoader} while grails-core stays on the base loader, so a filterer under
     * {@code grails-app} or {@code src/main/groovy} is invisible to the latter.
     */
    private Class<? extends StackTraceFilterer> resolveFiltererClass(Environment environment, ClassLoader classLoader) {
        Object configured;
        try {
            configured = environment.getProperty(Settings.SETTING_LOGGING_STACKTRACE_FILTER_CLASS, Object.class);
        }
        catch (Throwable t) {
            LOG.warn("Problem reading [{}], falling back to the default StackTraceFilterer: {}",
                    Settings.SETTING_LOGGING_STACKTRACE_FILTER_CLASS, t.getMessage());
            return DefaultStackTraceFilterer.class;
        }
        if (configured == null) {
            return DefaultStackTraceFilterer.class;
        }
        try {
            if (configured instanceof Class<?> configuredClass) {
                return configuredClass.asSubclass(StackTraceFilterer.class);
            }
            if (configured instanceof CharSequence configuredName) {
                if (!StringUtils.hasText(configuredName)) {
                    return DefaultStackTraceFilterer.class;
                }
                return ClassUtils.forName(configuredName.toString(), classLoader)
                        .asSubclass(StackTraceFilterer.class);
            }
            LOG.warn("Configured StackTraceFilterer [{}] for [{}] is neither a Class nor a String, falling back to default",
                    configured, Settings.SETTING_LOGGING_STACKTRACE_FILTER_CLASS);
        }
        catch (Throwable t) {
            LOG.warn("Problem loading configured StackTraceFilterer class [{}], falling back to default: {}",
                    configured, t.getMessage());
        }
        return DefaultStackTraceFilterer.class;
    }

    /**
     * Reads {@link Settings#SETTING_LOG_FULL_STACKTRACE_ON_FILTER}, defaulting to {@code true} both
     * when unset and when the configured value cannot be converted to a boolean — a bad value here
     * must not propagate out of the close listener and fail startup.
     */
    private boolean resolveLogFullStackTraceOnFilter(Environment environment) {
        try {
            return environment.getProperty(
                    Settings.SETTING_LOG_FULL_STACKTRACE_ON_FILTER, Boolean.class, Boolean.TRUE);
        }
        catch (Throwable t) {
            LOG.warn("Problem reading [{}], defaulting to true: {}",
                    Settings.SETTING_LOG_FULL_STACKTRACE_ON_FILTER, t.getMessage());
            return true;
        }
    }
}
