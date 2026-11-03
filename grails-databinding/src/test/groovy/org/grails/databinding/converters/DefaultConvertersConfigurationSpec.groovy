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
package org.grails.databinding.converters

import org.springframework.context.support.StaticApplicationContext
import org.springframework.web.servlet.LocaleResolver
import spock.lang.Specification

import grails.core.GrailsApplication
import org.grails.databinding.converters.web.LocaleAwareBigDecimalConverter
import org.grails.databinding.converters.web.LocaleAwareNumberConverter
import org.grails.plugins.databinding.DataBindingConfigurationProperties

class DefaultConvertersConfigurationSpec extends Specification {

    DataBindingConfigurationProperties properties = new DataBindingConfigurationProperties()

    private DefaultConvertersConfiguration configuration(LocaleResolver localeResolver) {
        StaticApplicationContext ctx = new StaticApplicationContext()
        if (localeResolver != null) {
            ctx.beanFactory.registerSingleton('localeResolver', localeResolver)
        }
        ctx.refresh()
        GrailsApplication application = Stub(GrailsApplication) { getMainContext() >> ctx }
        new DefaultConvertersConfiguration(application, properties)
    }

    void 'the configuration properties default to the grails data binding settings'() {
        expect:
        properties.trimStrings
        properties.convertEmptyStringsToNull
        properties.autoGrowCollectionLimit == 256
        !properties.dateParsingLenient
        properties.dateFormats == ['yyyy-MM-dd HH:mm:ss.S', "yyyy-MM-dd'T'HH:mm:ss'Z'", 'yyyy-MM-dd HH:mm:ss.S z', "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                                   "yyyy-MM-dd'T'HH:mm:ssZ", 'HH:mm:ssZ', "yyyy-MM-dd'T'HH:mm:ss", 'yyyy-MM-dd', 'HH:mm:ss']

        when:
        properties.trimStrings = false
        properties.convertEmptyStringsToNull = false
        properties.autoGrowCollectionLimit = 5
        properties.dateParsingLenient = true
        properties.dateFormats = ['dd/MM/yyyy']

        then:
        !properties.trimStrings
        !properties.convertEmptyStringsToNull
        properties.autoGrowCollectionLimit == 5
        properties.dateParsingLenient
        properties.dateFormats == ['dd/MM/yyyy']
    }

    void 'the number converters carry the locale resolver and their target types'() {
        given:
        LocaleResolver resolver = Stub(LocaleResolver)
        DefaultConvertersConfiguration config = configuration(resolver)

        expect:
        ((LocaleAwareBigDecimalConverter) config.defaultGrailsBigDecimalConverter()).targetType == BigDecimal
        ((LocaleAwareBigDecimalConverter) config.defaultGrailsBigDecimalConverter()).localeResolver.is(resolver)
        ((LocaleAwareBigDecimalConverter) config.defaultGrailsBigIntegerConverter()).targetType == BigInteger
        config.shortConverter().targetType == Short
        config.primitiveShortConverter().targetType == short
        config.integerConverter().targetType == Integer
        config.primitiveIntConverter().targetType == int
        config.floatConverter().targetType == Float
        config.primitiveFloattConverter().targetType == float
        config.longConverter().targetType == Long
        config.primitiveLongConverter().targetType == long
        config.doubleConverter().targetType == Double
        config.primitiveDoubleConverter().targetType == double
        [config.shortConverter(), config.integerConverter(), config.doubleConverter()].every { LocaleAwareNumberConverter c -> c.localeResolver.is(resolver) }
        config.defaultCurrencyConverter() instanceof CurrencyValueConverter
        config.defaultuuidConverter() instanceof UUIDConverter
        config.defaultLocalDateTimeConverter() instanceof LocalDateTimeConverter
        config.defaultTimeZoneConverter() instanceof TimeZoneConverter
    }

    void 'a missing locale resolver is tolerated'() {
        given:
        DefaultConvertersConfiguration config = configuration(null)

        expect:
        ((LocaleAwareBigDecimalConverter) config.defaultGrailsBigDecimalConverter()).localeResolver == null
        config.integerConverter().localeResolver == null
    }

    void 'the date converter takes its settings from the configuration properties'() {
        given:
        properties.dateParsingLenient = true
        properties.dateFormats = ['dd/MM/yyyy']
        DefaultConvertersConfiguration config = configuration(null)

        when:
        DateConversionHelper converter = config.defaultDateConverter()

        then:
        converter.dateParsingLenient
        converter.formatStrings == ['dd/MM/yyyy']
    }

    void 'the jsr310 beans delegate to the jsr310 configuration'() {
        given:
        DefaultConvertersConfiguration config = configuration(null)
        Jsr310ConvertersConfiguration jsr310 = new Jsr310ConvertersConfiguration(properties)

        expect:
        config.offsetDateTimeConverter().class == jsr310.offsetDateTimeConverter().class
        config.offsetDateTimeValueConverter().class == jsr310.offsetDateTimeValueConverter().class
        config.offsetDateTimeStructuredBindingEditor().class == jsr310.offsetDateTimeStructuredBindingEditor().class
        config.offsetTimeConverter().class == jsr310.offsetTimeConverter().class
        config.offsetTimeValueConverter().class == jsr310.offsetTimeValueConverter().class
        config.offsetTimeStructuredBindingEditor().class == jsr310.offsetTimeStructuredBindingEditor().class
        config.localDateTimeConverter().class == jsr310.localDateTimeConverter().class
        config.localDateTimeValueConverter().class == jsr310.localDateTimeValueConverter().class
        config.localDateTimeStructuredBindingEditor().class == jsr310.localDateTimeStructuredBindingEditor().class
        config.localDateConverter().class == jsr310.localDateConverter().class
        config.localDateValueConverter().class == jsr310.localDateValueConverter().class
        config.localDateStructuredBindingEditor().class == jsr310.localDateStructuredBindingEditor().class
        config.localTimeConverter().class == jsr310.localTimeConverter().class
        config.localTimeValueConverter().class == jsr310.localTimeValueConverter().class
        config.localTimeStructuredBindingEditor().class == jsr310.localTimeStructuredBindingEditor().class
        config.zonedDateTimeConverter().class == jsr310.zonedDateTimeConverter().class
        config.zonedDateTimeValueConverter().class == jsr310.zonedDateTimeValueConverter().class
        config.zonedDateTimeStructuredBindingEditor().class == jsr310.zonedDateTimeStructuredBindingEditor().class
        config.periodValueConverter().class == jsr310.periodValueConverter().class
        config.instantStringValueConverter().class == jsr310.instantStringValueConverter().class
        config.instantValueConverter().class == jsr310.instantValueConverter().class
        config.instantStructuredBindingEditor().class == jsr310.instantStructuredBindingEditor().class
    }

}
