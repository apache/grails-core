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

import java.util.Collection;
import java.util.Set;

import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.NameAwareMarshaller;
import org.grails.web.converters.marshaller.ObjectMarshaller;

/**
 * @author Siegfried Puchbauer
 * @since 1.1
 */
@SuppressWarnings("rawtypes")
public class CollectionMarshaller implements ObjectMarshaller<XML>, NameAwareMarshaller {

    public boolean supports(Object object) {
        return object instanceof Collection;
    }

    public void marshalObject(Object object, XML xml) throws ConverterException {
        Collection col = (Collection) object;
        for (Object o : col) {
            if (o != null) {
                xml.startNode(xml.getElementName(o));
                xml.convertAnother(o);
                xml.end();
            }
            else {
                xml.startNode("null");
                xml.end();
            }
        }
    }

    public String getElementName(Object o) {
        return o instanceof Set ? "set" : "list";
    }
}
