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
package org.grails.web.converters.marshaller.xml;

import grails.converters.XML;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MetaMethod;

import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.ObjectMarshaller;

/**
 * @author Siegfried Puchbauer
 * @since 1.1
 */
public class InstanceMethodBasedMarshaller implements ObjectMarshaller<XML> {

    public boolean supports(Object object) {
        return getToXMLMethod(object) != null;
    }

    public void marshalObject(Object object, XML converter) throws ConverterException {
        MetaMethod method = getToXMLMethod(object);
        try {
            Object result = method.invoke(object, new Object[]{ converter });
            if (result != null && !(result instanceof XML)) {
                converter.convertAnother(result);
            }
        }
        catch(Throwable e) {
            throw e instanceof ConverterException ? (ConverterException)e :
                new ConverterException("Error invoking toXML method of object with class " + object.getClass().getName(),e);
        }
    }

    protected MetaMethod getToXMLMethod(Object object) {
        MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(object.getClass());
        if (mc != null) {
            return mc.getMetaMethod("toXML", new Object[] { XML.class });
        }
        return null;
    }
}
