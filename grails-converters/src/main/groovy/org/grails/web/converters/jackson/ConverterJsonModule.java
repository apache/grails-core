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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.Version;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.module.SimpleSerializers;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.databind.ser.jackson.JsonValueSerializer;
import tools.jackson.databind.ser.jdk.EnumSerializer;
import tools.jackson.databind.ser.std.StdScalarSerializer;

import org.springframework.context.MessageSource;
import org.springframework.validation.Errors;

import grails.persistence.PersistenceMethod;
import grails.web.controllers.ControllerMethod;

/**
 * The rules {@code grails.converters.JSON} adds to the mapper it writes with: an enum is written by its name, so that it
 * binds back, validation errors are written without the values that were rejected, a bean leaves out the properties of the methods annotated {@code @PersistenceMethod} and
 * {@code @ControllerMethod}, and the converter's includes and excludes for the class of a bean decide which of its
 * properties are written.
 *
 * @since 9.0
 */
public final class ConverterJsonModule extends JacksonModule {

    private final Supplier<MessageSource> messageSource;

    /**
     * @param messageSource supplies the message source that resolves the messages of validation errors, or
     *        {@code null} for their default messages
     */
    public ConverterJsonModule(Supplier<MessageSource> messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * A writer attribute for a writer that includes only some of the properties of the beans it writes. Its value is a
     * {@code Function<Object, Predicate<String>>} that gives, for a bean, whether a property is written by its name, or
     * {@code null} for all of them.
     */
    public static final String PROPERTY_FILTERS = ConverterJsonModule.class.getName() + ".propertyFilters";

    @Override
    public String getModuleName() {
        return "grails-converter-json";
    }

    @Override
    public Version version() {
        return Version.unknownVersion();
    }

    @Override
    public void setupModule(SetupContext context) {
        // Validation errors are written without the values that were rejected, which can be passwords or tokens
        SimpleSerializers serializers = new SimpleSerializers();
        serializers.addSerializer(Errors.class, new SpringErrorsJsonSerializer(messageSource));
        context.addSerializers(serializers);
        context.addSerializerModifier(new ValueSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription.Supplier beanDescription,
                    List<BeanPropertyWriter> properties) {
                Map<String, String> internalNames = new HashMap<>();
                for (BeanPropertyDefinition definition : beanDescription.get().findProperties()) {
                    internalNames.put(definition.getName(), definition.getInternalName());
                }
                List<BeanPropertyWriter> written = new ArrayList<>(properties.size());
                for (BeanPropertyWriter writer : properties) {
                    if (writer.getAnnotation(PersistenceMethod.class) == null &&
                            writer.getAnnotation(ControllerMethod.class) == null) {
                        written.add(GatedPropertyWriter.gated(writer,
                                included(internalNames.getOrDefault(writer.getName(), writer.getName()))));
                    }
                }
                return written;
            }

            @Override
            public ValueSerializer<?> modifySerializer(SerializationConfig config, BeanDescription.Supplier beanDescription,
                    ValueSerializer<?> serializer) {
                return byName(beanDescription.get().getBeanClass(), serializer);
            }

            @Override
            public ValueSerializer<?> modifyEnumSerializer(SerializationConfig config, JavaType valueType,
                    BeanDescription.Supplier beanDescription, ValueSerializer<?> serializer) {
                return byName(valueType.getRawClass(), serializer);
            }
        });
    }

    /**
     * The converter's includes and excludes for the class of the bean being written, which name its properties as the
     * class does.
     */
    @SuppressWarnings("unchecked")
    private static GatedPropertyWriter.Gate included(String property) {
        return (bean, context, writer) -> {
            if (context.getAttribute(PROPERTY_FILTERS) instanceof Function<?, ?> filters) {
                Predicate<String> filter = ((Function<Object, Predicate<String>>) filters).apply(bean);
                return filter == null || filter.test(property);
            }
            return true;
        };
    }

    /**
     * The serializer of an enum that Jackson writes by {@code toString()}, by {@code @JsonValue} or by name, replaced by one
     * that writes the name. An enum with a serializer of its own, such as {@code Month}, keeps it.
     */
    private static ValueSerializer<?> byName(Class<?> type, ValueSerializer<?> serializer) {
        if (!Enum.class.isAssignableFrom(type)) {
            return serializer;
        }
        ValueSerializer<?> innermost = serializer;
        while (innermost.getDelegatee() != null && innermost.getDelegatee() != innermost) {
            innermost = innermost.getDelegatee();
        }
        return innermost instanceof EnumSerializer || innermost instanceof JsonValueSerializer ?
                replaceInnermost(serializer, new EnumNameSerializer()) : serializer;
    }

    /**
     * Replaces the serializer that does the writing, inside the serializers another module wrapped it with.
     */
    private static ValueSerializer<?> replaceInnermost(ValueSerializer<?> serializer, ValueSerializer<?> replacement) {
        ValueSerializer<?> delegatee = serializer.getDelegatee();
        if (delegatee == null || delegatee == serializer) {
            return replacement;
        }
        return serializer.replaceDelegatee(replaceInnermost(delegatee, replacement));
    }

    /** A scalar, as Jackson's own enum serializer is, so that it is also written with a type id. */
    private static final class EnumNameSerializer extends StdScalarSerializer<Enum<?>> {

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private EnumNameSerializer() {
            super((Class) Enum.class);
        }

        @Override
        public void serialize(Enum<?> value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(value.name());
        }
    }
}
