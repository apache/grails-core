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
import groovy.lang.Writable;

import java.io.IOException;

import org.codehaus.groovy.runtime.EncodingGroovyMethods;
import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.ObjectMarshaller;

/**
 * @author Siegfried Puchbauer
 * @since 1.1
 */
public class Base64ByteArrayMarshaller implements ObjectMarshaller<XML> {

    public boolean supports(Object object) {
        return object instanceof byte[] || object instanceof Byte[];
    }

    public void marshalObject(Object object, XML xml) throws ConverterException {
        xml.attribute("encoding", "BASE-64");
        xml.chars("");

        Writable w;
        if (object instanceof byte[]) {
            w = EncodingGroovyMethods.encodeBase64((byte[])object);
        }
        else {
            w = EncodingGroovyMethods.encodeBase64((Byte[])object);
        }

        try {
            w.writeTo(xml.getStream());
        }
        catch (IOException e) {
            throw new ConverterException(e);
        }
    }
}
