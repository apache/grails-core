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

import java.util.Objects;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.core.Version;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.JacksonSerializable;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.Serializers;

/**
 * A Jackson module that serializes domain class instances, and proxies of them, with a {@link DomainClassSerializer},
 * as {@code grails.converters.JSON} renders them. A Grails application registers it with the application's
 * {@code JsonMapper}, so that writing a domain class instance with the mapper, including returning one from a
 * Spring MVC controller, renders it as {@code render ... as JSON} does.
 *
 * <p>A domain class with its own {@code @JsonSerialize} serializer keeps it, and so does one with a
 * {@code @JsonValue}, or that implements {@code JacksonSerializable}, which Jackson writes as it writes any class. A
 * subclass of a domain class that is neither a domain class nor a proxy class of one is written as a Jackson bean.
 *
 * @since 9.0
 */
public class DomainClassJacksonModule extends JacksonModule {

    private final Supplier<DomainClassRendering> rendering;

    /**
     * @param rendering how to render domain class instances
     */
    public DomainClassJacksonModule(DomainClassRendering rendering) {
        this(() -> rendering);
    }

    /**
     * @param rendering supplies how to render domain class instances, when the first one is serialized
     */
    public DomainClassJacksonModule(Supplier<DomainClassRendering> rendering) {
        this.rendering = Objects.requireNonNull(rendering, "rendering cannot be null");
    }

    @Override
    public String getModuleName() {
        return "grails-domain-classes";
    }

    @Override
    public Version version() {
        return Version.unknownVersion();
    }

    @Override
    public void setupModule(SetupContext context) {
        context.addSerializers(new DomainClassSerializers(rendering));
    }

    private static final class DomainClassSerializers implements Serializers {

        private final Supplier<DomainClassRendering> rendering;

        private volatile DomainClassSerializer serializer;

        private DomainClassSerializers(Supplier<DomainClassRendering> rendering) {
            this.rendering = rendering;
        }

        @Override
        public ValueSerializer<?> findSerializer(SerializationConfig config, JavaType type,
                BeanDescription.Supplier beanDescRef, JsonFormat.Value formatOverrides) {
            Class<?> raw = type.getRawClass();
            if (JacksonSerializable.class.isAssignableFrom(raw)) {
                return null;
            }
            DomainClassSerializer domainClassSerializer = serializer();
            if (!domainClassSerializer.getRendering().isDomainClass(raw)) {
                return null;
            }
            // Jackson applies a @JsonValue after the serializers of modules, so a domain class with one is left to it
            return beanDescRef.get().findJsonValueAccessor() == null ? domainClassSerializer : null;
        }

        /**
         * The serializer, with the rendering the supplier gives once the application is available: a rendering
         * supplied before, such as for a type the mapper meets at startup, is not kept, as it knows no domain classes.
         */
        private DomainClassSerializer serializer() {
            DomainClassSerializer current = serializer;
            if (current == null) {
                current = new DomainClassSerializer(rendering.get());
                if (current.getRendering().hasApplication()) {
                    serializer = current;
                }
            }
            return current;
        }
    }
}
