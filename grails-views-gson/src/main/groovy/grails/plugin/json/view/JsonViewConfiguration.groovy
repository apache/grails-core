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

import grails.views.GenericViewConfiguration
import grails.web.mime.MimeType
import groovy.transform.CompileStatic
import org.springframework.beans.BeanUtils
import org.springframework.boot.context.properties.ConfigurationProperties

import java.beans.PropertyDescriptor

/**
 * Default configuration for JSON views
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@ConfigurationProperties('grails.views.json')
class JsonViewConfiguration implements GenericViewConfiguration {

    public static final String MODULE_NAME = "json"

    List<String> mimeTypes = [MimeType.JSON.name, MimeType.HAL_JSON.name]

    JsonViewGeneratorConfiguration generator = new JsonViewGeneratorConfiguration()

    JsonViewConfiguration() {
        setExtension(JsonViewWritableScript.EXTENSION)
        setCompileStatic(true)
        setBaseTemplateClass(JsonViewWritableScript)
    }

    @Override
    String getViewModuleName() {
        MODULE_NAME
    }

    PropertyDescriptor[] findViewConfigPropertyDescriptor() {
        BeanUtils.getPropertyDescriptors(GenericViewConfiguration)
    }

}
