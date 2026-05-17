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

import java.io.PrintWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;

import grails.config.Config;
import grails.config.Settings;
import grails.core.GrailsApplication;
import org.grails.exceptions.reporting.DefaultStackTraceFilterer;
import org.grails.exceptions.reporting.StackTraceFilterer;

/**
 * Grails utility methods for command line and GUI applications.
 *
 * @author Graeme Rocher
 * @since 0.2
 */
public class GrailsUtil {

    private static final Log LOG = LogFactory.getLog(GrailsUtil.class);
    private static final boolean LOG_DEPRECATED = Boolean.valueOf(System.getProperty("grails.log.deprecated", String.valueOf(Environment.isDevelopmentMode())));

    /**
     * Lazily-resolved filterer used by {@link #printSanitizedStackTrace}, {@link #sanitizeRootCause}
     * and {@link #deepSanitize}. Cached once a {@link GrailsApplication} is discoverable via
     * {@link Holders#findApplication()} so the config-driven class and emission flag are read
     * exactly once. Volatile to publish the cached value safely; double-checked init in
     * {@link #resolveStackFilterer()}.
     */
    private static volatile StackTraceFilterer stackFilterer;

    private GrailsUtil() {
    }

    /**
     * Retrieves whether the current execution environment is the development one.
     *
     * @return true if it is the development environment
     */
    public static boolean isDevelopmentEnv() {
        return Environment.getCurrent().equals(Environment.DEVELOPMENT);
    }

    public static String getGrailsVersion() {
        return Environment.getGrailsVersion();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }

    /**
     * Logs warning message about deprecation of specified property or method of some class.
     *
     * @param clazz A class
     * @param methodOrPropName Name of deprecated property or method
     */
    public static void deprecated(Class<?> clazz, String methodOrPropName) {
        deprecated(clazz, methodOrPropName, getGrailsVersion());
    }

    /**
     * Logs warning message about deprecation of specified property or method of some class.
     *
     * @param clazz A class
     * @param methodOrPropName Name of deprecated property or method
     * @param version Version of Grails release in which property or method were deprecated
     */
    public static void deprecated(Class<?> clazz, String methodOrPropName, String version) {
        if (LOG_DEPRECATED) {
            deprecated("Property or method [" + methodOrPropName + "] of class [" + clazz.getName() +
                    "] is deprecated in [" + version +
                    "] and will be removed in future releases");
        }
    }

    /**
     * Logs warning message about some deprecation and code style related hints.
     *
     * @param message Message to display
     */
    public static void deprecated(String message) {
        if (LOG_DEPRECATED && LOG.isWarnEnabled()) {
            LOG.warn("[DEPRECATED] " + message);
        }
    }

    /**
     * Logs warning message to grails.util.GrailsUtil logger which is turned on in development mode.
     *
     * @param message Message to display
     */
    public static void warn(String message) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("[WARNING] " + message);
        }
    }

    public static void printSanitizedStackTrace(Throwable t, PrintWriter p) {
        printSanitizedStackTrace(t, p, resolveStackFilterer());
    }

    public static void printSanitizedStackTrace(Throwable t, PrintWriter p, StackTraceFilterer stackTraceFilterer) {
        t = stackTraceFilterer.filter(t);

        StackTraceElement[] trace = t.getStackTrace();
        for (StackTraceElement stackTraceElement : trace) {
            p.println("at " + stackTraceElement.getClassName() +
                      "(" + stackTraceElement.getMethodName() +
                      ":" + stackTraceElement.getLineNumber() + ")");
        }
    }

    public static void printSanitizedStackTrace(Throwable t) {
        printSanitizedStackTrace(t, new PrintWriter(System.err));
    }

    /**
     * <p>Extracts the root cause of the exception, no matter how nested it is</p>
     * @param t
     * @return The deepest cause of the exception that can be found
     */
    public static Throwable extractRootCause(Throwable t) {
        Throwable result = t;
        while (result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }

    /**
     * <p>Get the root cause of an exception and sanitize it for display to the user</p>
     * <p>This will MODIFY the stacktrace of the root cause exception object and return it</p>
     * @param t
     * @return The root cause exception instance, with its stace trace modified to filter out grails runtime classes
     */
    public static Throwable sanitizeRootCause(Throwable t) {
        return resolveStackFilterer().filter(extractRootCause(t));
    }

    /**
     * <p>Sanitize the exception and ALL nested causes</p>
     * <p>This will MODIFY the stacktrace of the exception instance and all its causes irreversibly</p>
     * @param t
     * @return The root cause exception instances, with stack trace modified to filter out grails runtime classes
     */
    public static Throwable deepSanitize(Throwable t) {
        return resolveStackFilterer().filter(t, true);
    }

    /**
     * Returns the {@link StackTraceFilterer} used by this class, lazily initialised from the
     * Grails application config when one is discoverable. Honours
     * {@link Settings#SETTING_LOGGING_STACKTRACE_FILTER_CLASS} (the filterer class — same key
     * the exception resolver consults) and propagates
     * {@link Settings#SETTING_LOG_FULL_STACKTRACE_ON_FILTER} to instances of
     * {@link DefaultStackTraceFilterer}.
     *
     * <p>While no {@link GrailsApplication} is available (early-init paths, plain {@code main}
     * usage, tests that don't wire one up) a fresh {@link DefaultStackTraceFilterer} is returned
     * and <em>not</em> cached — so once the application context boots, the next call resolves
     * the configured filterer for real. After that the value is cached for the lifetime of the
     * JVM, matching the historical behaviour of the previous {@code static final} field.
     */
    private static StackTraceFilterer resolveStackFilterer() {
        StackTraceFilterer cached = stackFilterer;
        if (cached != null) {
            return cached;
        }
        GrailsApplication application = findApplicationQuietly();
        if (application == null) {
            // No application discoverable yet — return an uncached default. A later call,
            // once the context is up, will run through the configured-resolution branch
            // and populate the cache.
            return new DefaultStackTraceFilterer();
        }
        synchronized (GrailsUtil.class) {
            cached = stackFilterer;
            if (cached != null) {
                return cached;
            }
            stackFilterer = createConfiguredFilterer(application);
            return stackFilterer;
        }
    }

    private static GrailsApplication findApplicationQuietly() {
        try {
            return Holders.findApplication();
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private static StackTraceFilterer createConfiguredFilterer(GrailsApplication application) {
        Class<? extends StackTraceFilterer> filtererClass = DefaultStackTraceFilterer.class;
        boolean logOnFilter = true;
        try {
            Config config = application.getConfig();
            if (config != null) {
                filtererClass = config.getProperty(
                        Settings.SETTING_LOGGING_STACKTRACE_FILTER_CLASS,
                        Class.class, DefaultStackTraceFilterer.class);
                logOnFilter = config.getProperty(
                        Settings.SETTING_LOG_FULL_STACKTRACE_ON_FILTER,
                        Boolean.class, true);
            }
        }
        catch (Throwable t) {
            LOG.warn("Unable to resolve StackTraceFilterer config; using default: " + t.getMessage());
        }
        StackTraceFilterer instance;
        try {
            instance = BeanUtils.instantiateClass(filtererClass, StackTraceFilterer.class);
        }
        catch (Throwable t) {
            LOG.warn("Problem instantiating configured StackTraceFilterer [" + filtererClass.getName() +
                    "], falling back to default: " + t.getMessage());
            instance = new DefaultStackTraceFilterer();
        }
        if (instance instanceof DefaultStackTraceFilterer) {
            ((DefaultStackTraceFilterer) instance).setLogFullStackTraceOnFilter(logOnFilter);
        }
        return instance;
    }

}
