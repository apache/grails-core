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

import java.util.Map;

import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.NameAwareMarshaller;
import org.grails.web.converters.marshaller.ObjectMarshaller;

/**
 * @author Siegfried Puchbauer
 * @since 1.1
 */
public class MapMarshaller implements ObjectMarshaller<XML>, NameAwareMarshaller {

    public boolean supports(Object object) {
        return object instanceof Map;
    }

    @SuppressWarnings("unchecked")
    public void marshalObject(Object o, XML xml) throws ConverterException {

        Map<Object,Object> map = (Map<Object,Object>) o;
        for (Map.Entry<Object,Object> entry : map.entrySet()) {
            xml.startNode("entry").attribute("key", entry.getKey().toString());
            xml.convertAnother(entry.getValue());
            xml.end();
        }
    }

    public String getElementName(Object o) {
        return "map";
    }
}
