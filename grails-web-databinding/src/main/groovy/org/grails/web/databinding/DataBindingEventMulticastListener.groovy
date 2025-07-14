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
package org.grails.web.databinding

import grails.databinding.errors.BindingError
import grails.databinding.events.DataBindingListener
import groovy.transform.CompileStatic
import groovy.util.logging.Commons

/**
 * @author Jeff Brown
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
@Commons
class DataBindingEventMulticastListener implements DataBindingListener {

    protected final List<DataBindingListener> listeners

    DataBindingEventMulticastListener(List<DataBindingListener> listeners) {
        this.listeners = listeners
    }

    boolean supports(Class<?> clazz) {
        true
    }

    Boolean beforeBinding(target, errors) {
        boolean bind = true
        for (DataBindingListener listener in listeners) {
            try {
                if (!listener.beforeBinding(target, errors)) {
                    bind = false
                }
            } catch (Exception e) {
                log.error "An error occurred invoking beforeBinding on the ${listener.getClass().getName()} listener.", e
            }
        }
        bind
    }

    @Override
    Boolean beforeBinding(obj, String propertyName, value, errors) {
        boolean bind = true
        for (DataBindingListener listener in listeners) {
            try {
                if (!listener.beforeBinding(obj, propertyName, value, errors)) {
                    bind = false
                }
            } catch (Exception e) {
                log.error "An error occurred invoking beforeBinding on the ${listener.getClass().getName()} listener.", e
            }
        }
        bind
    }

    @Override
    void afterBinding(obj, String propertyName, errors) {
        if (listeners) {
            for (DataBindingListener listener : listeners) {
                try {
                    listener.afterBinding obj, propertyName, errors
                } catch (Exception e) {
                    log.error "An error occurred invoking afterBinding on the ${listener.getClass().getName()} listener.", e
                }
            }
        }
    }

    @Override
    void afterBinding(target, errors) {
        if (listeners) {
            for (DataBindingListener listener : listeners) {
                try {
                    listener.afterBinding target, errors
                } catch (Exception e) {
                    log.error "An error occurred invoking afterBinding on the ${listener.getClass().getName()} listener.", e
                }
            }
        }
    }

    @Override
    void bindingError(BindingError error, errors) {
        if (listeners) {
            for (DataBindingListener listener : listeners) {
                try {
                    listener.bindingError error, errors
                } catch (Exception e) {
                    log.error "An error occurred invoking bindingError on the ${listener.getClass().getName()} listener.", e
                }
            }
        }
    }
}