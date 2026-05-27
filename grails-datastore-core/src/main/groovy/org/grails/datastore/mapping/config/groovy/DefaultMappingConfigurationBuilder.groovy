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
import groovy.util.logging.Slf4j

import org.springframework.beans.MutablePropertyValues
import org.springframework.validation.DataBinder

import org.grails.datastore.mapping.config.Entity
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.reflect.NameUtils

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Slf4j
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

    /**
     * Merges the builder's own property map (populated by {@link #invokeMethod}
     * during evaluation of the mapping/constraints closures) with
     * {@code target.propertyConfigs} (populated by alternate paths such as
     * {@code Entity.property(name, Map)} or constraint evaluators that write
     * directly to the entity's property configs).
     *
     * <p>When the same key is present in both maps, the builder's instance
     * wins. This protects mapping-configured settings such as
     * {@code index: true} and {@code indexAttributes} from being silently
     * overwritten by a less-configured instance in {@code propertyConfigs}.
     *
     * <p>The dual-store design (this builder's {@code properties} field vs.
     * the entity's {@code propertyConfigs}) is a structural legacy; collapsing
     * the two into a single canonical store would let this method go away.
     * See <a href="https://github.com/apache/grails-core/issues/15680">#15680</a>.
     */
    Map<String, Property> getProperties() {
        if (!target.propertyConfigs.isEmpty()) {
            for (Map.Entry entry : target.propertyConfigs.entrySet()) {
                if (properties.containsKey(entry.key)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Property '{}' is configured in both the mapping closure and via Entity.propertyConfigs " +
                                "(typically a constraint evaluator or direct Entity.property() call). The mapping-side " +
                                "configuration takes precedence; the propertyConfigs entry is being ignored to avoid " +
                                "overwriting mapping-set fields such as index/indexAttributes. See grails-core#15680.",
                                entry.key)
                    }
                } else {
                    properties.put(entry.key, entry.value)
                }
            }
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
            if (target.respondsTo(name)) {
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
