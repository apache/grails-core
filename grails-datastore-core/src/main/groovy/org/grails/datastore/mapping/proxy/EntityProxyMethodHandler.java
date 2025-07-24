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
package org.grails.datastore.mapping.proxy;

import java.lang.reflect.Method;

import org.grails.datastore.mapping.model.config.GormProperties;

/**
 * Base class for entity proxy handlers that are aware of Groovy
 *
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 *
 */
public abstract class EntityProxyMethodHandler extends GroovyObjectMethodHandler {

    public static final String PROXY_PROPERTY = "proxy";
    public static final String PROXY_KEY_PROPERTY = "proxyKey";
    public static final String INITIALIZED_PROPERTY = "initialized";
    public static final String TARGET_PROPERTY = "target";
    public static final String IS_PROXY_METHOD = "isProxy";
    public static final String GET_PROXY_KEY_METHOD = "getProxyKey";
    public static final String GET_ID_METHOD = "getId";
    public static final String IS_INITIALIZED_METHOD = "isInitialized";
    public static final String INITIALIZE_METHOD = "initialize";
    public static final String GET_TARGET_METHOD = "getTarget";

    public EntityProxyMethodHandler(Class<?> proxyClass) {
        super(proxyClass);
    }

    @Override
    protected Object getPropertyBeforeResolving(Object self, String property) {
        if (property.equals(PROXY_PROPERTY)) {
            return true;
        } else if (property.equals(PROXY_KEY_PROPERTY) || property.equals(GormProperties.IDENTITY)) {
            return getProxyKey(self);
        } else if (property.equals(INITIALIZED_PROPERTY)) {
            return isProxyInitiated(self);
        } else if (property.equals(TARGET_PROPERTY)) {
            return getProxyTarget(self);
        } else {
            return super.getPropertyBeforeResolving(self, property);
        }
    }

    @Override
    public Object invokeMethodBeforeResolving(Object self, String methodName, Object[] args) {
        Object result = invokeEntityProxyMethods(self, methodName, args);
        if(!wasHandled(result)) {
            return super.invokeMethodBeforeResolving(self, methodName, args);
        } else {
            return result;
        }
    }

    @Override
    public Object handleInvocation(Object self, Method thisMethod, Object[] args) {
        Object result = invokeEntityProxyMethods(self, thisMethod.getName(), args);
        if(!wasHandled(result)) {
            result = super.handleInvocation(self, thisMethod, args);
            if(!wasHandled(result)) {
                return handleInvocationFallback(self, thisMethod, args);
            }
        }
        return result;
    }

    protected Object handleInvocationFallback(Object self, Method thisMethod, Object[] args) {
       return INVOKE_IMPLEMENTATION;
    }

    protected Object invokeEntityProxyMethods(Object self, String methodName, Object[] args) {
        if (methodName.equals(IS_PROXY_METHOD)) {
            return true;
        } else if (methodName.equals(GET_PROXY_KEY_METHOD) || methodName.equals(GET_ID_METHOD)) {
            return getProxyKey(self);
        } else if (methodName.equals(IS_INITIALIZED_METHOD)) {
            return isProxyInitiated(self);
        } else if (methodName.equals(GET_TARGET_METHOD)) {
            return getProxyTarget(self);
        } else if (methodName.equals(INITIALIZE_METHOD)) {
            initializeProxyTarget(self);
            return Void.class;
        } else {
            return INVOKE_IMPLEMENTATION;
        }
    }


    protected void initializeProxyTarget(Object self) {
        getProxyTarget(self);
    }

    protected Object getProxyTarget(Object self) {
        return resolveDelegate(self);
    }

    protected abstract Object isProxyInitiated(Object self);

    protected abstract Object getProxyKey(Object self);
}
