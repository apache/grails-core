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

import java.util.Locale;

import org.grails.web.json.JSONWriter;
import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.ObjectMarshaller;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

/**
 * @author Siegfried Puchbauer
 * @since 1.1
 */
public class ValidationErrorsMarshaller implements ObjectMarshaller<JSON>, ApplicationContextAware {

    private ApplicationContext applicationContext;

    public boolean supports(Object object) {
        return object instanceof Errors;
    }

    public void marshalObject(Object object, JSON json) throws ConverterException {
        Errors errors = (Errors) object;
        JSONWriter writer = json.getWriter();

        try {
            writer.object();
            writer.key("errors");
            writer.array();

            for (Object o : errors.getAllErrors()) {
                if (o instanceof FieldError) {
                    FieldError fe = (FieldError) o;
                    writer.object();
                    json.property("object", fe.getObjectName());
                    json.property("field", fe.getField());
                    json.property("rejected-value", fe.getRejectedValue());
                    Locale locale = LocaleContextHolder.getLocale();
                    if (applicationContext != null) {
                        json.property("message", applicationContext.getMessage(fe, locale));
                    }
                    else {
                        json.property("message", fe.getDefaultMessage());
                    }
                    writer.endObject();
                } else if (o instanceof ObjectError) {
                    ObjectError fe = (ObjectError) o;
                    writer.object();
                    json.property("object", fe.getObjectName());
                    Locale locale = LocaleContextHolder.getLocale();
                    if (applicationContext != null) {
                        json.property("message", applicationContext.getMessage(fe, locale));
                    }
                    else {
                        json.property("message", fe.getDefaultMessage());
                    }
                    writer.endObject();
                }
            }
            writer.endArray();
            writer.endObject();
        }
        catch (ConverterException ce) {
            throw ce;
        }
        catch (Exception e) {
            throw new ConverterException("Error converting Bean with class " + object.getClass().getName(), e);
        }
    }

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
}
