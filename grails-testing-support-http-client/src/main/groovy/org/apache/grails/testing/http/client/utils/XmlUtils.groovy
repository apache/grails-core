/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * 'License'); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.testing.http.client.utils

import javax.xml.XMLConstants
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParser

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.CompileStatic
import groovy.transform.Immutable
import groovy.transform.NamedDelegate
import groovy.transform.NamedVariant
import groovy.xml.FactorySupport
import groovy.xml.MarkupBuilder
import groovy.xml.XmlSlurper

import org.xml.sax.SAXException

/**
 * Utility methods for handling XML.
 *
 * @since 7.0.10
 */
@CompileStatic
class XmlUtils {

    private static final String DISALLOW_DOCTYPE_DECL = 'https://apache.org/xml/features/disallow-doctype-decl'
    private static final String EXTERNAL_GENERAL_ENTITIES = 'https://xml.org/sax/features/external-general-entities'
    private static final String EXTERNAL_PARAMETER_ENTITIES = 'https://xml.org/sax/features/external-parameter-entities'
    private static final String FEATURE_SECURE_PROCESSING = XMLConstants.FEATURE_SECURE_PROCESSING
    private static final String LOAD_DTD_GRAMMAR = 'https://apache.org/xml/features/nonvalidating/load-dtd-grammar'
    private static final String LOAD_EXTERNAL_DTD = 'https://apache.org/xml/features/nonvalidating/load-external-dtd'

    private static final Pattern SPACE_AND_EMPTY_ELEMENT_CLOSE = ~/ \/>/
    private static final String EMPTY_ELEMENT_CLOSE = '/>'

    private static final Pattern LINE_ENDINGS = ~/\r\n|[\r\n]/
    private static final Pattern XML_DECLARATION = ~/^\s*(<\?xml\b.*?\?>)/

    private static final Map<String, Boolean> SECURE_XML_SLURPER_FEATURES = [
            (DISALLOW_DOCTYPE_DECL): false,
            (EXTERNAL_GENERAL_ENTITIES): false,
            (EXTERNAL_PARAMETER_ENTITIES): false,
            (FEATURE_SECURE_PROCESSING): true,
            (LOAD_DTD_GRAMMAR): false,
            (LOAD_EXTERNAL_DTD): false
    ].asImmutable()

    /**
     * Renders XML from the given {@link groovy.xml.MarkupBuilder} DSL closure
     * using the optionally provided rendering options.
     *
     * @param format optional formatting options
     * @param dsl the closure that produces the XML markup
     * @return the rendered XML string
     */
    @NamedVariant
    static String toXml(
            @NamedDelegate Format format = null,
            @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MarkupBuilder) Closure<?> dsl
    ) {
        def w = new StringWriter()
        def f = format ?: new Format()
        def c = (Closure) dsl.clone()
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.delegate = f.newMarkupBuilder(w)
        c.call()

        def xml = w.toString()

        def declarationMatcher = XML_DECLARATION.matcher(xml)
        def dslXmlDeclaration = declarationMatcher.find() ? declarationMatcher.group(1) : null
        if (dslXmlDeclaration) {
            xml = xml.substring(declarationMatcher.end())
        }

        if (!f.expandEmptyElements && !f.spaceInEmptyElements) {
            xml = xml.replaceAll(SPACE_AND_EMPTY_ELEMENT_CLOSE, EMPTY_ELEMENT_CLOSE)
        }
        if (f.prettyPrint && f.lineSeparator != System.lineSeparator()) {
            xml = xml.replaceAll(LINE_ENDINGS, Matcher.quoteReplacement(f.lineSeparator))
        }

        def lineSeparator = f.prettyPrint ? f.lineSeparator : ''
        def xmlPrefix = new StringBuilder('')
        def xmlDeclaration = dslXmlDeclaration ?: (!f.omitDeclaration ? f.xmlDeclaration : null)
        if (xmlDeclaration) {
            xmlPrefix.append(xmlDeclaration).append(lineSeparator)
        }
        if (f.doctype) {
            xmlPrefix.append(f.doctype).append(lineSeparator)
        }

        xmlPrefix.append(xml).toString()
    }

    /**
     * Creates an {@link XmlSlurper} with secure defaults.
     * <p>
     * The default parser is namespace aware, non-validating, permits inline DOCTYPE declarations,
     * and disables external entity expansion plus external DTD loading.
     *
     * @param slurperConfig optional XML parser configuration or custom factory
     * @return configured {@link XmlSlurper}
     */
    @NamedVariant
    static XmlSlurper newXmlSlurper(@NamedDelegate SlurperConfig slurperConfig = null) {
        (slurperConfig ?: new SlurperConfig()).newSlurper()
    }

    /**
     * Rendering options for XML generated by {@link #toXml(Format, Closure)}.
     */
    @CompileStatic
    @Immutable(knownImmutableClasses = [Charset])
    static class Format {

        Charset charset = StandardCharsets.UTF_8

        boolean doubleQuotes = true
        boolean escapeAttributes = true
        boolean expandEmptyElements = false
        boolean omitDeclaration = true
        boolean omitEmptyAttributes = false
        boolean omitNullAttributes = false
        boolean prettyPrint = false
        boolean spaceInEmptyElements = true

        String doctype
        String indent = ' '
        String lineSeparator = System.lineSeparator()
        String xmlVersion = '1.0'

        MarkupBuilder applyFormat(MarkupBuilder markupBuilder) {
            markupBuilder.tap {
                it.doubleQuotes = this.doubleQuotes
                it.expandEmptyElements = this.expandEmptyElements
                it.omitEmptyAttributes = this.omitEmptyAttributes
                it.omitNullAttributes = this.omitNullAttributes
                it.escapeAttributes = this.escapeAttributes
            }
        }

        private IndentPrinter newIndentPrinter(Writer writer) {
            new IndentPrinter(
                    writer,
                    prettyPrint ? indent : '',
                    prettyPrint,
                    prettyPrint
            )
        }

        MarkupBuilder newMarkupBuilder(Writer writer) {
            applyFormat(new MarkupBuilder(newIndentPrinter(writer)))
        }

        String getXmlDeclaration() {
            def quote = doubleQuotes ? '"' : "'"
            new StringBuilder('<?xml version=')
                    .append(quote)
                    .append(xmlVersion)
                    .append(quote)
                    .append(' encoding=')
                    .append(quote)
                    .append(charset.name())
                    .append(quote)
                    .append('?>')
                    .toString()
        }
    }

    /**
     * XML parser configuration used by {@link #newXmlSlurper(SlurperConfig)} and
     * {@link org.apache.grails.testing.http.client.TestHttpResponse#withXmlSlurper(XmlUtils.SlurperConfig)}.
     * <p>
     * By default this uses a secure {@link XmlSlurper}. Provide {@link #factory} when tests need a fully
     * custom parser instance.
     */
    @CompileStatic
    static class SlurperConfig {

        Closure<XmlSlurper> factory

        XmlSlurper newSlurper() {
            if (factory) {
                def xmlSlurper = factory.call()
                if (!xmlSlurper) {
                    throw new IllegalArgumentException('factory must not return null')
                }
                return xmlSlurper
            }

            try {
                return new XmlSlurper(newSecureSaxParser())
            }
            catch (ParserConfigurationException | SAXException e) {
                throw new IllegalStateException('Unable to create secure XmlSlurper', e)
            }
        }
    }

    private static SAXParser newSecureSaxParser() throws ParserConfigurationException, SAXException {
        def saxParserFactory = FactorySupport.createSaxParserFactory().tap {
            it.namespaceAware = true
            it.validating = false
        }

        SECURE_XML_SLURPER_FEATURES.each { feature, enabled ->
            try {
                saxParserFactory.setFeature(feature, enabled)
            }
            catch (Exception ignored) {
                // ignore, parser doesn't support
            }
        }

        saxParserFactory.newSAXParser()
    }

}
