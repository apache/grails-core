/*
 * Copyright 2014-2024 the original author or authors.
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
package org.grails.web.converters

import grails.converters.JSON
import grails.converters.XML
import groovy.transform.CompileStatic

import jakarta.servlet.http.HttpServletRequest

import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.context.ApplicationContext


/**
 *
 *
 *
 * @author Jeff Brown
 * @author Graeme Rocher
 *
 * @since 3.0
 *
 */
@CompileStatic
class ConvertersExtension {
    
    static getJSON(HttpServletRequest request) {
        JSON.parse(request)
    }

    static getXML(HttpServletRequest request) {
        XML.parse(request)
    }
    
    static <T> T asType(instance, Class<T> clazz) {
        if (ConverterUtil.isConverterClass(clazz)) {
            return ConverterUtil.createConverter(clazz, 
                                                 instance, 
                                                 GrailsWebRequest.lookup()?.applicationContext)
        }
        else {
            return (T)ConverterUtil.invokeOriginalAsTypeMethod(instance, clazz)
        }
    }
    
    static <T> T  asType(Object[] array, Class<T> clazz) {
        asType((Object)array, clazz)
    }
}
