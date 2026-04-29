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

package grails.plugin.json.view

import groovy.json.JsonGenerator
import groovy.text.Template
import groovy.transform.CompileStatic
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.OrderComparator

import grails.plugin.json.converters.InstantJsonConverter
import grails.plugin.json.converters.LocalDateJsonConverter
import grails.plugin.json.converters.LocalDateTimeJsonConverter
import grails.plugin.json.converters.LocalTimeJsonConverter
import grails.plugin.json.converters.OffsetDateTimeJsonConverter
import grails.plugin.json.converters.OffsetTimeJsonConverter
import grails.plugin.json.converters.PeriodJsonConverter
import grails.plugin.json.converters.ZonedDateTimeJsonConverter
import grails.plugin.json.view.api.jsonapi.JsonApiIdRenderStrategy
import grails.plugin.json.view.internal.JsonTemplateTypeCheckingExtension
import grails.plugin.json.view.internal.JsonViewsTransform
import grails.plugin.json.view.template.JsonViewTemplate
import grails.views.ResolvableGroovyTemplateEngine
import grails.views.ViewConfiguration
import grails.views.WritableScriptTemplate
import grails.views.api.GrailsView
import grails.views.compiler.ViewsTransform

/**
 * A template engine for parsing JSON views
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class JsonViewTemplateEngine extends ResolvableGroovyTemplateEngine {

    private final boolean compileStatic

    final JsonGenerator generator

    @Autowired
    JsonApiIdRenderStrategy jsonApiIdRenderStrategy

    /**
     * Constructs a JsonTemplateEngine with the default configuration
     */
    JsonViewTemplateEngine() {
        this(new JsonViewConfiguration(), Thread.currentThread().contextClassLoader)
    }

    /**
     * Constructs a JsonTemplateEngine with the default configuration
     */
    JsonViewTemplateEngine(ClassLoader classLoader) {
        this(new JsonViewConfiguration(), classLoader)
    }

    /**
     * Constructs a JsonTemplateEngine with a custom base class
     *
     * @param baseClassName The name of the base class
     */
    JsonViewTemplateEngine(ViewConfiguration configuration, ClassLoader classLoader) {
        super(configuration, classLoader)
        this.compileStatic = configuration.compileStatic

        JsonGenerator.Options options = new JsonGenerator.Options()
        JsonViewGeneratorConfiguration config = ((JsonViewConfiguration) configuration).generator

        if (!config.escapeUnicode) {
            options.disableUnicodeEscaping()
        }
        Locale locale
        String[] localeData = config.locale.split('/')
        if (localeData.length > 1) {
            locale = new Locale(localeData[0], localeData[1])
        } else {
            locale = new Locale(localeData[0])
        }

        options.dateFormat(config.dateFormat, locale)
        options.timezone(config.timeZone)

        Map<String, JsonGenerator.Converter> convertersByClass = new LinkedHashMap<>()
        registerConverters(ServiceLoader.load(JsonGenerator.Converter, classLoader), convertersByClass)
        List<JsonGenerator.Converter> converters = new ArrayList<>(convertersByClass.values())
        converters.add(new InstantJsonConverter())
        converters.add(new LocalDateJsonConverter())
        converters.add(new LocalDateTimeJsonConverter())
        converters.add(new LocalTimeJsonConverter())
        converters.add(new OffsetDateTimeJsonConverter())
        converters.add(new OffsetTimeJsonConverter())
        converters.add(new PeriodJsonConverter())
        converters.add(new ZonedDateTimeJsonConverter())
        OrderComparator.sort(converters)
        converters.each {
            options.addConverter(it)
        }

        this.generator = options.build()
    }

    private static void registerConverters(Iterable<? extends JsonGenerator.Converter> source,
                                           Map<String, JsonGenerator.Converter> target) {
        for (JsonGenerator.Converter converter : source) {
            target.putIfAbsent(converter.getClass().getName(), converter)
        }
    }

    @Override
    protected void prepareCustomizers(CompilerConfiguration compilerConfiguration) {
        super.prepareCustomizers(compilerConfiguration)
        if (compileStatic) {
            compilerConfiguration.addCompilationCustomizers(
                    new ASTTransformationCustomizer(Collections.singletonMap('extensions', JsonTemplateTypeCheckingExtension.name), CompileStatic))
        }

    }

    @Override
    protected ViewsTransform newViewsTransform() {
        return new JsonViewsTransform(viewConfiguration.extension)
    }

    @Override
    String getDynamicTemplatePrefix() {
        'JsonView'.intern()
    }

    @Override
    protected WritableScriptTemplate createTemplate(Class<? extends Template> cls, File sourceFile) {
        def template = new JsonViewTemplate((Class<? extends GrailsView>) cls, sourceFile)
        template.generator = this.generator
        template.jsonApiIdRenderStrategy = this.jsonApiIdRenderStrategy
        return initializeTemplate(template, sourceFile)
    }

}
