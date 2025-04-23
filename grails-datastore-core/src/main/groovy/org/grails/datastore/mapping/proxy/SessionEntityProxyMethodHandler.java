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

import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.util.ReflectionUtils;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
* An {@link EntityProxyMethodHandler} that uses the session to initialize a target for the given id
*
* @author Lari Hotari
* @author Graeme Rocher
*/
public class SessionEntityProxyMethodHandler extends EntityProxyMethodHandler {
    private static final Logger LOG = LoggerFactory.getLogger(SessionEntityProxyMethodHandler.class);
    private final Session session;
    private final Class cls;
    private final Serializable id;
    protected Object target;


    public SessionEntityProxyMethodHandler(Class proxyClass, Session session, Class cls, Serializable id) {
        super(proxyClass);
        this.session = session;
        this.cls = cls;
        this.id = id;
    }

    @Override
    protected Object resolveDelegate(Object self) {
        if (target == null) {
            initializeTarget(self);

            // This tends to happen during unit testing if the proxy class is not properly mocked
            // and therefore can't be found in the session.
            if( target == null ) {
                throw new DataIntegrityViolationException("Proxy for ["+ cls.getName()+":"+ id +"] could not be initialized");
            }
        }
        return target;
    }

    protected void initializeTarget() {
        if(LOG.isDebugEnabled()) {
            LOG.debug("Lazy loading proxy for class {} with id {}", cls.getName(), id);
        }
        target = session.retrieve(cls, id);
    }

    protected void initializeTarget(Object self) {
        initializeTarget();
        if (target instanceof DirtyCheckable) {
            ((DirtyCheckable) target).syncChangedProperties(self);
        }
    }

    @Override
    protected Object isProxyInitiated(Object self) {
        return target != null;
    }

    @Override
    protected Object getProxyKey(Object self) {
        return id;
    }

    protected Object handleInvocationFallback(Object self, Method thisMethod, Object[] args) {
        Object actualTarget = getProxyTarget(self);
        if(!thisMethod.getDeclaringClass().isInstance(actualTarget)) {
            if(Modifier.isPublic(thisMethod.getModifiers())) {
                final Method method = ReflectionUtils.findMethod(actualTarget.getClass(), thisMethod.getName(), thisMethod.getParameterTypes());
                if(method != null) {
                    ReflectionUtils.makeAccessible(method);
                    thisMethod = method;
                }
            } else {
                final Method method = ReflectionUtils.findMethod(actualTarget.getClass(), thisMethod.getName(), thisMethod.getParameterTypes());
                if(method != null) {
                    thisMethod = method;
                }
            }
        }
        return ReflectionUtils.invokeMethod(thisMethod, actualTarget, args);
    }
}
