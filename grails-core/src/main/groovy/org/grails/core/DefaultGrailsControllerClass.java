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
package org.grails.core;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import groovy.lang.GroovyObject;

import org.springframework.util.ReflectionUtils;

import grails.config.Settings;
import grails.core.GrailsApplication;
import grails.core.GrailsControllerClass;
import grails.web.Action;
import grails.web.UrlConverter;
import org.grails.core.exceptions.GrailsConfigurationException;

/**
 * Evaluates the conventions contained within controllers to perform auto-configuration.
 *
 * @author Graeme Rocher
 * @author Steven Devijver
 * @since 0.1
 */
@SuppressWarnings("rawtypes")
public class DefaultGrailsControllerClass extends AbstractInjectableGrailsClass implements GrailsControllerClass {

    public static final String CONTROLLER = "Controller";

    private static final String DEFAULT_CLOSURE_PROPERTY = "defaultAction";
    public static final String ALLOWED_HTTP_METHODS_PROPERTY = "allowedMethods";
    public static final Object[] EMPTY_ARGS = new Object[0];
    public static final String SCOPE = "scope";
    public static final String SCOPE_SINGLETON = "singleton";
    private String scope;
    private Map<String, MethodHandle> actions = new HashMap<>();
    private String defaultActionName;
    private String namespace;
    protected Map<String, String> actionUriToViewName = new HashMap<>();

    public DefaultGrailsControllerClass(Class<?> clazz) {
        super(clazz, CONTROLLER);
        namespace = getStaticPropertyValue(NAMESPACE_PROPERTY, String.class);
        defaultActionName = getStaticPropertyValue(DEFAULT_CLOSURE_PROPERTY, String.class);
        if (defaultActionName == null) {
            defaultActionName = INDEX_ACTION;
        }
        methodStrategy(actions);
        this.scope = getStaticPropertyValue(SCOPE, String.class);
    }

    public void initialize() {
        // no-op
    }

    @Override
    public void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication;
        if (this.scope == null) {
            this.scope = grailsApplication.getConfig().getProperty(Settings.CONTROLLERS_DEFAULT_SCOPE, SCOPE_SINGLETON);
        }
    }

    @Override
    public Set<String> getActions() {
        return actions.keySet();
    }

    public String getNamespace() {
        return namespace;
    }

    @Override
    public String getScope() {
        return scope;
    }

    @Override
    public boolean isSingleton() {
        return SCOPE_SINGLETON.equalsIgnoreCase(getScope());
    }

    @Override
    public String getDefaultAction() {
        return this.defaultActionName;
    }

    private void methodStrategy(Map<String, MethodHandle> methodNames) {

        Class superClass = getClazz();
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        while (superClass != Object.class && superClass != GroovyObject.class) {
            for (Method method : superClass.getMethods()) {
                if (Modifier.isPublic(method.getModifiers()) && method.getAnnotation(Action.class) != null) {
                    methodNames.put(method.getName(), actionHandle(lookup, method));
                }
            }
            superClass = superClass.getSuperclass();
        }

        if (!isActionMethod(defaultActionName) && methodNames.size() == 1 && !isReadableProperty("scaffold")) {
            defaultActionName = methodNames.keySet().iterator().next();
        }
    }

    /**
     * A handle to the action, made from the method that reflection reports, so that it also reaches an action a class
     * reloading agent has added to a class that is already loaded, and an action of a class that is not public.
     */
    private static MethodHandle actionHandle(MethodHandles.Lookup lookup, Method method) {
        ReflectionUtils.makeAccessible(method);
        MethodHandle handle;
        try {
            handle = lookup.unreflect(method);
        }
        catch (IllegalAccessException e) {
            throw new GrailsConfigurationException("Cannot invoke controller action [" + method + "]", e);
        }
        if (Modifier.isStatic(method.getModifiers())) {
            // invoked with the controller, which a static action ignores, as Method.invoke does
            handle = MethodHandles.dropArguments(handle, 0, Object.class);
        }
        return handle;
    }

    @Override
    public boolean mapsToURI(String uri) {
        if (uri.startsWith("/")) {
            String[] tokens = uri.substring(1).split("\\/");
            if (tokens.length > 0) {
                String controllerName = tokens[0];
                if (getLogicalPropertyName().equals(controllerName)) {
                    if (tokens.length > 1) {
                        String actionName = tokens[1];
                        if (actions.containsKey(actionName) || defaultActionName.equals(actionName)) {
                            return true;
                        }
                    }
                    else {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Register a new {@link grails.web.UrlConverter} with the controller
     *
     * @param urlConverter The {@link grails.web.UrlConverter} to register
     */
    @Override
    public void registerUrlConverter(UrlConverter urlConverter) {
        for (String actionName : new ArrayList<>(actions.keySet())) {
            actionUriToViewName.put(urlConverter.toUrlElement(actionName), actionName);
            actions.put(urlConverter.toUrlElement(actionName), actions.remove(actionName));
        }
        defaultActionName = urlConverter.toUrlElement(defaultActionName);
    }

    /**
     * Invokes the controller action for the given name on the given controller instance
     *
     * @param controller The controller instance
     * @param action The action name
     * @return The result of the action
     * @throws Throwable
     */
    @Override
    public Object invoke(Object controller, String action) throws Throwable {
        if (action == null) action = this.defaultActionName;
        MethodHandle handle = actions.get(action);
        if (handle == null) throw new IllegalArgumentException("Invalid action name: " + action);
        return handle.invoke(controller);
    }

    public String actionUriToViewName(String actionUri) {
        String actionName = actionUriToViewName.get(actionUri);

        return actionName != null ? actionName : actionUri;
    }
}
