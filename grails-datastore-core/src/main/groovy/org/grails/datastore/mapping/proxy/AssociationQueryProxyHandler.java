/*
 * Copyright 2015-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.proxy;

import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable;
import org.grails.datastore.mapping.engine.AssociationQueryExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.util.ReflectionUtils;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * A proxy handler that uses a {@link org.grails.datastore.mapping.engine.AssociationQueryExecutor} to retrieve the association
 *
 * @author Graeme Rocher
 * @since 5.0
 */
public class AssociationQueryProxyHandler  extends EntityProxyMethodHandler {

    protected final Session session;
    protected final AssociationQueryExecutor executor;
    protected final Serializable associationKey;
    protected Object target;

    public AssociationQueryProxyHandler(Session session, AssociationQueryExecutor executor, Serializable associationKey) {
        super(executor.getIndexedEntity().getJavaClass());
        this.session = session;
        this.executor = executor;
        this.associationKey = associationKey;
    }

    @Override
    protected Object isProxyInitiated(Object self) {
        return target != null;
    }

    @Override
    protected Object getProxyKey(Object self) {
        return associationKey;
    }


    @Override
    protected Object resolveDelegate(Object self) {
        if (target == null) {
            final List results = executor.query(associationKey);
            if(executor.doesReturnKeys()) {
                if(!results.isEmpty()) {
                    target = session.retrieve(executor.getIndexedEntity().getJavaClass(), (Serializable) results.get(0));
                }
            }
            else {
                if(!results.isEmpty()) {
                    target = results.get(0);
                }
            }

            // This tends to happen during unit testing if the proxy class is not properly mocked
            // and therefore can't be found in the session.
            if( target == null ) {
                throw new DataIntegrityViolationException("Proxy for ["+ proxyClass.getName()+"] for association ["+executor.getIndexedEntity().getName()+"] could not be initialized");
            }
            if (target instanceof DirtyCheckable) {
                ((DirtyCheckable) target).syncChangedProperties(self);
            }
        }
        return target;
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
