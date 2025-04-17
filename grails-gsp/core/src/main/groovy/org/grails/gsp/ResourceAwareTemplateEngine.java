/*
 * Copyright 2004-2005 Graeme Rocher
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
package org.grails.gsp;

import grails.io.IOUtils;
import groovy.text.Template;
import groovy.text.TemplateEngine;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.runtime.DefaultGroovyMethodsSupport;
import org.grails.buffer.StreamByteBuffer;
import org.springframework.core.io.Resource;

import java.io.*;
import java.net.URL;

/**
 * An abstract TemplateEngine that extends the default Groovy TemplateEngine (@see groovy.text.TemplateEngine) and
 * provides the ability to create templates from the Spring Resource API
 *
 * @author Graeme Rocher
 * @since 0.4
 */
public abstract class ResourceAwareTemplateEngine extends TemplateEngine {
    public static final String BEAN_ID = "groovyPagesTemplateEngine";
    private static final String GROOVY_SOURCE_CHAR_ENCODING = "UTF-8";

    /**
     * Creates the specified Template using the given Spring Resource
     *
     * @param resource The Spring Resource to create the template for
     * @return A Template instance
     * @throws IOException Thrown when there was an error reading the Template
     * @throws ClassNotFoundException Thrown when there was a problem loading the Template into a class
     */
    public Template createTemplate(Resource resource) throws IOException, ClassNotFoundException {
        return createTemplateAndCloseInput(resource.getInputStream());
    }

    /**
     * Creates the specified Template using the given Spring Resource
     *
     * @param resource The Spring Resource to create the template for
     * @param cacheable Whether the resource can be cached
     * @return A Template instance
     *
     */
    public abstract Template createTemplate(Resource resource, boolean cacheable);

    @Override
    public final Template createTemplate(Reader reader) throws IOException {
        StreamByteBuffer buf=new StreamByteBuffer();
        IOUtils.copy(reader, new OutputStreamWriter(buf.getOutputStream(), GROOVY_SOURCE_CHAR_ENCODING));
        return createTemplate(buf.getInputStream());
    }

    /**
     * Unlike groovy.text.TemplateEngine, implementors need to provide an implementation that operates
     * with an InputStream
     *
     * @param inputStream The InputStream
     * @return A Template instance
     * @throws IOException Thrown when an IO error occurs reading the stream
     */
    public abstract Template createTemplate(InputStream inputStream) throws IOException;

    @Override
    public Template createTemplate(String templateText) throws CompilationFailedException, ClassNotFoundException, IOException {
        return createTemplate(new ByteArrayInputStream(templateText.getBytes(GROOVY_SOURCE_CHAR_ENCODING)));
    }

    @Override
    public Template createTemplate(File file) throws CompilationFailedException, ClassNotFoundException, IOException {
        return createTemplateAndCloseInput(new FileInputStream(file));
    }

    @Override
    public Template createTemplate(URL url) throws CompilationFailedException, ClassNotFoundException, IOException {
        return createTemplateAndCloseInput(url.openStream());
    }

    private Template createTemplateAndCloseInput(InputStream input) throws FileNotFoundException, IOException {
        try {
            return createTemplate(input);
        } finally {
            DefaultGroovyMethodsSupport.closeWithWarning(input);
        }
    }
    
    abstract public Template createTemplateForUri(String[] uris);
    
    abstract public int mapStackLineNumber(String url, int lineNumber);
}
