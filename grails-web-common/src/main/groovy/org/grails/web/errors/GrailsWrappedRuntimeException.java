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
package org.grails.web.errors;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.reflect.Constructor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;

import jakarta.servlet.ServletContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.context.support.WebApplicationContextUtils;

import grails.core.GrailsApplication;
import grails.util.GrailsStringUtils;
import org.grails.buffer.FastStringPrintWriter;
import org.grails.core.artefact.ControllerArtefactHandler;
import org.grails.core.artefact.ServiceArtefactHandler;
import org.grails.core.exceptions.GrailsException;
import org.grails.core.io.support.GrailsFactoriesLoader;
import org.grails.exceptions.reporting.SourceCodeAware;
import org.grails.gsp.ResourceAwareTemplateEngine;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.grails.web.util.GrailsApplicationAttributes;

/**
 * Wraps a Grails RuntimeException and attempts to extract more relevent diagnostic messages
 * from the stack trace.
 *
 * @author Graeme Rocher
 * @since 0.1
 */
public class GrailsWrappedRuntimeException extends GrailsException {
    private static final Class<? extends GrailsApplicationAttributes> grailsApplicationAttributesClass = GrailsFactoriesLoader.loadFactoryClasses(GrailsApplicationAttributes.class, GrailsWebRequest.class.getClassLoader()).get(0);
    private static final Constructor<? extends GrailsApplicationAttributes> grailsApplicationAttributesConstructor = ClassUtils.getConstructorIfAvailable(grailsApplicationAttributesClass, ServletContext.class);

    private static final long serialVersionUID = 7284065617154554366L;
    private static final Pattern ANY_GSP_DETAILS = Pattern.compile("_gsp.run");
    private static final Pattern PARSE_DETAILS_STEP1 = Pattern.compile("\\((\\w+)\\.groovy:(\\d+)\\)");
    private static final Pattern PARSE_DETAILS_STEP2 = Pattern.compile("at\\s{1}(\\w+)\\$_closure\\d+\\.doCall\\(\\1:(\\d+)\\)");
    private static final Pattern PARSE_GSP_DETAILS_STEP1 = Pattern.compile("_gsp\\.run\\(((\\w+?)_.*?):(\\d+)\\)");
    public static final String URL_PREFIX = "/WEB-INF/grails-app/";
    private static final Log LOG  = LogFactory.getLog(GrailsWrappedRuntimeException.class);
    private String className = UNKNOWN;
    private int lineNumber = - 1;
    private String stackTrace;
    private String[] codeSnippet = new String[0];
    private String gspFile;
    private Throwable cause;
    private PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    private String[] stackTraceLines;
    private static final String UNKNOWN = "Unknown";
    private String fileName;

    /**
     * @param servletContext The ServletContext instance
     * @param t The exception that was thrown
     */
    public GrailsWrappedRuntimeException(ServletContext servletContext, Throwable t) {
        super(t.getMessage(), t);
        this.cause = t;
        Throwable cause = t;

        FastStringPrintWriter pw = FastStringPrintWriter.newInstance();
        cause.printStackTrace(pw);
        stackTrace = pw.toString();

        while (cause.getCause() != cause) {
            if (cause.getCause() == null) {
                break;
            }
            cause = cause.getCause();
        }

        stackTraceLines = stackTrace.split("\\n");

        if (cause instanceof MultipleCompilationErrorsException) {
            MultipleCompilationErrorsException mcee = (MultipleCompilationErrorsException)cause;
            Object message = mcee.getErrorCollector().getErrors().iterator().next();
            if (message instanceof SyntaxErrorMessage) {
                SyntaxErrorMessage sem = (SyntaxErrorMessage)message;
                lineNumber = sem.getCause().getLine();
                className = sem.getCause().getSourceLocator();
                sem.write(pw);
            }
        }
        else {
            Matcher m1 = PARSE_DETAILS_STEP1.matcher(stackTrace);
            Matcher m2 = PARSE_DETAILS_STEP2.matcher(stackTrace);
            Matcher gsp = PARSE_GSP_DETAILS_STEP1.matcher(stackTrace);
            try {
                if (ANY_GSP_DETAILS.matcher(stackTrace).find() && gsp.find()) {
                    System.out.println(gsp.group(1) + " " + gsp.group(2) + " " + gsp.group(3));
                    className = gsp.group(1);
                    lineNumber = Integer.parseInt(gsp.group(3));
                    gspFile = URL_PREFIX + "views/" + gsp.group(2)  + '/' + className;
                }
                else {
                    if (m1.find()) {
                        do {
                            className = m1.group(1);
                            lineNumber = Integer.parseInt(m1.group(2));
                        }
                        while (m1.find());
                    }
                    else {
                        while (m2.find()) {
                            className = m2.group(1);
                            lineNumber = Integer.parseInt(m2.group(2));
                        }
                    }
                }
            }
            catch (NumberFormatException nfex) {
                // ignore
            }
        }

        LineNumberReader reader = null;
        try {
            checkIfSourceCodeAware(t);
            checkIfSourceCodeAware(cause);

            if (getLineNumber() > -1) {
                String fileLocation;
                String url = null;

                if (fileName != null) {
                    fileLocation = fileName;
                }
                else {
                    String urlPrefix = "";
                    if (gspFile == null) {
                        fileName = className.replace('.', '/') + ".groovy";

                        GrailsApplication application = WebApplicationContextUtils.getRequiredWebApplicationContext(servletContext).getBean(GrailsApplication.APPLICATION_ID, GrailsApplication.class);
                        // @todo Refactor this to get the urlPrefix from the ArtefactHandler
                        if (application.isArtefactOfType(ControllerArtefactHandler.TYPE, className)) {
                            urlPrefix += "/controllers/";
                        }
                        else if (application.isArtefactOfType("TagLib", className)) {
                            urlPrefix += "/taglib/";
                        }
                        else if (application.isArtefactOfType(ServiceArtefactHandler.TYPE, className)) {
                            urlPrefix += "/services/";
                        }
                        url = URL_PREFIX + urlPrefix + fileName;
                    }
                    else {
                        url = gspFile;
                        GrailsApplicationAttributes attrs = null;
                        try {
                            attrs = grailsApplicationAttributesConstructor.newInstance(servletContext);
                        }
                        catch (Exception e) {
                            ReflectionUtils.rethrowRuntimeException(e);
                        }
                        ResourceAwareTemplateEngine engine = attrs.getPagesTemplateEngine();
                        lineNumber = engine.mapStackLineNumber(url, lineNumber);
                    }
                    fileLocation = "grails-app" + urlPrefix + fileName;
                }

                InputStream in = null;
                if (!GrailsStringUtils.isBlank(url)) {
                    in = servletContext.getResourceAsStream(url);
                    LOG.debug("Attempting to display code snippet found in url " + url);
                }
                if (in == null) {
                    Resource r = null;
                    try {
                        r = resolver.getResource(fileLocation);
                        in = r.getInputStream();
                    }
                    catch (Throwable e) {
                        r = resolver.getResource("file:" + fileLocation);
                        if (r.exists()) {
                            try {
                                in = r.getInputStream();
                            }
                            catch (IOException e1) {
                                // ignore
                            }
                        }
                    }
                }

                if (in != null) {
                    reader = new LineNumberReader(new InputStreamReader(in, "UTF-8"));
                    String currentLine = reader.readLine();
                    StringBuilder buf = new StringBuilder();
                    while (currentLine != null) {
                        int currentLineNumber = reader.getLineNumber();
                        if ((lineNumber > 0 && currentLineNumber == lineNumber - 1) ||
                                (currentLineNumber == lineNumber)) {
                            buf.append(currentLineNumber)
                               .append(": ")
                               .append(currentLine)
                               .append("\n");
                        }
                        else if (currentLineNumber == lineNumber + 1) {
                            buf.append(currentLineNumber)
                               .append(": ")
                               .append(currentLine);
                            break;
                        }
                        currentLine = reader.readLine();
                    }
                    codeSnippet = buf.toString().split("\n");
                }
            }
        }
        catch (IOException e) {
            LOG.warn("[GrailsWrappedRuntimeException] I/O error reading line diagnostics: " + e.getMessage(), e);
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private void checkIfSourceCodeAware(Throwable t) {
        if (!(t instanceof SourceCodeAware)) {
            return;
        }

        final SourceCodeAware codeAware = (SourceCodeAware) t;
        if (codeAware.getFileName() != null) {
            fileName = codeAware.getFileName();
            if (className == null || UNKNOWN.equals(className)) {
                className = codeAware.getFileName();
            }
        }
        if (codeAware.getLineNumber() > -1) {
            lineNumber = codeAware.getLineNumber();
        }
    }

    /**
     * @return Returns the line.
     */
    public String[] getCodeSnippet() {
        return codeSnippet;
    }

    /**
     * @return Returns the className.
     */
    public String getClassName() {
        return className;
    }

    /**
     * @return Returns the lineNumber.
     */
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * @return Returns the stackTrace.
     */
    public String getStackTraceText() {
        return stackTrace;
    }

    /**
     * @return Returns the stackTrace lines
     */
    public String[] getStackTraceLines() {
        return stackTraceLines;
    }

    /* (non-Javadoc)
     * @see groovy.lang.GroovyRuntimeException#getMessage()
     */
    @Override
    public String getMessage() {
        return cause.getMessage();
    }
}
