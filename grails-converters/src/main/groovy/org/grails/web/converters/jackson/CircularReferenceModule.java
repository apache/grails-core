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
package org.grails.web.converters.jackson;

import java.util.function.Supplier;

import tools.jackson.core.Version;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.ValueSerializerModifier;

import org.grails.web.converters.Converter.CircularReferenceBehaviour;

/**
 * Applies the JSON converter's circular reference behaviour to the beans a mapper writes property by property, as
 * {@link DomainClassJacksonModule} applies it to domain class instances. A bean that is reached again while it is being
 * written would otherwise be written until Jackson's nesting limit fails the response.
 *
 * <p>Both share the instances being written, so a cycle that passes through domain class instances and plain beans is
 * found at whichever it first repeats.</p>
 *
 * @since 9.0
 */
final class CircularReferenceModule extends JacksonModule {

    private final Supplier<CircularReferenceBehaviour> behaviour;

    CircularReferenceModule(Supplier<CircularReferenceBehaviour> behaviour) {
        this.behaviour = behaviour;
    }

    @Override
    public String getModuleName() {
        return "grails-circular-references";
    }

    @Override
    public Version version() {
        return Version.unknownVersion();
    }

    @Override
    public void setupModule(SetupContext context) {
        context.addSerializerModifier(new ValueSerializerModifier() {
            @Override
            public ValueSerializer<?> modifySerializer(SerializationConfig config, BeanDescription.Supplier beanDescription,
                    ValueSerializer<?> serializer) {
                ValueSerializer<?> tracked = CycleTracking.around(serializer, (value, enclosing, generator, context) ->
                        Cycles.write(value, enclosing, generator, behaviour.get()));
                return tracked != null ? tracked : serializer;
            }
        });
    }
}
