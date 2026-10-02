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
 * <p>A domain class with its own {@code @JsonSerialize} serializer keeps it.
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
            DomainClassSerializer domainClassSerializer = serializer();
            return domainClassSerializer.getRendering().isDomainClass(type.getRawClass()) ? domainClassSerializer : null;
        }

        private DomainClassSerializer serializer() {
            DomainClassSerializer current = serializer;
            if (current == null) {
                current = new DomainClassSerializer(rendering.get());
                serializer = current;
            }
            return current;
        }
    }
}
