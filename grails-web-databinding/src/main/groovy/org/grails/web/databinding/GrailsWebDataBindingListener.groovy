/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.web.databinding

import grails.databinding.errors.BindingError;
import grails.databinding.events.DataBindingListenerAdapter;
import grails.util.GrailsNameUtils
import groovy.transform.CompileStatic

import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.validation.BindingResult
import org.springframework.validation.FieldError

@CompileStatic
class GrailsWebDataBindingListener extends DataBindingListenerAdapter {
    private final MessageSource messageSource

    GrailsWebDataBindingListener(MessageSource messageSource) {
        this.messageSource = messageSource
    }

    @Override
    void bindingError(BindingError error, errors) {
        BindingResult bindingResult = (BindingResult)errors
        String className = error.object?.getClass()?.getName()
        String classAsPropertyName = GrailsNameUtils.getPropertyNameRepresentation(className)
        String propertyName = error.getPropertyName()
        String[] codes = [
            className + '.' + propertyName + '.typeMismatch.error',
            className + '.' + propertyName + '.typeMismatch',
            classAsPropertyName + '.' + propertyName + '.typeMismatch.error',
            classAsPropertyName + '.' + propertyName + '.typeMismatch',
            bindingResult.resolveMessageCodes('typeMismatch', propertyName),
        ].flatten() as String[]
        Object[] args = [getPropertyName(className, classAsPropertyName, propertyName)] as Object[]
        def defaultMessage = error.cause?.message ?: 'Data Binding Failed'
        def fieldError = new FieldError(className, propertyName, error.getRejectedValue(), true, codes, args, defaultMessage)
        bindingResult.addError(fieldError)
    }

    protected String getPropertyName(String className, String classAsPropertyName, String propertyName) {
        if (!messageSource) return propertyName

        final Locale locale = LocaleContextHolder.getLocale()
        String propertyNameCode = className + '.' + propertyName + ".label"
        String resolvedPropertyName = messageSource.getMessage(propertyNameCode, null, propertyName, locale)
        if (resolvedPropertyName.equals(propertyName)) {
            propertyNameCode = classAsPropertyName + '.' + propertyName + ".label"
            resolvedPropertyName = messageSource.getMessage(propertyNameCode, null, propertyName, locale)
        }
        return resolvedPropertyName
    }
}
