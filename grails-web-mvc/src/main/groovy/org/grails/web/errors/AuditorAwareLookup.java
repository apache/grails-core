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
package org.grails.web.errors;

import java.lang.reflect.Method;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

/**
 * Optional integration with the GORM {@code AuditorAware} bean. When the audit API
 * class is on the classpath and a bean is registered, {@link #getCurrentAuditor()}
 * returns its result so the exception resolver can reuse the same "current user"
 * resolution that {@code @CreatedBy} uses. The lookup is reflective so that this
 * module does not compile-time depend on {@code grails-datamapping-core}.
 *
 * <p>Any failure — missing class, missing bean, invocation error — resolves to an
 * empty {@code Optional}. Exception logging must never be blocked by a broken
 * auditor lookup.</p>
 */
class AuditorAwareLookup {

    private static final Logger LOG = LoggerFactory.getLogger(AuditorAwareLookup.class);
    private static final String AUDITOR_AWARE_CLASS = "org.grails.datastore.gorm.timestamp.AuditorAware";

    private final ApplicationContext applicationContext;

    private volatile boolean resolved;
    private Object bean;
    private Method getCurrentAuditor;

    AuditorAwareLookup(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    Optional<?> getCurrentAuditor() {
        if (!resolve() || bean == null) {
            return Optional.empty();
        }
        try {
            Object result = getCurrentAuditor.invoke(bean);
            return result instanceof Optional<?> ? (Optional<?>) result : Optional.empty();
        }
        catch (ReflectiveOperationException e) {
            LOG.debug("AuditorAware#getCurrentAuditor invocation failed", e);
            return Optional.empty();
        }
    }

    private boolean resolve() {
        if (resolved) {
            return true;
        }
        synchronized (this) {
            if (resolved) {
                return true;
            }
            try {
                if (applicationContext != null &&
                        ClassUtils.isPresent(AUDITOR_AWARE_CLASS, applicationContext.getClassLoader())) {
                    Class<?> type = ClassUtils.forName(AUDITOR_AWARE_CLASS, applicationContext.getClassLoader());
                    try {
                        bean = applicationContext.getBean(type);
                        getCurrentAuditor = type.getMethod("getCurrentAuditor");
                    }
                    catch (BeansException noBean) {
                        // optional — no bean registered
                    }
                }
            }
            catch (Throwable t) {
                LOG.debug("AuditorAware integration unavailable", t);
            }
            finally {
                resolved = true;
            }
        }
        return true;
    }
}
