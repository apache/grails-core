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

package org.grails.datastore.gorm.events

import groovy.transform.CompileStatic

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext

/**
 * Bridge to Spring ApplicationContext event publishing
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class ConfigurableApplicationContextEventPublisher implements ConfigurableApplicationEventPublisher, ApplicationContextAware {

    /**
     * Volatile because it is no longer final. A publisher built without a context is completed by a
     * container callback, on whichever thread the container refreshes on, and read later on
     * whichever thread publishes an event -- so what one wrote has to be what the other sees.
     */
    volatile ConfigurableApplicationContext applicationContext

    /**
     * Takes the context from the container. A bean definition built this way holds no
     * already-constructed object, which is what allows it to be processed ahead of time.
     */
    ConfigurableApplicationContextEventPublisher() {
    }

    ConfigurableApplicationContextEventPublisher(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext
    }

    /**
     * Narrowed rather than cast. This is a container callback, so a context that is not
     * configurable would throw a {@link ClassCastException} out of the container's own setup rather
     * than out of anything this was asked to do. Left unset instead, which {@link #context()}
     * reports if it is ever used.
     */
    @Override
    void setApplicationContext(ApplicationContext applicationContext) {
        if (applicationContext instanceof ConfigurableApplicationContext) {
            this.applicationContext = applicationContext
        }
    }

    @Override
    void addApplicationListener(ApplicationListener<? extends ApplicationEvent> listener) {
        context().addApplicationListener(listener)
    }

    @Override
    void publishEvent(ApplicationEvent event) {
        context().publishEvent(event)
    }

    @Override
    void publishEvent(Object event) {
        context().publishEvent(event)
    }

    /**
     * The context, or what is wrong if there is not one.
     *
     * <p>A publisher built without a context is completed by {@code ApplicationContextAware}, and
     * that callback is applied by the context -- so it never happens where the bean was registered
     * in a plain bean factory. Without this, the first event published is a
     * {@link NullPointerException} from inside GORM, which says nothing about the bean factory the
     * publisher was registered in.</p>
     */
    private ConfigurableApplicationContext context() {
        ConfigurableApplicationContext current = this.applicationContext
        if (current == null) {
            throw new IllegalStateException('This event publisher has no application context. It is ' +
                    'given one by the container, which only happens where it was registered in a ' +
                    'ConfigurableApplicationContext -- registered in a plain bean factory, nothing ' +
                    'applies ApplicationContextAware to it. Register it in a context, or construct ' +
                    'it with the context to publish through.')
        }
        current
    }
}
