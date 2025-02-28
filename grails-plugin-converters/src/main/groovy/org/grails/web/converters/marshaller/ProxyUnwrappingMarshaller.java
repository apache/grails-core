/*
 * Copyright 2004-2008 the original author or authors.
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
package org.grails.web.converters.marshaller;

import grails.util.GrailsNameUtils;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassRegistry;

import org.grails.web.converters.Converter;
import org.grails.web.converters.exceptions.ConverterException;

/**
 * Unwraps Hibernate proxies with no direct references to the Hibernate APIs
 *
 * @author Siegfried Puchbauer
 * @author Graeme Rocher
 *
 * @since 1.1
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class ProxyUnwrappingMarshaller<C extends Converter> implements ObjectMarshaller<C>, NameAwareMarshaller {

    private static final String HIBERNATE_LAZY_INITIALIZER_PROP = "hibernateLazyInitializer";
    private static final String IMPLEMENTATION_PROP = "implementation";

    public boolean supports(Object object) {
        if (object == null) return false;
        MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(object.getClass());
        return mc.hasProperty(object, HIBERNATE_LAZY_INITIALIZER_PROP) != null;
    }

    public void marshalObject(Object object, C converter) throws ConverterException {
        Object unwrapped = unwrap(object);
        converter.lookupObjectMarshaller(unwrapped).marshalObject(unwrapped, converter);
    }

    private Object unwrap(Object o) {
        if (o == null) return o;

        final MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
        MetaClass mc = registry.getMetaClass(o.getClass());

        final Object hibernateLazyInitializer = mc.getProperty(o, HIBERNATE_LAZY_INITIALIZER_PROP);
        return registry.getMetaClass(hibernateLazyInitializer.getClass()).getProperty(
                hibernateLazyInitializer, IMPLEMENTATION_PROP);
    }

    public String getElementName(Object o) {
        return GrailsNameUtils.getPropertyName(unwrap(o).getClass().getName());
    }
}
