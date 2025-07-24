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
package org.grails.datastore.mapping.config.groovy

import groovy.transform.CompileStatic

import org.springframework.beans.MutablePropertyValues
import org.springframework.validation.DataBinder

import org.grails.datastore.mapping.config.Entity
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.reflect.NameUtils

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class DefaultMappingConfigurationBuilder implements MappingConfigurationBuilder {

    public static final String VERSION_KEY = 'VERSION_KEY'

    Entity target
    Map<String, Property> properties = [:]
    Class propertyClass

    DefaultMappingConfigurationBuilder(Entity target, Class propertyClass) {
        this.target = target
        this.propertyClass = propertyClass
        propertyClass.metaClass.propertyMissing = { String name, val -> }
    }

    Map<String, Property> getProperties() {
        if(!target.propertyConfigs.isEmpty()) {
            properties.putAll( target.propertyConfigs )
        }
        return properties
    }

    def invokeMethod(String name, args) {
        if (args.size() == 0) {
            return
        }

        if ('version'.equals(name) && args.length == 1 && args[0] instanceof Boolean) {
            properties[VERSION_KEY] = args[0]
            target.setVersion(args[0])
            return
        }

        def setterName = NameUtils.getSetterName(name)
        if (target.respondsTo(setterName)) {
            target[name] = args.size() == 1 ? args[0] : args
        }
        else {
            if(target.respondsTo(name)) {
                target."$name"(*args)
            }
            else if (args.size() == 1 && args[0] instanceof Map) {

                def instance
                if (properties['*']) {
                    instance = properties['*'].clone()
                }
                else {
                    instance = properties[name] ?: propertyClass.newInstance()
                }

                def binder = new DataBinder(instance)
                binder.bind(new MutablePropertyValues(args[0]))
                properties[name] = instance
            }
        }
    }

    @CompileStatic
    Entity evaluate(Closure callable, Object context = null) {
        if (!callable) {
            return
        }

        def originalDelegate = callable.delegate

        try {
            callable.delegate = this
            callable.resolveStrategy = Closure.DELEGATE_ONLY
            callable.call(context)
        } finally {
            callable.delegate = originalDelegate
        }
        return target
    }
}
