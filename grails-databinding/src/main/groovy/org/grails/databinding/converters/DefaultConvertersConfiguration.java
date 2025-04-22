/*
 * Copyright 2004-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.databinding.converters;

import grails.core.GrailsApplication;
import grails.databinding.TypedStructuredBindingEditor;
import grails.databinding.converters.FormattedValueConverter;
import grails.databinding.converters.ValueConverter;
import org.grails.databinding.converters.web.LocaleAwareBigDecimalConverter;
import org.grails.databinding.converters.web.LocaleAwareNumberConverter;
import org.grails.plugins.databinding.DataBindingConfigurationProperties;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Default converters configuration.
 */
@Configuration
public class DefaultConvertersConfiguration {

    private final DataBindingConfigurationProperties configurationProperties;
    private final LocaleResolver localResolver;
    private final Jsr310ConvertersConfiguration jsr310ConvertersConfiguration;

    public DefaultConvertersConfiguration(GrailsApplication grailsApplication, DataBindingConfigurationProperties configurationProperties) {
        this.configurationProperties = configurationProperties;
        jsr310ConvertersConfiguration = new Jsr310ConvertersConfiguration(configurationProperties);
        LocaleResolver localResolver;
        try {
            localResolver = grailsApplication.getMainContext().getBean(LocaleResolver.class);
        } catch (NoSuchBeanDefinitionException e) {
            localResolver = null;
        }
        this.localResolver = localResolver;
    }

    /**
     * @return The default currency converter
     */
    @Bean("defaultCurrencyConverter")
    protected CurrencyValueConverter defaultCurrencyConverter() {
        return new CurrencyValueConverter();
    }

    @Bean("defaultGrailsBigDecimalConverter")
    protected ValueConverter defaultGrailsBigDecimalConverter() {
        LocaleAwareBigDecimalConverter converter = new LocaleAwareBigDecimalConverter();
        converter.setTargetType(BigDecimal.class);
        converter.setLocaleResolver(localResolver);
        return converter;
    }

    @Bean("offsetDateTimeConverter")
    FormattedValueConverter offsetDateTimeConverter() {
        return jsr310ConvertersConfiguration.offsetDateTimeConverter();
    }

    @Bean("offsetDateTimeValueConverter")
    ValueConverter offsetDateTimeValueConverter() {
        return jsr310ConvertersConfiguration.offsetDateTimeValueConverter();
    }

    @Bean("offsetDateTimeStructuredBindingEditor")
    TypedStructuredBindingEditor offsetDateTimeStructuredBindingEditor() {
        return jsr310ConvertersConfiguration.offsetDateTimeStructuredBindingEditor();
    }

    @Bean("offsetTimeConverter")
    FormattedValueConverter offsetTimeConverter() {
        return jsr310ConvertersConfiguration.offsetTimeConverter();
    }

    @Bean("offsetTimeValueConverter")
    ValueConverter offsetTimeValueConverter() {
        return jsr310ConvertersConfiguration.offsetTimeValueConverter();
    }

    @Bean("offsetTimeStructuredBindingEditor")
    TypedStructuredBindingEditor offsetTimeStructuredBindingEditor() {
        return jsr310ConvertersConfiguration.offsetTimeStructuredBindingEditor();
    }

    @Bean("localDateTimeConverter")
    FormattedValueConverter localDateTimeConverter() {
        return jsr310ConvertersConfiguration.localDateTimeConverter();
    }

    @Bean("localDateTimeValueConverter")
    ValueConverter localDateTimeValueConverter() {
        return jsr310ConvertersConfiguration.localDateTimeValueConverter();
    }

    @Bean("localDateTimeStructuredBindingEditor")
    TypedStructuredBindingEditor localDateTimeStructuredBindingEditor() {
        return jsr310ConvertersConfiguration.localDateTimeStructuredBindingEditor();
    }

    @Bean("localDateConverter")
    FormattedValueConverter localDateConverter() {
        return jsr310ConvertersConfiguration.localDateConverter();
    }

    @Bean("localDateValueConverter")
    ValueConverter localDateValueConverter() {
        return jsr310ConvertersConfiguration.localDateValueConverter();
    }

    @Bean("localDateStructuredBindingEditor")
    TypedStructuredBindingEditor localDateStructuredBindingEditor() {
        return jsr310ConvertersConfiguration.localDateStructuredBindingEditor();
    }

    @Bean("localTimeConverter")
    FormattedValueConverter localTimeConverter() {
        return jsr310ConvertersConfiguration.localTimeConverter();
    }

    @Bean("localTimeValueConverter")
    ValueConverter localTimeValueConverter() {
        return jsr310ConvertersConfiguration.localTimeValueConverter();
    }

    @Bean("localTimeStructuredBindingEditor")
    TypedStructuredBindingEditor localTimeStructuredBindingEditor() {
        return jsr310ConvertersConfiguration.localTimeStructuredBindingEditor();
    }

    @Bean("zonedDateTimeConverter")
    FormattedValueConverter zonedDateTimeConverter() {
        return jsr310ConvertersConfiguration.zonedDateTimeConverter();
    }

    @Bean("zonedDateTimeValueConverter")
    ValueConverter zonedDateTimeValueConverter() {
        return jsr310ConvertersConfiguration.zonedDateTimeValueConverter();
    }

    @Bean("zonedDateTimeStructuredBindingEditor")
    TypedStructuredBindingEditor zonedDateTimeStructuredBindingEditor() {
        return jsr310ConvertersConfiguration.zonedDateTimeStructuredBindingEditor();
    }

    @Bean("periodValueConverter")
    ValueConverter periodValueConverter() {
        return jsr310ConvertersConfiguration.periodValueConverter();
    }

    @Bean("instantStringValueConverter")
    ValueConverter instantStringValueConverter() {
        return jsr310ConvertersConfiguration.instantStringValueConverter();
    }

    @Bean("instantValueConverter")
    ValueConverter instantValueConverter() {
        return jsr310ConvertersConfiguration.instantValueConverter();
    }

    @Bean("defaultUUIDConverter")
    protected UUIDConverter defaultuuidConverter() {
        return new UUIDConverter();
    }

    @Bean("defaultGrailsBigIntegerConverter")
    protected ValueConverter defaultGrailsBigIntegerConverter() {
        LocaleAwareBigDecimalConverter converter = new LocaleAwareBigDecimalConverter();
        converter.setTargetType(BigInteger.class);
        converter.setLocaleResolver(localResolver);
        return converter;
    }

    @Bean("defaultDateConverter")
    protected DateConversionHelper defaultDateConverter() {
        DateConversionHelper converter = new DateConversionHelper();
        converter.setDateParsingLenient(configurationProperties.isDateParsingLenient());
        converter.setFormatStrings(configurationProperties.getDateFormats());
        return converter;
    }

    @Bean("defaultLocalDateTimeConverter")
    protected LocalDateTimeConverter defaultLocalDateTimeConverter() {
        return new LocalDateTimeConverter();
    }

    @Bean("timeZoneConverter")
    protected TimeZoneConverter defaultTimeZoneConverter() {
        return new TimeZoneConverter();
    }

    @Bean("defaultShortConverter")
    protected LocaleAwareNumberConverter shortConverter() {
        final LocaleAwareNumberConverter converter = new LocaleAwareNumberConverter();
        converter.setLocaleResolver(localResolver);
        converter.setTargetType(Short.class);
        return converter;
    }

    @Bean("defaultshortConverter")
    protected LocaleAwareNumberConverter primitiveShortConverter() {
        final LocaleAwareNumberConverter converter = new LocaleAwareNumberConverter();
        converter.setLocaleResolver(localResolver);
        converter.setTargetType(short.class);
        return converter;
    }

    @Bean("defaultIntegerConverter")
    protected LocaleAwareNumberConverter integerConverter() {
        final LocaleAwareNumberConverter converter = new LocaleAwareNumberConverter();
        converter.setLocaleResolver(localResolver);
        converter.setTargetType(Integer.class);
        return converter;
    }

    @Bean("defaultintConverter")
    protected LocaleAwareNumberConverter primitiveIntConverter() {
        final LocaleAwareNumberConverter converter = new LocaleAwareNumberConverter();
        converter.setLocaleResolver(localResolver);
        converter.setTargetType(int.class);
        return converter;
    }

    @Bean("defaultFloatConverter")
    protected LocaleAwareNumberConverter floatConverter() {
        final LocaleAwareNumberConverter converter = new LocaleAwareNumberConverter();
        converter.setLocaleResolver(localResolver);
        converter.setTargetType(Float.class);
        return converter;
    }

    @Bean("defaultfloatConverter")
    protected LocaleAwareNumberConverter primitiveFloattConverter() {
        final LocaleAwareNumberConverter converter = new LocaleAwareNumberConverter();
        converter.setLocaleResolver(localResolver);
        converter.setTargetType(float.class);
        return converter;
    }

    @Bean("defaultLongConverter")
    protected LocaleAwareNumberConverter longConverter() {
        final LocaleAwareNumberConverter converter = new LocaleAwareNumberConverter();
        converter.setLocaleResolver(localResolver);
        converter.setTargetType(Long.class);
        return converter;
    }

    @Bean("defaultlongConverter")
    protected LocaleAwareNumberConverter primitiveLongConverter() {
        final LocaleAwareNumberConverter converter = new LocaleAwareNumberConverter();
        converter.setLocaleResolver(localResolver);
        converter.setTargetType(long.class);
        return converter;
    }

    @Bean("defaultDoubleConverter")
    protected LocaleAwareNumberConverter doubleConverter() {
        final LocaleAwareNumberConverter converter = new LocaleAwareNumberConverter();
        converter.setLocaleResolver(localResolver);
        converter.setTargetType(Double.class);
        return converter;
    }

    @Bean("defaultdoubleConverter")
    protected LocaleAwareNumberConverter primitiveDoubleConverter() {
        final LocaleAwareNumberConverter converter = new LocaleAwareNumberConverter();
        converter.setLocaleResolver(localResolver);
        converter.setTargetType(double.class);
        return converter;
    }
}
