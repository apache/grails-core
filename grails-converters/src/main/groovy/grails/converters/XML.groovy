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
package grails.converters

import groovy.transform.CompileStatic

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import org.springframework.util.Assert

import grails.core.support.proxy.EntityProxyHandler
import grails.core.support.proxy.ProxyHandler
import grails.util.GrailsNameUtils
import grails.util.GrailsWebUtil
import grails.web.mime.MimeType
import org.grails.buffer.FastStringWriter
import org.grails.web.converters.AbstractConverter
import org.grails.web.converters.Converter
import org.grails.web.converters.Converter.CircularReferenceBehaviour
import org.grails.web.converters.ConverterUtil
import org.grails.web.converters.IncludeExcludeConverter
import org.grails.web.converters.configuration.ConverterConfiguration
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.DefaultConverterConfiguration
import org.grails.web.converters.exceptions.ConverterException
import org.grails.web.converters.marshaller.ClosureObjectMarshaller
import org.grails.web.converters.marshaller.NameAwareMarshaller
import org.grails.web.converters.marshaller.ObjectMarshaller
import org.grails.web.xml.PrettyPrintXMLStreamWriter
import org.grails.web.xml.StreamingMarkupWriter
import org.grails.web.xml.XMLStreamWriter

import static org.grails.io.support.SpringIOUtils.createXmlSlurper

/**
 * A converter that converts domain classes to XML.
 *
 * @author Siegfried Puchbauer
 * @author Graeme Rocher
 */
@CompileStatic
class XML extends AbstractConverter<XMLStreamWriter> implements IncludeExcludeConverter<XMLStreamWriter> {

    public static final Log log = LogFactory.getLog(XML)

    private static final String CACHED_XML = 'org.codehaus.groovy.grails.CACHED_XML_REQUEST_CONTENT'

    private Object target
    private StreamingMarkupWriter stream
    private final ConverterConfiguration<XML> config
    private final String encoding
    private final CircularReferenceBehaviour circularReferenceBehaviour
    private XMLStreamWriter writer
    private final Stack<Object> referenceStack = new Stack<>()
    private boolean isRendering = false

    XML() {
        config = ConvertersConfigurationHolder.getConverterConfiguration(XML)
        encoding = config.getEncoding() != null ? config.getEncoding() : 'UTF-8'
        contentType = MimeType.XML.getName()
        circularReferenceBehaviour = config.getCircularReferenceBehaviour()
    }

    XML(Object target) {
        this()
        this.target = target
    }

    XML(XMLStreamWriter writer) {
        this()
        this.writer = writer
        this.isRendering = true
    }

    protected ConverterConfiguration<XML> initConfig() {
        return ConvertersConfigurationHolder.getConverterConfiguration(XML)
    }

    @Override
    void setTarget(Object target) {
        this.target = target
    }

    private void finalizeRender(Writer out) {
        try {
            if (out != null) {
                out.flush()
                out.close()
            }
        }
        catch (Exception e) {
            log.warn('Unexpected exception while closing a writer: ' + e.getMessage())
        }
    }

    void render(Writer out) throws ConverterException {
        stream = new StreamingMarkupWriter(out, encoding)
        writer = config.isPrettyPrint() ? new PrettyPrintXMLStreamWriter(stream) : new XMLStreamWriter(stream)

        try {
            isRendering = true
            writer.startDocument(encoding, '1.0')
            writer.startNode(getElementName(target))
            convertAnother(target)
            writer.end()
            finalizeRender(out)
        }
        catch (Exception e) {
            throw new ConverterException(e)
        }
        finally {
            isRendering = false
        }
    }

    private void checkState() {
        Assert.state(isRendering, 'Illegal XML Converter call!')
    }

    String getElementName(Object o) {
        ObjectMarshaller<XML> om = config.getMarshaller(o)
        if (om instanceof NameAwareMarshaller) {
            return ((NameAwareMarshaller) om).getElementName(o)
        }
        final ProxyHandler proxyHandler = config.getProxyHandler()
        if (proxyHandler.isProxy(o) && (proxyHandler instanceof EntityProxyHandler)) {
            EntityProxyHandler entityProxyHandler = (EntityProxyHandler) proxyHandler
            final Class<?> cls = entityProxyHandler.getProxiedClass(o)
            return GrailsNameUtils.getPropertyName(cls)
        }
        return GrailsNameUtils.getPropertyName(o.getClass())
    }

    void convertAnother(Object o) throws ConverterException {
        o = config.getProxyHandler().unwrapIfProxy(o)

        if (o == null) return

        try {
            if (o instanceof CharSequence) {
                writer.characters(o.toString())
            }
            else if (o instanceof Class<?>) {
                writer.characters(((Class<?>) o).getName())
            }
            else if ((o.getClass().isPrimitive() && !o.getClass().equals(byte[].class)) ||
                    o instanceof Number || o instanceof Boolean) {
                writer.characters(String.valueOf(o))
            }
            else {
                if (referenceStack.contains(o)) {
                    handleCircularRelationship(o)
                }
                else {
                    referenceStack.push(o)
                    ObjectMarshaller<XML> marshaller = config.getMarshaller(o)
                    if (marshaller == null) {
                        throw new ConverterException('Inconvertible object of class: ' + o.getClass().getName())
                    }
                    marshaller.marshalObject(o, this)
                    referenceStack.pop()
                }
            }
        }
        catch (Throwable t) {
            throw ConverterUtil.resolveConverterException(t)
        }
    }

    ObjectMarshaller<XML> lookupObjectMarshaller(Object target) {
        return config.getMarshaller(target)
    }

    int getDepth() {
        return referenceStack.size()
    }

    XML startNode(String tagName) {
        checkState()
        try {
            writer.startNode(tagName)
        }
        catch (Exception e) {
            throw ConverterUtil.resolveConverterException(e)
        }
        return this
    }

    XML chars(String chars) {
        checkState()
        try {
            writer.characters(chars)
        }
        catch (Exception e) {
            throw ConverterUtil.resolveConverterException(e)
        }
        return this
    }

    XML attribute(String name, String value) {
        checkState()
        try {
            writer.attribute(name, value)
        }
        catch (Exception e) {
            throw ConverterUtil.resolveConverterException(e)
        }
        return this
    }

    XML end() {
        checkState()
        try {
            writer.end()
        }
        catch (Exception e) {
            throw ConverterUtil.resolveConverterException(e)
        }
        return this
    }

    protected void handleCircularRelationship(Object o) throws ConverterException {
        switch (circularReferenceBehaviour) {
            case CircularReferenceBehaviour.DEFAULT:
                StringBuilder ref = new StringBuilder()
                int idx = referenceStack.indexOf(o)
                ref.append('../'.repeat(Math.max(0, referenceStack.size() - 1 - idx)))
                attribute('ref', ref.substring(0, ref.length() - 1))
                break
            case CircularReferenceBehaviour.EXCEPTION:
                throw new ConverterException('Circular Reference detected: class ' + o.getClass().getName())
            case CircularReferenceBehaviour.INSERT_NULL:
                convertAnother(null)
        }
    }

    void render(HttpServletResponse response) throws ConverterException {
        ConvertersConfigurationHolder.withConverterObservation('xml', { -> renderInternal(response) })
    }

    private void renderInternal(HttpServletResponse response) throws ConverterException {
        response.setContentType(GrailsWebUtil.getContentType(contentType, encoding))
        try {
            render(response.getWriter())
        }
        catch (IOException e) {
            throw new ConverterException(e)
        }
    }

    XMLStreamWriter getWriter() throws ConverterException {
        checkState()
        return writer
    }

    StreamingMarkupWriter getStream() {
        checkState()
        return stream
    }

    @Override
    void build(Closure c) throws ConverterException {
        new Builder(this).execute(c)
    }

    @Override
    String toString() {
        FastStringWriter writer = new FastStringWriter()
        render(writer)
        writer.flush()
        return writer.toString()
    }

    /**
     * Parses the given XML
     *
     * @param source a String containing some XML
     * @return a {@link groovy.xml.slurpersupport.GPathResult}
     * @throws ConverterException if an error occurs parsing the XML
     */
    static Object parse(String source) throws ConverterException {
        try {
            return createXmlSlurper().parseText(source)
        }
        catch (Exception e) {
            throw new ConverterException('Error parsing XML', e)
        }
    }

    /**
     * Parses the given XML
     *
     * @param inputStream an InputStream to read from
     * @param encoding the Character Encoding to use
     * @return a {@link groovy.xml.slurpersupport.GPathResult}
     * @throws ConverterException if an error occurs parsing the XML
     */
    static Object parse(InputStream inputStream, String encoding) throws ConverterException {
        try {
            InputStreamReader reader = new InputStreamReader(inputStream, encoding)
            return createXmlSlurper().parse(reader)
        }
        catch (Exception e) {
            throw new ConverterException('Error parsing XML', e)
        }
    }

    /**
     * Parses the give XML (read from the POST Body of the Request)
     *
     * @param request an HttpServletRequest
     * @return a {@link groovy.xml.slurpersupport.GPathResult}
     * @throws ConverterException if an error occurs parsing the XML
     */
    static Object parse(HttpServletRequest request) throws ConverterException {
        Object xml = request.getAttribute(CACHED_XML)
        if (xml == null) {
            try {
                if (!request.getMethod().equalsIgnoreCase('GET')) {
                    xml = parse(
                            request.getInputStream(),
                            Optional.ofNullable(request.getCharacterEncoding()).orElse(Converter.DEFAULT_REQUEST_ENCODING)
                    )
                    request.setAttribute(CACHED_XML, xml)
                }
            }
            catch (IOException e) {
                throw new ConverterException('Error parsing XML', e)
            }
        }
        return xml
    }

    static ConverterConfiguration<XML> getNamedConfig(String configName) throws ConverterException {
        ConverterConfiguration<XML> cfg = ConvertersConfigurationHolder.getNamedConverterConfiguration(
                configName, XML)
        if (cfg == null) {
            throw new ConverterException(String.format("Converter Configuration with name '%s' not found!", configName))
        }
        return cfg
    }

    static Object use(String configName, Closure<?> callable) throws ConverterException {
        ConverterConfiguration<XML> old = ConvertersConfigurationHolder.getThreadLocalConverterConfiguration(XML)
        ConverterConfiguration<XML> cfg = getNamedConfig(configName)
        ConvertersConfigurationHolder.setThreadLocalConverterConfiguration(XML, cfg)
        try {
            return callable.call()
        }
        finally {
            ConvertersConfigurationHolder.setThreadLocalConverterConfiguration(XML, old)
        }
    }

    static void use(String cfgName) throws ConverterException {
        if (cfgName == null || 'default' == cfgName) {
            ConvertersConfigurationHolder.setThreadLocalConverterConfiguration(XML, null)
        }
        else {
            ConvertersConfigurationHolder.setThreadLocalConverterConfiguration(XML, getNamedConfig(cfgName))
        }
    }

    static void registerObjectMarshaller(Class<?> clazz, Closure<?> callable) throws ConverterException {
        registerObjectMarshaller(new ClosureObjectMarshaller<>(clazz, callable))
    }

    static void registerObjectMarshaller(Class<?> clazz, int priority, Closure<?> callable) throws ConverterException {
        registerObjectMarshaller(new ClosureObjectMarshaller<>(clazz, callable), priority)
    }

    static void registerObjectMarshaller(ObjectMarshaller<XML> om) throws ConverterException {
        ConverterConfiguration<XML> cfg = createConvertersConfiguration()
        ((DefaultConverterConfiguration<XML>) cfg).registerObjectMarshaller(om)
    }

    static void registerObjectMarshaller(ObjectMarshaller<XML> om, int priority) throws ConverterException {
        ConverterConfiguration<XML> cfg = createConvertersConfiguration()
        ((DefaultConverterConfiguration<XML>) cfg).registerObjectMarshaller(om, priority)
    }

    private static ConverterConfiguration<XML> createConvertersConfiguration() {
        ConverterConfiguration<XML> cfg = ConvertersConfigurationHolder.getConverterConfiguration(XML)
        if (cfg == null) {
            throw new ConverterException('Default Configuration not found for class ' + XML.name)
        }
        if (!(cfg instanceof DefaultConverterConfiguration<?>)) {
            cfg = new DefaultConverterConfiguration<>(cfg)
            ConvertersConfigurationHolder.setDefaultConfiguration(XML, cfg)
        }
        return cfg
    }

    static void createNamedConfig(String name, Closure<?> callable) throws ConverterException {
        DefaultConverterConfiguration<XML> cfg = new DefaultConverterConfiguration<>(
                ConvertersConfigurationHolder.getConverterConfiguration(XML)
        )
        try {
            callable.call(cfg)
            ConvertersConfigurationHolder.setNamedConverterConfiguration(XML, name, cfg)
        }
        catch (Exception e) {
            throw ConverterUtil.resolveConverterException(e)
        }
    }

    static void withDefaultConfiguration(Closure<?> callable) throws ConverterException {
        ConverterConfiguration<XML> cfg = ConvertersConfigurationHolder.getConverterConfiguration(XML)
        if (!(cfg instanceof DefaultConverterConfiguration<?>)) {
            cfg = new DefaultConverterConfiguration<>(cfg)
        }
        try {
            callable.call(cfg)
            ConvertersConfigurationHolder.setDefaultConfiguration(XML, cfg)
        }
        catch (Throwable t) {
            throw ConverterUtil.resolveConverterException(t)
        }
    }

    @Override
    void setIncludes(List<String> includes) {
        setIncludes(target.getClass(), includes)
    }

    @Override
    void setExcludes(List<String> excludes) {
        setExcludes(target.getClass(), excludes)
    }

    static class Builder extends BuilderSupport {

        private final XML xml

        Builder(XML xml) {
            this.xml = xml
        }

        void execute(Closure<?> callable) {
            callable.setDelegate(this)
            callable.call()
        }

        @Override
        protected Object createNode(Object name) {
            return createNode(name, null, null)
        }

        @Override
        protected Object createNode(Object name, Object value) {
            return createNode(name, null, value)
        }

        @Override
        protected Object createNode(Object name, Map attributes) {
            return createNode(name, attributes, null)
        }

        @SuppressWarnings('rawtypes')
        @Override
        protected Object createNode(Object name, Map attributes, Object value) {
            xml.startNode(name.toString())
            if (attributes != null) {
                for (Object o : attributes.entrySet()) {
                    Map.Entry attribute = (Map.Entry) o
                    xml.attribute(attribute.getKey().toString(), attribute.getValue().toString())
                }
            }
            if (value != null) {
                xml.convertAnother(value)
            }
            return name
        }

        @Override
        protected void nodeCompleted(Object o, Object o1) {
            xml.end()
        }

        @Override
        protected void setParent(Object o, Object o1) {
            // do nothing
        }

    }

}
