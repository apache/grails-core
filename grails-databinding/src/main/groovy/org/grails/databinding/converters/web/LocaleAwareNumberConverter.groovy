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
package org.grails.databinding.converters.web

import grails.databinding.converters.ValueConverter;
import groovy.transform.CompileStatic

import java.text.NumberFormat
import java.text.ParsePosition

import jakarta.servlet.http.HttpServletRequest

import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.servlet.LocaleResolver


/**
 * A ValueConverter that knows how to convert a String to any numeric type and is Locale aware.  The
 * converter will use the Locale of the current request if being invoked as part of a
 * request, otherwise will use Locale.getDefault()
 * 
 * @author Jeff Brown
 * @since 2.3
 *
 */
@CompileStatic
class LocaleAwareNumberConverter implements ValueConverter {

    Class<?> targetType
    
    @Autowired(required=false)
    LocaleResolver localeResolver

    @Override
    public boolean canConvert(Object value) {
        value instanceof String
    }

    @Override
    public Object convert(Object value) {
        def trimmedValue = value.toString().trim()
        def parsePosition = new ParsePosition(0)
        def result = numberFormatter.parse((String)value, parsePosition).asType(getTargetType())
        if(parsePosition.index != trimmedValue.size()) {
            throw new NumberFormatException("Unable to parse number [${value}]")
        }
        result
    }

    protected NumberFormat getNumberFormatter() {
        NumberFormat.getInstance(getLocale())
    }

    protected Locale getLocale() {
        def locale
        def request = GrailsWebRequest.lookup()?.currentRequest
        if(request instanceof HttpServletRequest) {
            locale = localeResolver?.resolveLocale(request)
        }
        if(locale == null) {
            locale = Locale.default
        }
        return locale
    }
}
