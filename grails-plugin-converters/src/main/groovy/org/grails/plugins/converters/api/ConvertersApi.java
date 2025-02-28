/*
 * Copyright 2011 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.converters.api;

import org.grails.web.converters.ConverterUtil;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * The API for converting objects to target types such as XML.
 *
 * @since 2.0
 * @author Graeme Rocher
 */
public class ConvertersApi implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    /**
     * Converter an instance from one format to another
     *
     * @param instance The instance
     * @param clazz The type to convert to
     * @return the converted object
     */
    public Object asType(Object instance, Class<?> clazz) {
        if (ConverterUtil.isConverterClass(clazz)) {
            return ConverterUtil.createConverter(clazz, instance, getApplicationContext());
        }
        return ConverterUtil.invokeOriginalAsTypeMethod(instance, clazz);
    }

    public ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            GrailsWebRequest webRequest = GrailsWebRequest.lookup();
            if (webRequest != null) {
                applicationContext = webRequest.getApplicationContext();
            }
        }
        return applicationContext;
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext =applicationContext;
    }
}
