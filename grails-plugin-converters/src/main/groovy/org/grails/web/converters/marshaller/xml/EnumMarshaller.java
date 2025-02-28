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

import java.lang.reflect.Method;

import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.ObjectMarshaller;
import org.springframework.beans.BeanUtils;

/**
 * @author Siegfried Puchbauer
 * @since 1.1
 */
public class EnumMarshaller implements ObjectMarshaller<XML> {

    public boolean supports(Object object) {
        return object.getClass().isEnum();
    }

    public void marshalObject(Object en, XML xml) throws ConverterException {
        try {
            Class<?> enumClass = en.getClass();
            xml.attribute("enumType", enumClass.getName());
            Method nameMethod = BeanUtils.findDeclaredMethod(enumClass, "name");
            try {
                xml.chars(nameMethod.invoke(en).toString());
            }
            catch (Exception e) {
                // ignored
            }
        }
        catch (ConverterException ce) {
            throw ce;
        }
        catch (Exception e) {
            throw new ConverterException("Error converting Enum with class " + en.getClass().getName(), e);
        }
    }
}
