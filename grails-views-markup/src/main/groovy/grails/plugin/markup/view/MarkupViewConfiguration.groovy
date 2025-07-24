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

package grails.plugin.markup.view

import java.beans.PropertyDescriptor

import groovy.text.markup.TemplateConfiguration
import groovy.transform.CompileStatic

import org.springframework.beans.BeanUtils
import org.springframework.boot.context.properties.ConfigurationProperties

import grails.views.GenericViewConfiguration
import grails.views.ViewsEnvironment
import grails.web.mime.MimeType

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@ConfigurationProperties('grails.views.markup')
class MarkupViewConfiguration extends TemplateConfiguration implements GenericViewConfiguration {

    public static final String MODULE_NAME = "markup"

    List<String> mimeTypes = [MimeType.XML.name, MimeType.HAL_XML.name]

    MarkupViewConfiguration() {
        setExtension(MarkupViewTemplate.EXTENSION)
        setBaseTemplateClass(MarkupViewTemplate)
        setCacheTemplates( !ViewsEnvironment.isDevelopmentMode() )
        setAutoEscape(true)
        setPrettyPrint( ViewsEnvironment.isDevelopmentMode() )
    }

    @Override
    void setPrettyPrint(boolean prettyPrint) {
        setAutoIndent(true)
        setAutoNewLine(true)
    }

    @Override
    void setEncoding(String encoding) {
        GenericViewConfiguration.super.setEncoding(encoding)
        setDeclarationEncoding(encoding)
    }

    @Override
    boolean isCache() {
        return isCacheTemplates()
    }

    @Override
    void setCache(boolean cache) {
        setCacheTemplates(cache)
    }

    @Override
    String getViewModuleName() {
        MODULE_NAME
    }

    PropertyDescriptor[] findViewConfigPropertyDescriptor() {
        List<PropertyDescriptor> allDescriptors = []
        allDescriptors.addAll(BeanUtils.getPropertyDescriptors(GenericViewConfiguration))
        allDescriptors.addAll(BeanUtils.getPropertyDescriptors(TemplateConfiguration))
        return allDescriptors as PropertyDescriptor[]
    }
}
