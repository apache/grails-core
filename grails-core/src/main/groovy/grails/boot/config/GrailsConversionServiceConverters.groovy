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
package grails.boot.config

import groovy.transform.CompileStatic
import org.springframework.context.ApplicationContext
import org.springframework.core.convert.TypeDescriptor
import org.springframework.core.convert.converter.ConditionalGenericConverter
import org.springframework.core.convert.converter.GenericConverter.ConvertiblePair
import org.springframework.core.convert.support.ConfigurableConversionService
import org.springframework.core.io.Resource
import org.springframework.core.io.support.ResourceArrayPropertyEditor

import org.grails.config.NavigableMap

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
 * @since 8.0
 */
@CompileStatic
final class GrailsConversionServiceConverters {

    private GrailsConversionServiceConverters() {
    }

    static void register(ConfigurableConversionService conversionService, ApplicationContext applicationContext) {
        if (isRegistered(conversionService, applicationContext)) {
            return
        }
        conversionService.addConverter(String, Resource, applicationContext::getResource)
        conversionService.addConverter(new ResourceArrayConverter(applicationContext))
        conversionService.addConverter(NavigableMap.NullSafeNavigator, String, source -> null)
        conversionService.addConverter(NavigableMap.NullSafeNavigator, Object, source -> null)
        conversionService.addConverter(RegistrationMarker, ApplicationContext, marker -> applicationContext)
    }

    private static boolean isRegistered(ConfigurableConversionService conversionService, ApplicationContext applicationContext) {
        return conversionService.canConvert(RegistrationMarker, ApplicationContext) &&
                conversionService.convert(RegistrationMarker.INSTANCE, ApplicationContext) == applicationContext
    }

    /**
     * Records on the conversion service which application context the converters were registered
     * for, since a conversion service does not expose the converters it holds.
     */
    private enum RegistrationMarker {
        INSTANCE
    }

    /**
     * Converts location patterns to {@code Resource[]} the way Spring's default
     * {@link ResourceArrayPropertyEditor} does: each pattern expands to every matching resource,
     * a comma-delimited String holds several patterns, placeholders are resolved against the
     * environment, and {@link Resource} elements are kept as they are.
     */
    private static final class ResourceArrayConverter implements ConditionalGenericConverter {

        private static final Set<ConvertiblePair> CONVERTIBLE_TYPES = Set.of(
                new ConvertiblePair(String, Resource[]),
                new ConvertiblePair(Object[], Resource[]),
                new ConvertiblePair(Collection, Resource[]))

        private final ApplicationContext applicationContext

        private ResourceArrayConverter(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext
        }

        @Override
        Set<ConvertiblePair> getConvertibleTypes() {
            return CONVERTIBLE_TYPES
        }

        @Override
        boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
            // Elements other than locations and resources are left to the other converters.
            TypeDescriptor elementType = sourceType.getElementTypeDescriptor()
            if (elementType == null) {
                return true
            }
            Class<?> type = elementType.getType()
            return type == Object || type == String || Resource.isAssignableFrom(type)
        }

        @Override
        Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
            if (source == null) {
                return null
            }
            ResourceArrayPropertyEditor editor =
                    new ResourceArrayPropertyEditor(applicationContext, applicationContext.getEnvironment())
            if (source instanceof String text) {
                editor.setAsText(text)
            }
            else {
                editor.setValue(source)
            }
            return editor.getValue()
        }
    }
}
