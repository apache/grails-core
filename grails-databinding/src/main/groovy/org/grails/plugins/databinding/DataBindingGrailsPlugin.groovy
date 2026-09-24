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

import groovy.transform.CompileStatic

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.MessageSource
import org.springframework.core.annotation.AnnotationAwareOrderComparator

import grails.core.GrailsApplication
import grails.databinding.TypedStructuredBindingEditor
import grails.databinding.converters.FormattedValueConverter
import grails.databinding.converters.ValueConverter
import grails.databinding.events.DataBindingListener
import grails.plugins.Plugin
import grails.util.GrailsUtil
import grails.web.databinding.GrailsWebDataBinder
import org.grails.databinding.bindingsource.DataBindingSourceCreator
import org.grails.databinding.converters.DefaultConvertersConfiguration
import org.grails.web.databinding.bindingsource.DataBindingSourceRegistry
import org.grails.web.databinding.bindingsource.DefaultDataBindingSourceRegistry
import org.grails.web.databinding.bindingsource.HalJsonDataBindingSourceCreator
import org.grails.web.databinding.bindingsource.JsonApiDataBindingSourceCreator
import org.grails.web.databinding.bindingsource.JsonDataBindingSourceCreator

/**
 * Plugin for configuring the data binding features of Grails
 *
 * @author Jeff Brown
 * @author Graeme Rocher
 *
 * @since 2.3
 */
@CompileStatic
@AutoConfiguration
@AutoConfigureOrder
@EnableConfigurationProperties(DataBindingConfigurationProperties)
@ImportAutoConfiguration(DefaultConvertersConfiguration)
class DataBindingGrailsPlugin extends Plugin {

    def version = GrailsUtil.getGrailsVersion()

    def beans = {
        // Must be lazily initialized because plugins' ValueConverters and StructuredBindingEditors
        // may be registered through the Grails bean DSL rather than an auto-configuration. For
        // example DataBindingConfigurationSpec defines beans as part of test startup, and without
        // this they would never be wired into the GrailsWebDataBinder bean.
        //
        // configurationProperties was a constructor-injected field on the hand-written class; the
        // generated sibling always has a no-arg constructor, so the one bean that reads it takes it
        // as a parameter instead.
        bean(GrailsWebDataBinder).lazy() { GrailsApplication grailsApplication,
                DataBindingConfigurationProperties configurationProperties,
                ValueConverter[] valueConverters,
                FormattedValueConverter[] formattedValueConverters,
                TypedStructuredBindingEditor[] structuredBindingEditors,
                DataBindingListener[] dataBindingListeners ->

            GrailsWebDataBinder dataBinder = new GrailsWebDataBinder(grailsApplication)
            dataBinder.convertEmptyStringsToNull = configurationProperties.convertEmptyStringsToNull
            dataBinder.trimStrings = configurationProperties.trimStrings
            dataBinder.autoGrowCollectionLimit = configurationProperties.autoGrowCollectionLimit

            ApplicationContext mainContext = grailsApplication.mainContext
            ValueConverter[] allValueConverters = (valueConverters + mainContext.getBeansOfType(ValueConverter).values()) as ValueConverter[]
            AnnotationAwareOrderComparator.sort(allValueConverters)
            dataBinder.valueConverters = allValueConverters

            dataBinder.formattedValueConverters =
                    (formattedValueConverters + mainContext.getBeansOfType(FormattedValueConverter).values()) as FormattedValueConverter[]
            dataBinder.structuredBindingEditors =
                    (structuredBindingEditors + mainContext.getBeansOfType(TypedStructuredBindingEditor).values()) as TypedStructuredBindingEditor[]
            dataBinder.dataBindingListeners =
                    (dataBindingListeners + mainContext.getBeansOfType(DataBindingListener).values()) as DataBindingListener[]

            dataBinder.messageSource = mainContext.getBean('messageSource', MessageSource)
            dataBinder
        }

        // Each of these is nothing but its own no-argument construction, and each type's
        // JavaBeans-derived name is exactly the bean name the hand-written class declared.
        bean(JsonDataBindingSourceCreator)
        bean(HalJsonDataBindingSourceCreator)
        bean(JsonApiDataBindingSourceCreator)

        // Declared as an array rather than the original varargs parameter; Spring resolves both the
        // same way, injecting every DataBindingSourceCreator bean.
        bean(DataBindingSourceRegistry) { DataBindingSourceCreator[] creators ->
            new DefaultDataBindingSourceRegistry().tap {
                dataBindingSourceCreators = creators
                initialize()
            }
        }
    }

}
