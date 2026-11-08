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
package org.grails.plugins.web.controllers.api;

import java.util.Map;

import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;

import grails.core.GrailsApplication;
import grails.util.Environment;
import grails.util.Holders;
import grails.web.databinding.DataBindingUtils;
import org.grails.core.exceptions.GrailsConfigurationException;
import org.grails.datastore.mapping.model.PersistentEntity;

/**
 * Enhancements made to domain classes for data binding.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@SuppressWarnings("rawtypes")
public class ControllersDomainBindingApi {

    public static final String AUTOWIRE_DOMAIN_METHOD = "autowireDomain";

    /**
     * Autowires the instance
     *
     * @param instance The target instance
     */
    public static void initialize(Object instance) {
        GrailsApplication application = findApplication();
        autowire(instance, getDomainClass(instance, application));
    }

    /**
     * A map based constructor that binds the named arguments to the target instance
     *
     * @param instance The target instance
     * @param namedArgs The named arguments
     */
    public static void initialize(Object instance, Map namedArgs) {
        GrailsApplication application = findApplication();
        PersistentEntity dc = getDomainClass(instance, application);
        if (dc == null) {
            DataBindingUtils.bindObjectToInstance(instance, namedArgs);
        }
        else {
            DataBindingUtils.bindObjectToDomainInstance(dc, instance, namedArgs);
            DataBindingUtils.assignBidirectionalAssociations(instance, namedArgs, dc);
        }
        autowire(instance, dc);
    }

    /**
     * @return The current application, or null while the environment is still initializing or before an
     * application has been bound
     */
    private static GrailsApplication findApplication() {
        return Environment.isInitializing() ? null : Holders.findApplication();
    }

    private static PersistentEntity getDomainClass(Object instance, GrailsApplication application) {
        if (application == null) {
            return null;
        }
        try {
            return application.getMappingContext().getPersistentEntity(instance.getClass().getName());
        } catch (GrailsConfigurationException e) {
            // ignore, Mapping Context not initialized yet
            return null;
        }
    }

    private static void autowire(Object instance, PersistentEntity domainClass) {
        if (domainClass == null) {
            return;
        }
        try {
            if (domainClass.getMapping().getMappedForm().isAutowire()) {
                final ApplicationContext applicationContext = Holders.findApplicationContext();
                if (applicationContext != null) {
                    applicationContext
                            .getAutowireCapableBeanFactory()
                            .autowireBeanProperties(instance, AutowireCapableBeanFactory.AUTOWIRE_BY_NAME, false);
                }
            }
        } catch (GrailsConfigurationException e) {
            // ignore, Mapping Context not initialized yet
        }
    }
}
