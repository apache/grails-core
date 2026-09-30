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

package org.grails.datastore.gorm.jdbc;

import java.util.EnumSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.util.Assert;

/**
 * Internal {@link ConversionService} used by {@link RelaxedDataBinder} to support
 * additional relaxed conversion.
 *
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @since 1.1.0
 */
class RelaxedConversionService implements ConversionService {

    private final ConversionService conversionService;

    private final GenericConversionService additionalConverters;

    /**
     * Create a new {@link RelaxedConversionService} instance.
     * @param conversionService and option root conversion service
     */
    RelaxedConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
        this.additionalConverters = new GenericConversionService();
        DefaultConversionService.addDefaultConverters(this.additionalConverters);
        this.additionalConverters
                .addConverterFactory(new StringToEnumIgnoringCaseConverterFactory());
        this.additionalConverters.addConverter(new StringToCharArrayConverter());
    }

    @Override
    public boolean canConvert(@Nullable Class<?> sourceType, Class<?> targetType) {
        return (this.conversionService != null &&
                this.conversionService.canConvert(sourceType, targetType)) ||
                this.additionalConverters.canConvert(sourceType, targetType);
    }

    @Override
    public boolean canConvert(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
        return (this.conversionService != null &&
                this.conversionService.canConvert(sourceType, targetType)) ||
                this.additionalConverters.canConvert(sourceType, targetType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T convert(@Nullable Object source, Class<T> targetType) {
        Assert.notNull(targetType, "The targetType to convert to cannot be null");
        return (T) convert(source, TypeDescriptor.forObject(source),
                TypeDescriptor.valueOf(targetType));
    }

    @Override
    public @Nullable Object convert(@Nullable Object source, @Nullable TypeDescriptor sourceType,
                          TypeDescriptor targetType) {
        if (this.conversionService != null) {
            try {
                return this.conversionService.convert(source, sourceType, targetType);
            }
            catch (ConversionFailedException ex) {
                // Ignore and try the additional converters
            }
        }
        return this.additionalConverters.convert(source, sourceType, targetType);
    }

    /**
     * Clone of Spring's package private StringToEnumConverterFactory, but ignoring the
     * case of the source.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static class StringToEnumIgnoringCaseConverterFactory
            implements ConverterFactory<String, Enum> {

        @Override
        public <T extends Enum> Converter<String, ? extends @Nullable T> getConverter(Class<T> targetType) {
            Class<?> enumType = targetType;
            while (enumType != null && !enumType.isEnum()) {
                enumType = enumType.getSuperclass();
            }
            Assert.notNull(enumType, "The target type " + targetType.getName() +
                    " does not refer to an enum");
            return new StringToEnum(enumType);
        }

        private record StringToEnum<T extends Enum>(Class<T> enumType) implements Converter<String, @Nullable T> {

            @Override
            public @Nullable T convert(String source) {
                if (source.isEmpty()) {
                    // It's an empty enum identifier: reset the enum value to null.
                    return null;
                }
                source = source.trim();
                for (T candidate : (Set<T>) EnumSet.allOf(this.enumType)) {
                    RelaxedNames names = new RelaxedNames(
                            candidate.name().replace("_", "-").toLowerCase());
                    for (String name : names) {
                        if (name.equals(source)) {
                            return candidate;
                        }
                    }
                    if (candidate.name().equalsIgnoreCase(source)) {
                        return candidate;
                    }
                }
                throw new IllegalArgumentException("No enum constant " +
                        this.enumType.getCanonicalName() + "." + source);
            }

        }

    }

    private static class StringToCharArrayConverter implements Converter<String, char[]> {
        @Override
        public char[] convert(String source) {
            return source.toCharArray();
        }
    }
}
