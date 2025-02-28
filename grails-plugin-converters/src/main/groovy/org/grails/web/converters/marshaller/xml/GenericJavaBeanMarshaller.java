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

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.ObjectMarshaller;
import org.springframework.beans.BeanUtils;

/**
 * @author Siegfried Puchbauer
 * @since 1.1
 */
public class GenericJavaBeanMarshaller implements ObjectMarshaller<XML> {

    public boolean supports(Object object) {
        return true;
    }

    public void marshalObject(Object o, XML xml) throws ConverterException {
        try {
            for (PropertyDescriptor property : BeanUtils.getPropertyDescriptors(o.getClass())) {
                String name = property.getName();
                Method readMethod = property.getReadMethod();
                if (readMethod != null) {
                    Object value = readMethod.invoke(o, (Object[]) null);
                    xml.startNode(name);
                    xml.convertAnother(value);
                    xml.end();
                }
            }
            for (Field field : o.getClass().getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (field.isAccessible() && Modifier.isPublic(modifiers) &&
                        !(Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers))) {
                    xml.startNode(field.getName());
                    xml.convertAnother(field.get(o));
                    xml.end();
                }
            }
        }
        catch (ConverterException ce) {
            throw ce;
        }
        catch (Exception e) {
            throw new ConverterException("Error converting Bean with class " + o.getClass().getName(), e);
        }
    }
}
