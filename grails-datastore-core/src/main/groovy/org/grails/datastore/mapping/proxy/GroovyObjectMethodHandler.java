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

import groovy.lang.MetaClass;
import org.codehaus.groovy.runtime.InvokerHelper;

import javassist.util.proxy.MethodHandler;

/**
 * Javassist MethodHandler for handling GroovyObject methods
 *
 * delegates getProperty, setProperty &amp; invokeMethod to super class's MetaClass
 *
 * @author Lari Hotari
 * @author Graeme Rocher
 */
public class GroovyObjectMethodHandler implements MethodHandler {
    public static final Object INVOKE_IMPLEMENTATION = new Object();
    public static final String META_CLASS_PROPERTY = "metaClass";
    public static final String GET_META_CLASS = "getMetaClass";
    public static final String GET_PROPERTY = "getProperty";
    public static final String SET_META_CLASS = "setMetaClass";
    public static final String SET_PROPERTY = "setProperty";
    public static final String INVOKE_METHOD = "invokeMethod";
    protected final Class<?> proxyClass;
    protected transient MetaClass metaClass;

    public GroovyObjectMethodHandler(Class<?> proxyClass) {
        this.proxyClass = proxyClass;
    }

    public Object getProperty(Object self, String property) {
        Object result = getPropertyBeforeResolving(self, property);
        if(!wasHandled(result)) {
            return resolveDelegateAndGetProperty(self, property);
        } else {
            return result;
        }
    }

    protected Object resolveDelegateAndGetProperty(Object self, String property) {
        return getPropertyAfterResolving(resolveDelegate(self), property);
    }

    protected Object getPropertyAfterResolving(Object delegate, String property) {
        return InvokerHelper.getMetaClass(delegate).getProperty(delegate, property);
    }

    protected Object getPropertyBeforeResolving(Object self, String property) {
        if(META_CLASS_PROPERTY.equals(property)) {
            return getThisMetaClass();
        }
        return INVOKE_IMPLEMENTATION;
    }

    protected Object resolveDelegate(Object self) {
        return self;
    }

    public void setProperty(Object self, String property, Object newValue) {
        if(setPropertyBeforeResolving(self, property, newValue)) {
            return;
        }
        resolveDelegateAndSetProperty(self, property, newValue);
    }

    protected void resolveDelegateAndSetProperty(Object self, String property, Object newValue) {
        setPropertyAfterResolving(resolveDelegate(self), property, newValue);
    }

    protected boolean setPropertyBeforeResolving(Object self, String property, Object newValue) {
        if(META_CLASS_PROPERTY.equals(property)) {
            setThisMetaClass((MetaClass)newValue);
            return true;
        }
        return false;
    }
    
    protected void setPropertyAfterResolving(Object delegate, String property, Object newValue) {
        InvokerHelper.getMetaClass(delegate).setProperty(delegate, property, newValue);
    }

    public Object invokeThisMethod(Object self, String name, Object[] args) {
        Object result = invokeMethodBeforeResolving(self, name, args);
        if(!wasHandled(result)) {
            return resolveDelegateAndInvokeThisMethod(self, name, args);
        } else {
            return result;
        }
    }

    protected Object resolveDelegateAndInvokeThisMethod(Object self, String name, Object[] args) {
        return invokeMethodAfterResolving(resolveDelegate(self), name, args);
    }

    protected Object invokeMethodAfterResolving(Object delegate, String name, Object[] args) {
        return InvokerHelper.getMetaClass(delegate).invokeMethod(delegate, name, args);
    }

    public Object invokeMethodBeforeResolving(Object self, String name, Object[] args) {
        if(GET_META_CLASS.equals(name) && args.length==0) {
            return getThisMetaClass();
        }
        if(SET_META_CLASS.equals(name) && args.length==1) {
            setThisMetaClass((MetaClass)args[0]);
            return Void.class;
        }
        return INVOKE_IMPLEMENTATION;
    }

    public MetaClass getThisMetaClass() {
        if (metaClass == null) {
            metaClass = InvokerHelper.getMetaClass(proxyClass);
        }
        return metaClass;
    }

    public void setThisMetaClass(MetaClass metaClass) {
        this.metaClass = metaClass;
    }

    @Override
    public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
        Object result = handleInvocation(self, thisMethod, args);
        if(!wasHandled(result)) {

            return proceed.invoke(self, args);
        } else {
            return result;
        }
    }

    public boolean wasHandled(Object result) {
        return result != INVOKE_IMPLEMENTATION;
    }

    public Object handleInvocation(Object self, Method thisMethod, Object[] args) {
        String methodName = thisMethod.getName();
        if (args.length == 0) {
            if (GET_META_CLASS.equals(methodName)) {
                return getThisMetaClass();
            }
        }
        else if (args.length == 1) {
            if (GET_PROPERTY.equals(methodName)) {
                String name = args[0].toString();
                if(META_CLASS_PROPERTY.equals(name)) {
                    return getThisMetaClass();
                } else {
                    return getProperty(self, name);
                }
            } else if (SET_META_CLASS.equals(methodName)) {
                setThisMetaClass((MetaClass)args[0]);
                return Void.class;
            }
        }
        else if (args.length == 2) {
            if (SET_PROPERTY.equals(methodName)) {
                String name = args[0].toString();
                Object value = args[1];
                if(META_CLASS_PROPERTY.equals(name)) {
                    setThisMetaClass((MetaClass)value);
                } else {
                    setProperty(self, name, value);
                }
                return Void.class;
            } else if (INVOKE_METHOD.equals(methodName)) {
                return invokeThisMethod(self, args[0].toString(), (Object[])args[1]);
            }
        }
        return INVOKE_IMPLEMENTATION;
    }
}
