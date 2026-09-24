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
package org.grails.compiler.beans;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

/**
 * Matches when the current Grails environment is one of those named by
 * {@code grails.compiler.beans.ConditionalOnGrailsEnv}.
 *
 * <p>The environment comes from {@code grails.util.Environment.getCurrent()}, which is the only
 * answer that accounts for Grails inferring an environment nobody set. That class lives in a module
 * this one is a dependency of, so it is reached reflectively rather than by import; where it is
 * absent the {@code grails.env} property is consulted instead, which covers the case the property
 * was designed for - an explicitly set environment - and leaves the condition unmatched otherwise
 * rather than guessing.</p>
 */
public class OnGrailsEnvCondition implements Condition {

    private static final String ENVIRONMENT_CLASS = "grails.util.Environment";
    private static final String ENVIRONMENT_PROPERTY = "grails.env";
    private static final String ANNOTATION_NAME = "grails.compiler.beans.ConditionalOnGrailsEnv";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(ANNOTATION_NAME);
        if (attributes == null) {
            return false;
        }
        Object value = attributes.get("value");
        if (!(value instanceof String[])) {
            return false;
        }
        String current = currentEnvironmentName(context);
        if (current == null) {
            return false;
        }
        for (String candidate : (String[]) value) {
            if (current.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String currentEnvironmentName(ConditionContext context) {
        String fromGrails = currentEnvironmentNameFromGrails(context.getClassLoader());
        if (fromGrails != null) {
            return fromGrails;
        }
        return context.getEnvironment().getProperty(ENVIRONMENT_PROPERTY);
    }

    private String currentEnvironmentNameFromGrails(ClassLoader classLoader) {
        try {
            // ConditionContext.getClassLoader() is allowed to be null early in bootstrapping.
            // Spring's own class-presence checks fall back to getDefaultClassLoader(), which tries
            // the thread context loader first - the one that can see Grails when this class and the
            // application are loaded by different loaders, as under Boot's LaunchedClassLoader.
            ClassLoader loader = classLoader != null ? classLoader : ClassUtils.getDefaultClassLoader();
            Class<?> environmentClass = Class.forName(ENVIRONMENT_CLASS, true, loader);
            Object current = environmentClass.getMethod("getCurrent").invoke(null);
            if (current == null) {
                return null;
            }
            Method getName = environmentClass.getMethod("getName");
            Object name = getName.invoke(current);
            return name == null ? null : name.toString().toLowerCase(Locale.ENGLISH);
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Not a Grails application, or an Environment that cannot answer - fall back to the
            // property rather than fail a condition the rest of the context depends on.
            //
            // LinkageError is not paranoia: ClassNotFoundException only covers the class being
            // absent from the loader that was asked. A class present but unlinkable - a transitive
            // dependency missing, a version mismatch - raises NoClassDefFoundError, and initializing
            // Environment (Class.forName with initialize = true, which is what reading getCurrent
            // needs) can raise ExceptionInInitializerError. Both are LinkageError, neither is a
            // ReflectiveOperationException, and either would otherwise be thrown out of matches()
            // and fail the whole configuration this condition was meant to skip quietly.
            // VirtualMachineError is deliberately still not caught.
            return null;
        }
    }

}
