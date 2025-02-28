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
package org.grails.web.converters.marshaller.json;

import grails.converters.JSON;
import grails.persistence.PersistenceMethod;
import grails.web.controllers.ControllerMethod;
import groovy.lang.GroovyObject;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import org.grails.core.util.IncludeExcludeSupport;
import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.IncludeExcludePropertyMarshaller;
import org.grails.web.json.JSONWriter;
import org.springframework.beans.BeanUtils;

/**
 * @author Siegfried Puchbauer
 * @since 1.1
 */
public class GroovyBeanMarshaller extends IncludeExcludePropertyMarshaller<JSON> {

    public boolean supports(Object object) {
        return object instanceof GroovyObject;
    }

    public void marshalObject(Object o, JSON json) throws ConverterException {
        JSONWriter writer = json.getWriter();


        Class<? extends Object> clazz = o.getClass();
        List<String> excludes = json.getExcludes(clazz);
        List<String> includes = json.getIncludes(clazz);
        IncludeExcludeSupport<String> includeExcludeSupport = new IncludeExcludeSupport<String>();
        try {
            writer.object();
            for (PropertyDescriptor property : BeanUtils.getPropertyDescriptors(clazz)) {

                Method readMethod = property.getReadMethod();
                String name = property.getName();

                if(!shouldInclude(includeExcludeSupport, includes, excludes, o, name)) continue;

                if (readMethod != null && !(name.equals("metaClass"))&& !(name.equals("class"))) {
                    if(readMethod.getAnnotation(PersistenceMethod.class) != null) continue;
                    if(readMethod.getAnnotation(ControllerMethod.class) != null) continue;
                    Object value = readMethod.invoke(o, (Object[]) null);
                    writer.key(name);
                    json.convertAnother(value);
                }
            }
            for (Field field : clazz.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isPublic(modifiers) && !(Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers))) {
                    String name = field.getName();
                    if(!shouldInclude(includeExcludeSupport,includes,excludes,o,name)) continue;
                    writer.key(name);
                    json.convertAnother(field.get(o));
                }
            }
            writer.endObject();
        }
        catch (ConverterException ce) {
            throw ce;
        }
        catch (Exception e) {
            throw new ConverterException("Error converting Bean with class " + clazz.getName(), e);
        }
    }

    private boolean shouldInclude(IncludeExcludeSupport<String> includeExcludeSupport, List<String> includes, List<String> excludes, Object o, String name) {
        return includeExcludeSupport.shouldInclude(includes,excludes, name) && shouldInclude(o,name);
    }

}
