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
package org.grails.orm.hibernate.proxy;

import java.io.Serializable;

import groovy.lang.GroovyObject;
import groovy.lang.MetaClass;
import org.codehaus.groovy.runtime.HandleMetaClass;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.grails.datastore.gorm.proxy.ProxyInstanceMetaClass;

/**
 * Pure logic for Groovy proxy interception and handling, decoupled from Hibernate.
 *
 * @author Graeme Rocher
 * @since 7.0
 */
public class GroovyProxyInterceptorLogic {

    public static final Object INVOKE_IMPLEMENTATION = new Object();

    public record InterceptorState(
            String entityName,
            Class<?> persistentClass,
            Object identifier
    ) {}

    public static Object handleUninitialized(InterceptorState state, String methodName, Object[] args) {
        if ((methodName.equals("getMetaClass") || methodName.endsWith("getStaticMetaClass")) && (args == null || args.length == 0)) {
            return InvokerHelper.getMetaClass(state.persistentClass());
        }
        if (methodName.equals("getProperty") && args.length == 1 && args[0].equals("id")) {
            return state.identifier();
        }
        if (methodName.equals("ident") && (args == null || args.length == 0)) {
            return state.identifier();
        }
        if ((methodName.equals("isDirty") || methodName.equals("hasChanged")) && (args == null || args.length == 0)) {
            return false;
        }
        if (methodName.equals("toString") && (args == null || args.length == 0)) {
            return state.entityName() + ":" + state.identifier();
        }
        return INVOKE_IMPLEMENTATION;
    }

    public static boolean isGroovyMethod(String methodName) {
        return methodName.equals("getMetaClass") || methodName.equals("setMetaClass") ||
               methodName.equals("getProperty") || methodName.equals("setProperty") ||
               methodName.equals("invokeMethod");
    }

    public static ProxyInstanceMetaClass getProxyInstanceMetaClass(Object o) {
        if (o instanceof GroovyObject go) {
            MetaClass mc = go.getMetaClass();
            if (mc instanceof HandleMetaClass hmc) {
                mc = hmc.getAdaptee();
            }
            if (mc instanceof ProxyInstanceMetaClass pmc) {
                return pmc;
            }
        }
        return null;
    }

    public static Object unwrap(Object object) {
        ProxyInstanceMetaClass proxyMc = getProxyInstanceMetaClass(object);
        if (proxyMc != null) {
            return proxyMc.getProxyTarget();
        }
        return null;
    }

    public static Serializable getIdentifier(Object o) {
        ProxyInstanceMetaClass proxyMc = getProxyInstanceMetaClass(o);
        if (proxyMc != null) {
            return proxyMc.getKey();
        }
        return null;
    }
}
