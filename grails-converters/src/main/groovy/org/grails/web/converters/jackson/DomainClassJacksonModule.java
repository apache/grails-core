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
 * A Jackson module that serializes domain class instances, and proxies of them, as {@code grails.converters.JSON}
 * renders them when {@code grails.converters.json.legacy} is {@code false}, which writes them with this module, and
 * applies the marshallers registered with it and its own rule for enums on top. Grails responses write with it, and a
 * Grails application registers it with the application's {@code JsonMapper} when
 * {@code grails.converters.json.domain.jackson.enabled} is set, so that returning a domain class instance from a Spring
 * MVC controller writes it with the same serializer.
 *
 * <p>An instance is written by Jackson's bean serializer, which is shaped to the domain class: the class name, the id and
 * the version where they apply, then the persistent properties in the order the domain class declares them, with an
 * associated instance as a reference of its id, or in full when rendering deep. What Jackson applies to a bean
 * therefore applies to an instance: the mapper's naming strategy and inclusion, {@code @JsonIgnore},
 * {@code @JsonInclude}, {@code @JsonView}, {@code @JsonFormat}, {@code @JsonProperty}, {@code @JsonNaming},
 * {@code @JsonTypeInfo}, {@code @JsonUnwrapped}, {@code @JsonAnyGetter} on a persistent property, mixins and filters. A
 * domain class with its own {@code @JsonSerialize} serializer keeps it, and so does one with a {@code @JsonValue}, that
 * implements {@code JacksonSerializable}, or that a module has a serializer for, which Jackson writes as it writes any
 * class; an associated instance of such a class is written with that serializer too, unless the rendering writes the
 * references itself, or Jackson cannot build the serializer, which a reference does not need. Whether a proxy is written
 * with such a serializer is decided by the domain class the association declares, since the class of the instance it
 * stands for is known only once it is loaded. A subclass of a domain
 * class that is neither a domain class nor a proxy class of one is written as a Jackson bean.</p>
 *
 * @since 9.0
 */
public class DomainClassJacksonModule extends JacksonModule {

    private final DomainRendering rendering;

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
        this(rendering, false);
    }

    /**
     * @param rendering supplies how to render domain class instances, when the first one is serialized
     * @param renderingsPerWrite whether a write supplies the rendering of each instance it writes, with
     *        {@link #RENDERINGS}, so that the class name and the version are written as that rendering selects. Otherwise
     *        they are properties of the instance only when {@code rendering} writes them, so that they do not conflict
     *        with the properties of a bean the instance is unwrapped into
     */
    public DomainClassJacksonModule(Supplier<DomainClassRendering> rendering, boolean renderingsPerWrite) {
        this.rendering = new DomainRendering(Objects.requireNonNull(rendering, "rendering cannot be null"),
                renderingsPerWrite);
    }

    /**
     * A writer attribute for a writer that renders the domain class instances it writes in its own ways. Its value is a
     * {@code Function<Object, DomainClassRendering>} that gives the rendering of an instance, or {@code null} for the
     * module's. The JSON converter renders each instance with its own includes and excludes this way. A module whose
     * writes do this is created with {@code renderingsPerWrite}.
     */
    public static final String RENDERINGS = DomainClassRendering.class.getName() + ".renderings";

    /**
     * A writer attribute for a writer that is told of each proxy the module writes as the instance it proxies, before it
     * writes the instance. Its value is a {@code BiConsumer<Object, Object>} that is given the proxy and the instance. The
     * JSON converter applies the includes and excludes it has for the class of the proxy to the instance this way.
     *
     * @since 9.0
     */
    public static final String STAND_INS = DomainClassRendering.class.getName() + ".standIns";

    /**
     * A writer attribute for a writer that writes the associated instances of the domain class instances it writes in
     * full, rather than as references of their ids, as {@code grails.converters.json.default.deep} does for every write.
     * Its value is {@link Boolean#TRUE}.
     */
    public static final String DEEP = DomainClassRendering.class.getName() + ".deep";

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
        context.addSerializerModifier(new DomainClassModifier(rendering));
        context.addSerializers(new ProxySerializers(rendering));
    }

    private static final class ProxySerializers implements Serializers {

        private final DomainRendering rendering;

        private final DomainProxySerializer serializer;

        private ProxySerializers(DomainRendering rendering) {
            this.rendering = rendering;
            this.serializer = new DomainProxySerializer(rendering);
        }

        @Override
        public ValueSerializer<?> findSerializer(SerializationConfig config, JavaType type,
                BeanDescription.Supplier beanDescRef, JsonFormat.Value formatOverrides) {
            Class<?> raw = type.getRawClass();
            if (JacksonSerializable.class.isAssignableFrom(raw)) {
                return null;
            }
            DomainClassRendering current = rendering.get();
            // A proxy is written as the instance it proxies, which is a domain class and so written as one
            return current.isDomainClass(raw) && DomainClassRendering.isProxyClass(raw) ? serializer : null;
        }
    }
}
