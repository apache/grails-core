/*
 * Copyright 2004-2005 the original author or authors.
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
package grails.core.gsp;

import grails.core.InjectableGrailsClass;

import java.util.Set;

/**
 * Represents a Grails tab library class.
 *
 * @author Graeme Rocher
 */
public interface GrailsTagLibClass extends InjectableGrailsClass {

    String DEFAULT_NAMESPACE = "g";

    String NAMESPACE_FIELD_NAME = "namespace";

    String RETURN_OBJECT_FOR_TAGS_FIELD_NAME = "returnObjectForTags";
    String ENCODE_AS_FOR_TAGS_FIELD_NAME = "encodeAsForTags";
    String DEFAULT_ENCODE_AS_FIELD_NAME = "defaultEncodeAs";

    /**
     * @param tagName The name of the tag
     * @return Whether the tag library contains the specified tag
     */
    boolean hasTag(String tagName);

    /**
     * @return The tag names in this library
     */
    Set<String> getTagNames();

    Set<String> getTagNamesThatReturnObject();

    /**
     * @return the namespace that this taglib occupies.
     */
    String getNamespace();

    Object getEncodeAsForTag(String tagName);
    Object getDefaultEncodeAs();
}
