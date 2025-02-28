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
import grails.persistence.Entity;
import grails.persistence.PersistenceMethod;
import grails.web.controllers.ControllerMethod;
import groovy.lang.GroovyObject;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import org.grails.core.util.IncludeExcludeSupport;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.IncludeExcludePropertyMarshaller;
import org.springframework.beans.BeanUtils;

/**
 * @author Siegfried Puchbauer
 * @since 1.1
 */
public class GroovyBeanMarshaller extends IncludeExcludePropertyMarshaller<XML> {

    public boolean supports(Object object) {
        return object instanceof GroovyObject;
    }

    public void marshalObject(Object o, XML xml) throws ConverterException {
        try {
            Class<? extends Object> clazz = o.getClass();
            List<String> excludes = xml.getExcludes(clazz);
            List<String> includes = xml.getIncludes(clazz);
            IncludeExcludeSupport<String> includeExcludeSupport = new IncludeExcludeSupport<String>();

            boolean isEntity = o.getClass().getAnnotation(Entity.class)!=null;
            for (PropertyDescriptor property : BeanUtils.getPropertyDescriptors(o.getClass())) {
                String name = property.getName();

                if(!shouldInclude(includeExcludeSupport,includes,excludes,o, name)) continue;

                if(isEntity && (name.equals(GormProperties.ATTACHED) || name.equals(GormProperties.ERRORS))) continue;
                Method readMethod = property.getReadMethod();
                if (readMethod != null && !(name.equals("metaClass"))&& !(name.equals("class"))) {
                    if(readMethod.getAnnotation(PersistenceMethod.class) != null) continue;
                    if(readMethod.getAnnotation(ControllerMethod.class) != null) continue;
                    Object value = readMethod.invoke(o, (Object[]) null);
                    xml.startNode(name);
                    xml.convertAnother(value);
                    xml.end();
                }
            }
            for (Field field : o.getClass().getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isPublic(modifiers) && !(Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers))) {
                    String name = field.getName();
                    if(!shouldInclude(includeExcludeSupport,includes,excludes,o, name)) continue;
                    if(isEntity && (name.equals(GormProperties.ATTACHED) || name.equals(GormProperties.ERRORS))) continue;
                    xml.startNode(name);
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

    private boolean shouldInclude(IncludeExcludeSupport<String> includeExcludeSupport, List<String> includes, List<String> excludes, Object o, String name) {
        return includeExcludeSupport.shouldInclude(includes,excludes, name) && shouldInclude(o,name);
    }
}
