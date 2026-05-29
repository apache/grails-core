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
     * Default filterer used before {@link #initializeStackFilterer(GrailsApplication)} runs (CLI,
     * tests that don't boot a context, plain {@code main()} usage). Preserves the pre-PR behaviour
     * of a single hardcoded {@link DefaultStackTraceFilterer} instance for the JVM lifetime when no
     * application is wired.
     */
    private static final StackTraceFilterer FALLBACK_FILTERER = new DefaultStackTraceFilterer();

    /**
     * Active filterer for {@link #printSanitizedStackTrace}, {@link #sanitizeRootCause} and
     * {@link #deepSanitize}. Starts as {@link #FALLBACK_FILTERER} and is replaced with a
     * config-driven instance when {@link #initializeStackFilterer(GrailsApplication)} runs during
     * Grails bootstrap. Volatile so the bootstrap-time write publishes safely to the request
     * threads that read it later.
     */
    private static volatile StackTraceFilterer stackFilterer = FALLBACK_FILTERER;

    private GrailsUtil() {
    }

    /**
     * Installs a {@link StackTraceFilterer} resolved from the given application's config, replacing
     * the default fallback. Reads {@link Settings#SETTING_LOGGING_STACKTRACE_FILTER_CLASS} for the
     * filterer class and propagates {@link Settings#SETTING_LOG_FULL_STACKTRACE_ON_FILTER} to
     * instances of {@link DefaultStackTraceFilterer}. Called by {@code GrailsExceptionResolver}
     * during Spring bean wiring (which is the same point the resolver consults these keys for its
     * own filterer), so request-time callers of the static {@code sanitize}/{@code deepSanitize}
     * methods see the configured instance.
     *
     * <p>No-ops when {@code application} is null. Safe to call more than once — the last successful
     * invocation wins.
     *
     * @since 7.1.2
     */
    public static void initializeStackFilterer(GrailsApplication application) {
        if (application == null) {
            return;
        }
        StackTraceFilterer instance = createConfiguredFilterer(application);
        if (instance != null) {
            stackFilterer = instance;
        }
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
        printSanitizedStackTrace(t, p, stackFilterer);
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
        return stackFilterer.filter(extractRootCause(t));
    }

    /**
     * <p>Sanitize the exception and ALL nested causes</p>
     * <p>This will MODIFY the stacktrace of the exception instance and all its causes irreversibly</p>
     * @param t
     * @return The root cause exception instances, with stack trace modified to filter out grails runtime classes
     */
    public static Throwable deepSanitize(Throwable t) {
        return stackFilterer.filter(t, true);
    }

    private static StackTraceFilterer createConfiguredFilterer(GrailsApplication application) {
        Class<? extends StackTraceFilterer> filtererClass = DefaultStackTraceFilterer.class;
        boolean logOnFilter = true;
        Config config = application.getConfig();
        if (config != null) {
            Class<? extends StackTraceFilterer> configured = config.getProperty(
                    Settings.SETTING_LOGGING_STACKTRACE_FILTER_CLASS,
                    Class.class, DefaultStackTraceFilterer.class);
            if (configured != null) {
                filtererClass = configured;
            }
            Boolean configuredLogOnFilter = config.getProperty(
                    Settings.SETTING_LOG_FULL_STACKTRACE_ON_FILTER,
                    Boolean.class, Boolean.TRUE);
            if (configuredLogOnFilter != null) {
                logOnFilter = configuredLogOnFilter;
            }
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
