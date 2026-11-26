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
package grails.boot.config;

import java.util.Collection;
import java.util.Set;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceArrayPropertyEditor;

import org.grails.config.NavigableMap;

/**
 * The converters the Grails lifecycle adds to the environment's conversion service, shared by
 * {@link GrailsEarlyPluginRegistrationPostProcessor} and {@link GrailsApplicationPostProcessor} so
 * that both phases convert configuration and bean property values the same way.
 *
 * <p>Boot installs the environment's conversion service on the bean factory too, and the bean
 * factory consults it before its default property editors. Registering {@code String -> Resource}
 * therefore also lets the generic array and collection converters reach {@code Resource[]} one
 * element at a time, which would turn a {@code classpath*:} pattern into a single resource with the
 * pattern as its literal path. The {@code Resource[]} converter registered here keeps Spring's
 * pattern-aware {@link ResourceArrayPropertyEditor} semantics for those values.
 *
 * <p>Both phases run for the same context when the early phase is active, so registration is
 * idempotent per conversion service and application context.
 *
 * <p>{@link #registerOnBeanFactory} gives the bean factory the conversion of a missing configuration
 * key to {@code null}, before any bean definition is registered, so a bean of an application's
 * {@code resources.groovy} or {@code doWithSpring}, or of a plugin's bean builder, that assigns
 * {@code grailsApplication.config.some.missing.key} gets {@code null} rather than the navigator that
 * stands for the missing key. A Spring Boot context shares the environment's conversion service with
 * its bean factory, so {@link #register} already covers it; another context gets the converter here.
 *
 * @since 8.0
 */
final class GrailsConversionServiceConverters {

    private GrailsConversionServiceConverters() {
    }

    static void register(ConfigurableConversionService conversionService, ApplicationContext applicationContext) {
        if (isRegistered(conversionService, applicationContext)) {
            return;
        }
        conversionService.addConverter(String.class, Resource.class, applicationContext::getResource);
        conversionService.addConverter(new ResourceArrayConverter(applicationContext));
        conversionService.addConverter(NavigableMap.NullSafeNavigator.class, String.class, source -> null);
        conversionService.addConverter(NavigableMap.NullSafeNavigator.class, Object.class, source -> null);
        conversionService.addConverter(RegistrationMarker.class, ApplicationContext.class, marker -> applicationContext);
    }

    static void registerOnBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        ConversionService existing = beanFactory.getConversionService();
        if (existing == null) {
            GenericConversionService conversionService = new GenericConversionService();
            addNullSafeNavigatorConverter(conversionService);
            beanFactory.setConversionService(conversionService);
        }
        else if (existing instanceof ConverterRegistry registry &&
                !existing.canConvert(NullSafeNavigatorMarker.class, NullSafeNavigatorConverterAdded.class)) {
            addNullSafeNavigatorConverter(registry);
        }
    }

    private static void addNullSafeNavigatorConverter(ConverterRegistry registry) {
        registry.addConverter(NavigableMap.NullSafeNavigator.class, String.class, source -> null);
        registry.addConverter(NavigableMap.NullSafeNavigator.class, Object.class, source -> null);
        registry.addConverter(NullSafeNavigatorMarker.class, NullSafeNavigatorConverterAdded.class,
                marker -> NullSafeNavigatorConverterAdded.INSTANCE);
    }

    private static boolean isRegistered(ConfigurableConversionService conversionService, ApplicationContext applicationContext) {
        return conversionService.canConvert(RegistrationMarker.class, ApplicationContext.class) &&
                conversionService.convert(RegistrationMarker.INSTANCE, ApplicationContext.class) == applicationContext;
    }

    /**
     * Records on the conversion service which application context the converters were registered
     * for, since a conversion service does not expose the converters it holds.
     */
    private enum RegistrationMarker {
        INSTANCE
    }

    /**
     * Records on a bean factory's conversion service that the missing-key converter was added, so it
     * is added once however many phases run for the context.
     */
    private enum NullSafeNavigatorMarker {
        INSTANCE
    }

    /**
     * What only the converter of {@link NullSafeNavigatorMarker} converts to.
     */
    private static final class NullSafeNavigatorConverterAdded {

        private static final NullSafeNavigatorConverterAdded INSTANCE = new NullSafeNavigatorConverterAdded();

        private NullSafeNavigatorConverterAdded() {
        }
    }

    /**
     * Converts location patterns to {@code Resource[]} the way Spring's default
     * {@link ResourceArrayPropertyEditor} does: each pattern expands to every matching resource,
     * a comma-delimited String holds several patterns, placeholders are resolved against the
     * environment, and {@link Resource} elements are kept as they are.
     */
    private static final class ResourceArrayConverter implements ConditionalGenericConverter {

        private static final Set<ConvertiblePair> CONVERTIBLE_TYPES = Set.of(
                new ConvertiblePair(String.class, Resource[].class),
                new ConvertiblePair(Object[].class, Resource[].class),
                new ConvertiblePair(Collection.class, Resource[].class));

        private final ApplicationContext applicationContext;

        private ResourceArrayConverter(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
        }

        @Override
        public Set<ConvertiblePair> getConvertibleTypes() {
            return CONVERTIBLE_TYPES;
        }

        @Override
        public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
            // Elements other than locations and resources are left to the other converters.
            TypeDescriptor elementType = sourceType.getElementTypeDescriptor();
            if (elementType == null) {
                return true;
            }
            Class<?> type = elementType.getType();
            return type == Object.class || type == String.class || Resource.class.isAssignableFrom(type);
        }

        @Override
        public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
            if (source == null) {
                return null;
            }
            ResourceArrayPropertyEditor editor =
                    new ResourceArrayPropertyEditor(applicationContext, applicationContext.getEnvironment());
            if (source instanceof String text) {
                editor.setAsText(text);
            }
            else {
                editor.setValue(source);
            }
            return editor.getValue();
        }
    }
}
