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

import java.util.Locale;

import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.NameAwareMarshaller;
import org.grails.web.converters.marshaller.ObjectMarshaller;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;

/**
 * @author Siegfried Puchbauer
 * @since 1.1
 */
public class ValidationErrorsMarshaller implements ObjectMarshaller<XML>, NameAwareMarshaller, ApplicationContextAware {

    private ApplicationContext applicationContext;

    public boolean supports(Object object) {
        return object instanceof Errors;
    }

    public void marshalObject(Object object, XML xml) throws ConverterException {
        Errors errors = (Errors) object;

        try {
            for (Object o : errors.getAllErrors()) {
                if (o instanceof FieldError) {
                    FieldError fe = (FieldError) o;
                    xml.startNode("error");
                    xml.attribute("object", fe.getObjectName());
                    xml.attribute("field", fe.getField());
                    xml.startNode("rejected-value").convertAnother(fe.getRejectedValue());
                    xml.end();
                    Locale locale = LocaleContextHolder.getLocale();
                    if (applicationContext != null) {
                        xml.startNode("message").chars(applicationContext.getMessage(fe, locale)).end();
                    }
                    else {
                        String defaultMessage = fe.getDefaultMessage();
                        if(defaultMessage != null)
                            xml.startNode("message").chars(defaultMessage).end();
                    }
                    xml.end();
                }
            }
        }
        catch (ConverterException ce) {
            throw ce;
        }
        catch (Exception e) {
            throw new ConverterException("Error converting Bean with class " + object.getClass().getName(), e);
        }
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public String getElementName(Object o) {
        return "errors";
    }
}
