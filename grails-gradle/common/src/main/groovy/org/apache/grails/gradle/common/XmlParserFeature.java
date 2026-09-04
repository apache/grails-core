/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.grails.gradle.common;

/**
 * Registered SAX and Xerces parser feature identifiers used to harden XML parsing.
 *
 * <p><strong>These values are opaque identifiers, not addresses.</strong> Nothing is ever fetched
 * from them. A parser matches them by exact string comparison against the prefixes it registers
 * internally, {@code http://xml.org/sax/features/} and {@code http://apache.org/xml/features/}.
 *
 * <p><strong>Do not rewrite the {@code http} scheme to {@code https}.</strong> No parser
 * recognizes the {@code https} spelling; {@code setFeature} answers it with
 * {@code SAXNotRecognizedException}. Because callers wrap {@code setFeature} in a catch that
 * tolerates parsers lacking a feature, an unrecognised name is swallowed and the hardening is
 * silently disabled rather than failing loudly. A blanket "prefer https" sweep over the codebase
 * therefore turns XML hardening off without leaving a trace, which is exactly what happened
 * before these values were collected here.
 *
 * <p>Consumers must assert parser <em>behaviour</em> rather than reading these names back, so that
 * the guarding tests hold no copy of the identifiers and cannot be rewritten by the same sweep.
 *
 * @since 8.0.0
 */
public enum XmlParserFeature {

    /**
     * Rejects any document carrying a {@code DOCTYPE} declaration.
     *
     * <p>Enabling this is stricter than blocking external entities: it refuses documents whose
     * DOCTYPE is entirely internal and harmless. Descriptors read from the classpath — JSP tag
     * library definitions, {@code web.xml}, {@code plugin.xml} — routinely carry a DOCTYPE, so a
     * parser shared with those callers must leave this disabled.
     */
    DISALLOW_DOCTYPE_DECL("http://apache.org/xml/features/disallow-doctype-decl"),

    /**
     * Blocks resolution of external general entities, the primary XXE vector.
     */
    EXTERNAL_GENERAL_ENTITIES("http://xml.org/sax/features/external-general-entities"),

    /**
     * Blocks resolution of external parameter entities.
     */
    EXTERNAL_PARAMETER_ENTITIES("http://xml.org/sax/features/external-parameter-entities"),

    /**
     * Stops the parser building a grammar from a DTD.
     */
    LOAD_DTD_GRAMMAR("http://apache.org/xml/features/nonvalidating/load-dtd-grammar"),

    /**
     * Skips external DTD subsets instead of retrieving them.
     *
     * <p>This differs from the JAXP {@code XMLConstants.ACCESS_EXTERNAL_DTD} property, which
     * raises an error when a document references an external DTD. Skipping is what allows a
     * descriptor that names a DTD, such as a JSP 1.2 tag library, to parse without retrieving it.
     */
    LOAD_EXTERNAL_DTD("http://apache.org/xml/features/nonvalidating/load-external-dtd");

    private final String featureName;

    XmlParserFeature(String featureName) {
        this.featureName = featureName;
    }

    /**
     * @return the registered identifier to pass to {@code setFeature}
     */
    public String getFeatureName() {
        return featureName;
    }

    @Override
    public String toString() {
        return featureName;
    }

}
