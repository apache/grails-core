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

import java.text.Format;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import grails.converters.XML;
import org.grails.web.converters.ConverterUtil;
import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.ObjectMarshaller;

/**
 * XML ObjectMarshaller which converts a Date Object to an ISO 8601 offset
 * date-time string in the system default zone (e.g. {@code 2024-06-15T14:30:45.123-04:00}).
 *
 * @author Siegfried Puchbauer
 * @since 1.1
 */
public class DateMarshaller implements ObjectMarshaller<XML> {

    private static final DateTimeFormatter DEFAULT_FORMATTER =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    private final Format legacyFormatter;

    /**
     * Constructor with a custom formatter.
     * @param formatter  the formatter
     */
    public DateMarshaller(Format formatter) {
        this.legacyFormatter = formatter;
    }

    /**
     * Default constructor — uses {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME}
     * with the system default zone.
     */
    public DateMarshaller() {
        this(null);
    }

    public boolean supports(Object object) {
        return object instanceof Date;
    }

    public void marshalObject(Object object, XML xml) throws ConverterException {
        try {
            Date date = (Date) object;
            String formatted = legacyFormatter != null
                    ? legacyFormatter.format(date)
                    : DEFAULT_FORMATTER.format(date.toInstant());
            xml.chars(formatted);
        }
        catch (Exception e) {
            throw ConverterUtil.resolveConverterException(e);
        }
    }
}
