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
package org.grails.web.servlet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;

import jakarta.servlet.http.HttpSession;

import org.springframework.web.context.request.RequestContextHolder;

import grails.web.mvc.FlashScope;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.grails.web.util.GrailsApplicationAttributes;

/**
 * Grails implementation of Flash scope (@see grails.web.mvc.FlashScope).
 *
 * @author Graeme Rocher
 */
@SuppressWarnings({"unchecked","rawtypes"})
public class GrailsFlashScope implements FlashScope {

    private static final long serialVersionUID = 1457772347769500476L;
    private Map current = new ConcurrentHashMap();
    private Map next = new ConcurrentHashMap();
    public static final String ERRORS_PREFIX = "org.codehaus.groovy.grails.ERRORS_";
    private static final String ERRORS_PROPERTY = "errors";

    private final boolean registerWithSession;

    public GrailsFlashScope() {
        this(true);
    }

    public GrailsFlashScope(boolean registerWithSession) {
        this.registerWithSession = registerWithSession;
    }

    public void next() {
        current.clear();
        current = new ConcurrentHashMap(next);
        next.clear();
        reassociateObjectsWithErrors(current);
    }

    public Map getNow() {
        return current;
    }

    private void reassociateObjectsWithErrors(Map scope) {
        for (Object key : scope.keySet()) {
            Object value = scope.get(key);
            if (value instanceof Map) {
                reassociateObjectsWithErrors((Map) value);
            }
            reassociateObjectWithErrors(scope, value);
        }
    }

    private void reassociateObjectWithErrors(Map scope, Object value) {
        if (value instanceof Collection) {
            Collection values = (Collection)value;
            for (Object val : values) {
                reassociateObjectWithErrors(scope, val);
            }
        }
        else {
            String errorsKey = ERRORS_PREFIX + System.identityHashCode(value);
            Object errors = scope.get(errorsKey);
            if (value!=null && errors != null) {
                MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(value.getClass());
                if (mc.hasProperty(value, ERRORS_PROPERTY) != null) {
                    mc.setProperty(value, ERRORS_PROPERTY, errors);
                }
            }
        }
    }

    public int size() {
        return current.size() + next.size();
    }

    public void clear() {
        current.clear();
        next.clear();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean containsKey(Object key) {
        return (current.containsKey(key) || next.containsKey(key));
    }

    public boolean containsValue(Object value) {
        return (current.containsValue(value) || next.containsValue(value));
    }

    public Collection<Object> values() {
        Collection c = new ArrayList();
        c.addAll(current.values());
        c.addAll(next.values());
        return c;
    }

    public void putAll(Map<? extends String, ? extends Object> t) {
        for (Entry<? extends String, ? extends Object> entry : t.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    public Set<Map.Entry<String, Object>> entrySet() {
        Set entrySet = new HashSet();
        entrySet.addAll(current.entrySet());
        entrySet.addAll(next.entrySet());
        return entrySet;
    }

    public Set<String> keySet() {
        Set keySet = new HashSet();
        keySet.addAll(current.keySet());
        keySet.addAll(next.keySet());
        return keySet;
    }

    public Object get(Object key) {
        if (next.containsKey(key)) {
            return next.get(key);
        }
        if ("now".equals(key)) {
            return getNow();
        }
        return current.get(key);
    }

    public Object remove(Object key) {
        if (current.containsKey(key)) {
            return current.remove(key);
        }

        return next.remove(key);
    }

    public Object put(String key, Object value) {
        // create the session if it doesn't exist
        registerWithSessionIfNecessary();

        if (current.containsKey(key)) {
            current.remove(key);
        }
        storeErrorsIfPossible(next,value);

        if (value == null) {
            return next.remove(key);
        }

        return next.put(key,value);
    }

    private void storeErrorsIfPossible(Map scope,Object value) {
        if (value == null) {
            return;
        }

        if (value instanceof Collection) {
            Collection values = (Collection)value;
            for (Object val : values) {
                storeErrorsIfPossible(scope, val);
            }
        }
        else if (value instanceof Map) {
            Map map = (Map)value;
            Collection keys = new LinkedList(map.keySet());
            for (Object key : keys) {
                Object val = map.get(key);
                storeErrorsIfPossible(map, val);
            }
        }
        else {
            MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(value.getClass());
            if (mc.hasProperty(value, ERRORS_PROPERTY) != null) {
                Object errors = mc.getProperty(value, ERRORS_PROPERTY);
                if (errors != null) {
                    scope.put(ERRORS_PREFIX + System.identityHashCode(value), errors);
                }
            }
        }
    }

    private void registerWithSessionIfNecessary() {
        if (registerWithSession) {
            GrailsWebRequest webRequest = (GrailsWebRequest)RequestContextHolder.currentRequestAttributes();
            HttpSession session = webRequest.getCurrentRequest().getSession(true);
            if (session.getAttribute(GrailsApplicationAttributes.FLASH_SCOPE) == null) {
                session.setAttribute(GrailsApplicationAttributes.FLASH_SCOPE, this);
            }
        }
    }
}
