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
package org.grails.web.converters.marshaller.xml;

import grails.converters.XML;
import org.apache.commons.lang3.time.FastDateFormat;
import org.grails.web.converters.ConverterUtil;
import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.ObjectMarshaller;

import java.text.Format;
import java.util.Date;

/**
 * @author Siegfried Puchbauer
 * @since 1.1
 */
public class DateMarshaller implements ObjectMarshaller<XML> {

    private final Format formatter;

    /**
     * Constructor with a custom formatter.
     *
     * @param formatter the formatter
     */
    public DateMarshaller(Format formatter) {
        this.formatter = formatter;
    }

    /**
     * Default constructor.
     */
    public DateMarshaller() {
        this(FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.S z"));
    }

    public boolean supports(Object object) {
        return object instanceof Date;
    }

    public void marshalObject(Object object, XML xml) throws ConverterException {
        try {
            xml.chars(formatter.format(object));
        } catch (Exception e) {
            throw ConverterUtil.resolveConverterException(e);
        }
    }
}
