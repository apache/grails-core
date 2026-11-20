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
package org.grails.exceptions.reporting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Default implementation of StackTraceFilterer.
 *
 * @since 2.0
 * @author Graeme Rocher
 */
public class DefaultStackTraceFilterer implements StackTraceFilterer {
    public static final String STACK_LOG_NAME = "StackTrace";
    /**
     * Dedicated logger for exception stack traces. The filterer emits the unfiltered
     * trace to this logger as a side effect of {@link #filter(Throwable)} — before the
     * trace is trimmed in place — when {@link #logFullStackTraceOnFilter} is {@code true}
     * (the default). {@code GrailsExceptionResolver} also emits to this logger when
     * {@code grails.exceptionresolver.logFullStackTrace} is enabled. Exposed as a public
     * constant so that subclasses and logback configurations can reference the logger
     * name symbolically.
     */
    public static final Log STACK_LOG = LogFactory.getLog(STACK_LOG_NAME);

    private static final String[] DEFAULT_INTERNAL_PACKAGES = new String[] {
        "org.codehaus.groovy.runtime.",
        "org.codehaus.groovy.reflection.",
        "org.codehaus.groovy.ast.",
        "org.springframework.web.filter",
        "org.springframework.boot.actuate",
        "org.mortbay.",
        "groovy.lang.",
        "org.apache.catalina.",
        "org.apache.coyote.",
        "org.apache.tomcat.",
        "net.sf.cglib.proxy.",
        "sun.",
        "java.lang.reflect.",
        "org.springframework.boot.devtools.",
        "org.springsource.loaded.",
        "com.opensymphony.",
        "jakarta.servlet."
    };

    private List<String> packagesToFilter = new ArrayList<>();
    private boolean shouldFilter;
    private String cutOffPackage = null;
    private boolean logFullStackTraceOnFilter = true;

    public DefaultStackTraceFilterer() {
        this(!Boolean.getBoolean(SYS_PROP_DISPLAY_FULL_STACKTRACE));
    }

    public DefaultStackTraceFilterer(boolean shouldFilter) {
        this.shouldFilter = shouldFilter;
        packagesToFilter.addAll(Arrays.asList(DEFAULT_INTERNAL_PACKAGES));
    }

    public void addInternalPackage(String name) {
        if (name == null) throw new IllegalArgumentException("Package name cannot be null");
        packagesToFilter.add(name);
    }

    public void setCutOffPackage(String cutOffPackage) {
        this.cutOffPackage = cutOffPackage;
    }

    /**
     * Controls whether {@link #filter(Throwable)} emits the unfiltered stack trace
     * to {@link #STACK_LOG} as a side effect before trimming the trace in place.
     * Defaults to {@code true} for backwards compatibility with pre-7.1 behaviour;
     * set to {@code false} to disable the side-effect emission. The exception
     * resolver wires this from {@code grails.exceptionresolver.logFullStackTraceOnFilter}.
     */
    public void setLogFullStackTraceOnFilter(boolean logFullStackTraceOnFilter) {
        this.logFullStackTraceOnFilter = logFullStackTraceOnFilter;
    }

    public Throwable filter(Throwable source, boolean recursive) {
        if (recursive) {
            Throwable current = source;
            while (current != null) {
                filter(current);
                current = current.getCause();
            }
            return source;
        }
        return filter(source);
    }

    public Throwable filter(Throwable source) {
        if (!shouldFilter) {
            return source;
        }
        StackTraceElement[] trace = source.getStackTrace();
        List<StackTraceElement> newTrace = filterTraceWithCutOff(trace, cutOffPackage);

        if (newTrace.isEmpty()) {
            // filter with no cut-off so at least there is some trace
            newTrace = filterTraceWithCutOff(trace, null);
        }

        // Only trim the trace if there was some application trace on the stack
        // if not we will just skip sanitizing and leave it as is
        if (!newTrace.isEmpty()) {
            if (logFullStackTraceOnFilter) {
                // emit the unfiltered trace before mutating in place; once setStackTrace(clean)
                // runs the original frames are gone
                STACK_LOG.error(FULL_STACK_TRACE_MESSAGE, source);
            }
            StackTraceElement[] clean = new StackTraceElement[newTrace.size()];
            newTrace.toArray(clean);
            source.setStackTrace(clean);
        }
        return source;
    }

    private List<StackTraceElement> filterTraceWithCutOff(StackTraceElement[] trace, String endPackage) {
        List<StackTraceElement> newTrace = new ArrayList<>();
        boolean foundGroovy = false;
        for (StackTraceElement stackTraceElement : trace) {
            String className = stackTraceElement.getClassName();
            String fileName = stackTraceElement.getFileName();
            if (!foundGroovy && fileName != null && fileName.endsWith(".groovy")) {
                foundGroovy = true;
            }
            if (endPackage != null && className.startsWith(endPackage) && foundGroovy) break;
            if (isApplicationClass(className)) {
                if (stackTraceElement.getLineNumber() > -1) {
                    newTrace.add(stackTraceElement);
                }
            }
        }
        return newTrace;
    }

    /**
     * Whether the given class name is an internal class and should be filtered
     * @param className The class name
     * @return true if is internal
     */
    protected boolean isApplicationClass(String className) {
        for (String packageName : packagesToFilter) {
            if (className.startsWith(packageName)) return false;
        }
        return true;
    }

    public void setShouldFilter(boolean shouldFilter) {
        this.shouldFilter = shouldFilter;
    }
}
