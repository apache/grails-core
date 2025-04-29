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
package org.grails.plugins.databinding

import grails.config.Settings
import grails.plugins.Plugin
import grails.util.GrailsUtil
import grails.web.databinding.DataBindingUtils
import grails.web.databinding.GrailsWebDataBinder
import org.grails.databinding.converters.Jsr310ConvertersConfiguration
import org.grails.databinding.converters.UUIDConverter
import org.grails.web.databinding.bindingsource.DataBindingSourceRegistry
import org.grails.web.databinding.bindingsource.DefaultDataBindingSourceRegistry
import org.grails.web.databinding.bindingsource.HalJsonDataBindingSourceCreator
import org.grails.web.databinding.bindingsource.HalXmlDataBindingSourceCreator
import org.grails.web.databinding.bindingsource.JsonApiDataBindingSourceCreator
import org.grails.web.databinding.bindingsource.JsonDataBindingSourceCreator
import org.grails.web.databinding.bindingsource.XmlDataBindingSourceCreator
import org.grails.databinding.converters.CurrencyValueConverter
import org.grails.databinding.converters.DateConversionHelper
import org.grails.databinding.converters.TimeZoneConverter
import org.grails.databinding.converters.web.LocaleAwareBigDecimalConverter
import org.grails.databinding.converters.web.LocaleAwareNumberConverter

/**
 * Plugin for configuring the data binding features of Grails
 *
 * @author Jeff Brown
 * @author Graeme Rocher
 *
 * @since 2.3
 */
abstract class AbstractDataBindingGrailsPlugin extends Plugin {

    public static final String DEFAULT_JSR310_OFFSET_ZONED_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ"
    public static final String DEFAULT_JSR310_OFFSET_TIME_FORMAT = "HH:mm:ssZ"
    public static final String DEFAULT_JSR310_LOCAL_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"
    public static final String DEFAULT_JSR310_LOCAL_DATE_FORMAT = "yyyy-MM-dd"
    public static final String DEFAULT_JSR310_LOCAL_TIME_FORMAT = "HH:mm:ss"
    public static final List<String> DEFAULT_DATE_FORMATS = ['yyyy-MM-dd HH:mm:ss.S',"yyyy-MM-dd'T'HH:mm:ss'Z'","yyyy-MM-dd HH:mm:ss.S z","yyyy-MM-dd'T'HH:mm:ss.SSSX", DEFAULT_JSR310_OFFSET_ZONED_DATE_TIME_FORMAT, DEFAULT_JSR310_OFFSET_TIME_FORMAT, DEFAULT_JSR310_LOCAL_DATE_TIME_FORMAT, DEFAULT_JSR310_LOCAL_DATE_FORMAT, DEFAULT_JSR310_LOCAL_TIME_FORMAT]

    def version = GrailsUtil.getGrailsVersion()

    @Override
    Closure doWithSpring() {{->
        def application = grailsApplication
        def config = application.config
        boolean trimStringsSetting = config.getProperty(Settings.TRIM_STRINGS, Boolean, true)
        boolean convertEmptyStringsToNullSetting = config.getProperty(Settings.CONVERT_EMPTY_STRINGS_TO_NULL, Boolean, true)
        boolean dateParsingLenientSetting = config.getProperty(Settings.DATE_LENIENT_PARSING, Boolean, false)
        Integer autoGrowCollectionLimitSetting = config.getProperty(Settings.AUTO_GROW_COLLECTION_LIMIT, Integer, 256)
        List dateFormats = config.getProperty(Settings.DATE_FORMATS, List, DEFAULT_DATE_FORMATS)


        "${DataBindingUtils.DATA_BINDER_BEAN_NAME}"(GrailsWebDataBinder, grailsApplication) {
            // trimStrings defaults to TRUE
            trimStrings = trimStringsSetting
            // convertEmptyStringsToNull defaults to TRUE
            convertEmptyStringsToNull = convertEmptyStringsToNullSetting
            // autoGrowCollectionLimit defaults to 256
            autoGrowCollectionLimit = autoGrowCollectionLimitSetting
        }

        dataBindingConfigurationProperties(DataBindingConfigurationProperties)
        timeZoneConverter(TimeZoneConverter)
        uuidConverter(UUIDConverter)

        defaultDateConverter(DateConversionHelper) {
            formatStrings = dateFormats
            // dateParsingLenient defaults to false
            dateParsingLenient = dateParsingLenientSetting
        }
        [Short,   Short.TYPE,
         Integer, Integer.TYPE,
         Float,   Float.TYPE,
         Long,    Long.TYPE,
         Double,  Double.TYPE].each { numberType ->
            "defaultGrails${numberType.simpleName}Converter"(LocaleAwareNumberConverter) {
                targetType = numberType
            }
        }
        defaultGrailsBigDecimalConverter(LocaleAwareBigDecimalConverter) {
            targetType = BigDecimal
        }
        defaultGrailsBigIntegerConverter(LocaleAwareBigDecimalConverter) {
            targetType = BigInteger
        }

        jsr310DataBinding(Jsr310ConvertersConfiguration) {
            formatStrings = dateFormats
        }

        "${DataBindingSourceRegistry.BEAN_NAME}"(DefaultDataBindingSourceRegistry)

        xmlDataBindingSourceCreator(XmlDataBindingSourceCreator)
        jsonDataBindingSourceCreator(JsonDataBindingSourceCreator)
        halJsonDataBindingSourceCreator(HalJsonDataBindingSourceCreator)
        halXmlDataBindingSourceCreator(HalXmlDataBindingSourceCreator)
        jsonApiDataBindingSourceCreator(JsonApiDataBindingSourceCreator)

        defaultCurrencyConverter CurrencyValueConverter
    }}

}
