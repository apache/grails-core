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

import grails.core.GrailsApplication;
import grails.core.support.GrailsApplicationAware;
import grails.io.IOUtils;
import grails.plugins.GrailsPlugin;
import grails.plugins.GrailsPluginManager;
import grails.util.CacheEntry;
import groovy.lang.GroovySystem;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.grails.encoder.Encoder;
import org.grails.gsp.compiler.GroovyPageParser;
import org.grails.gsp.jsp.TagLibraryResolver;
import org.grails.taglib.TagLibraryLookup;
import org.grails.taglib.encoder.WithCodecHelper;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.ReflectionUtils;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLConnection;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Encapsulates the information necessary to describe a GSP.
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 * @since 0.5
 */
public class GroovyPageMetaInfo implements GrailsApplicationAware {

    private static final Log LOG = LogFactory.getLog(GroovyPageMetaInfo.class);
    private TagLibraryLookup tagLibraryLookup;
    private TagLibraryResolver jspTagLibraryResolver;

    private boolean precompiledMode=false;
    private Class<?> pageClass;
    private long lastModified;
    private InputStream groovySource;
    private String contentType;
    private int[] lineNumbers;
    private String[] htmlParts;
    @SuppressWarnings("rawtypes")
    private Map jspTags = Collections.emptyMap();
    private GroovyPagesException compilationException;
    private Encoder expressionEncoder;
    private Encoder staticEncoder;
    private Encoder outEncoder;
    private Encoder taglibEncoder;
    private String expressionCodecName;
    private String staticCodecName;
    private String outCodecName;
    private String taglibCodecName;
    private boolean compileStaticMode;
    private boolean modelFieldsMode;
    private Set<Field> modelFields;

    public static final String HTML_DATA_POSTFIX = "_html.data";
    public static final String LINENUMBERS_DATA_POSTFIX = "_linenumbers.data";

    public static final long LASTMODIFIED_CHECK_INTERVAL =  Long.getLong("grails.gsp.reload.interval", 5000).longValue();
    private static final long LASTMODIFIED_CHECK_GRANULARITY =  Long.getLong("grails.gsp.reload.granularity", 2000).longValue();
    private GrailsApplication grailsApplication;

    private String pluginPath;
    private GrailsPlugin pagePlugin;
    private boolean initialized = false;

    private CacheEntry<Resource> shouldReloadCacheEntry = new CacheEntry<Resource>();
    public static String DEFAULT_PLUGIN_PATH = "";
    
    volatile boolean metaClassShouldBeRemoved=false;

    public GroovyPageMetaInfo() {

    }

    @SuppressWarnings("rawtypes")
    public GroovyPageMetaInfo(Class<?> pageClass) {
        this();
        precompiledMode=true;
        this.pageClass = pageClass;
        contentType = (String)ReflectionUtils.getField(ReflectionUtils.findField(pageClass, GroovyPageParser.CONSTANT_NAME_CONTENT_TYPE), null);
        jspTags = (Map)ReflectionUtils.getField(ReflectionUtils.findField(pageClass, GroovyPageParser.CONSTANT_NAME_JSP_TAGS), null);
        lastModified = (Long)ReflectionUtils.getField(ReflectionUtils.findField(pageClass, GroovyPageParser.CONSTANT_NAME_LAST_MODIFIED), null);
        expressionCodecName = (String)ReflectionUtils.getField(ReflectionUtils.findField(pageClass, GroovyPageParser.CONSTANT_NAME_EXPRESSION_CODEC), null);
        staticCodecName = (String)ReflectionUtils.getField(ReflectionUtils.findField(pageClass, GroovyPageParser.CONSTANT_NAME_STATIC_CODEC), null);
        outCodecName = (String)ReflectionUtils.getField(ReflectionUtils.findField(pageClass, GroovyPageParser.CONSTANT_NAME_OUT_CODEC), null);
        taglibCodecName = (String)ReflectionUtils.getField(ReflectionUtils.findField(pageClass, GroovyPageParser.CONSTANT_NAME_TAGLIB_CODEC), null);
        Field compileStaticModeField = ReflectionUtils.findField(pageClass, GroovyPageParser.CONSTANT_NAME_COMPILE_STATIC_MODE);
        if (compileStaticModeField != null) {
            compileStaticMode = (Boolean) ReflectionUtils.getField(compileStaticModeField, null);
        }
        Field modelFieldsModeField = ReflectionUtils.findField(pageClass, GroovyPageParser.CONSTANT_NAME_MODEL_FIELDS_MODE);
        if (modelFieldsModeField != null) {
            modelFieldsMode = (Boolean) ReflectionUtils.getField(modelFieldsModeField, null);
        }

        try {
            readHtmlData();
        }
        catch (IOException e) {
            throw new RuntimeException("Problem reading html data for page class " + pageClass, e);
        }
    }

    static interface GroovyPageMetaInfoInitializer {
        public void initialize(GroovyPageMetaInfo metaInfo);
    }

    synchronized void initializeOnDemand(GroovyPageMetaInfoInitializer initializer) {
        if (!initialized) {
            initializer.initialize(this);
        }
    }

    public void initialize() {
        expressionEncoder = getCodec(expressionCodecName);
        staticEncoder = getCodec(staticCodecName);
        outEncoder = getCodec(outCodecName);
        taglibEncoder = getCodec(taglibCodecName);

        initializePluginPath();
        initializeModelFields();

        initialized = true;
    }

    private synchronized void initializeModelFields() {
        if(getPageClass() != null) {
            Set<Field> modelFields = new HashSet<>();
            if (modelFieldsMode) {
                for (Field field : getPageClass().getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                        ReflectionUtils.makeAccessible(field);
                        modelFields.add(field);
                    }
                }
            }
            this.modelFields = Collections.unmodifiableSet(modelFields);
        }
    }

    private Encoder getCodec(String codecName) {
        return WithCodecHelper.lookupEncoder(grailsApplication, codecName);
    }

    private void initializePluginPath() {
        if (grailsApplication == null || pageClass == null) {
            return;
        }

        final ApplicationContext applicationContext = grailsApplication.getMainContext();
        if (applicationContext == null || !applicationContext.containsBean(GrailsPluginManager.BEAN_NAME)) {
            return;
        }

        GrailsPluginManager pluginManager = applicationContext.getBean(GrailsPluginManager.BEAN_NAME, GrailsPluginManager.class);
        pluginPath = pluginManager.getPluginPathForClass(pageClass);
        if (pluginPath == null) pluginPath=DEFAULT_PLUGIN_PATH ;
        pagePlugin = pluginManager.getPluginForClass(pageClass);
    }

    /**
     * Reads the static html parts from a file stored in a separate file in the same package as the precompiled GSP class
     *
     * @throws IOException
     */
    private void readHtmlData() throws IOException {
        String dataResourceName = resolveDataResourceName(HTML_DATA_POSTFIX);

        DataInputStream input = null;
        try {
            InputStream resourceStream = pageClass.getResourceAsStream(dataResourceName);

            if (resourceStream != null) {

                input = new DataInputStream(resourceStream);
                int arrayLen = input.readInt();
                htmlParts = new String[arrayLen];
                for (int i = 0; i < arrayLen; i++) {
                    htmlParts[i] = input.readUTF();
                }
            }
        }
        finally {
            IOUtils.closeQuietly(input);
        }
    }

    /**
     * reads the linenumber mapping information from a separate file that has been generated at precompile time
     *
     * @throws IOException
     */
    private void readLineNumbers() throws IOException {
        String dataResourceName = resolveDataResourceName(LINENUMBERS_DATA_POSTFIX);

        DataInputStream input = null;
        try {
            input = new DataInputStream(pageClass.getResourceAsStream(dataResourceName));
            int arrayLen = input.readInt();
            lineNumbers = new int[arrayLen];
            for (int i = 0; i < arrayLen; i++) {
                lineNumbers[i] = input.readInt();
            }
        }
        finally {
            IOUtils.closeQuietly(input);
        }
    }

    /**
     * resolves the file name for html and linenumber data files
     * the file name is the classname + POSTFIX
     *
     * @param postfix
     * @return The data resource name
     */
    private String resolveDataResourceName(String postfix) {
        String dataResourceName = pageClass.getName();
        int pos = dataResourceName.lastIndexOf('.');
        if (pos > -1) {
            dataResourceName = dataResourceName.substring(pos+1);
        }
        dataResourceName += postfix;
        return dataResourceName;
    }

    public TagLibraryLookup getTagLibraryLookup() {
        return tagLibraryLookup;
    }

    public void setTagLibraryLookup(TagLibraryLookup tagLibraryLookup) {
        this.tagLibraryLookup = tagLibraryLookup;
    }

    public TagLibraryResolver getJspTagLibraryResolver() {
        return jspTagLibraryResolver;
    }

    public void setJspTagLibraryResolver(TagLibraryResolver jspTagLibraryResolver) {
        this.jspTagLibraryResolver = jspTagLibraryResolver;
    }

    public Class<?> getPageClass() {
        return pageClass;
    }

    public void setPageClass(Class<?> pageClass) {
        this.pageClass = pageClass;
        initializePluginPath();
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public InputStream getGroovySource() {
        return groovySource;
    }

    public void setGroovySource(InputStream groovySource) {
        this.groovySource = groovySource;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public int[] getLineNumbers() {
        if (precompiledMode) {
            return getPrecompiledLineNumbers();
        }

        return lineNumbers;
    }

    private synchronized int[] getPrecompiledLineNumbers() {
        if (lineNumbers == null) {
            try {
                readLineNumbers();
            }
            catch (IOException e) {
                LOG.warn("Problem reading precompiled linenumbers", e);
            }
        }
        return lineNumbers;
    }

    public void setLineNumbers(int[] lineNumbers) {
        this.lineNumbers = lineNumbers;
    }

    @SuppressWarnings("rawtypes")
    public void setJspTags(Map jspTags) {
        this.jspTags = jspTags != null ? jspTags : Collections.emptyMap();
    }

    @SuppressWarnings("rawtypes")
    public Map getJspTags() {
        return jspTags;
    }

    public void setCompilationException(GroovyPagesException e) {
        compilationException = e;
    }

    public GroovyPagesException getCompilationException() {
        return compilationException;
    }

    public String[] getHtmlParts() {
        return htmlParts;
    }

    public void setHtmlParts(String[] htmlParts) {
        this.htmlParts = htmlParts;
    }

    public void applyLastModifiedFromResource(Resource resource) {
        this.lastModified = establishLastModified(resource);
    }

    /**
     * Attempts to establish what the last modified date of the given resource is. If the last modified date cannot
     * be etablished -1 is returned
     *
     * @param resource The Resource to evaluate
     * @return The last modified date or -1
     */
    private long establishLastModified(Resource resource) {
        if (resource == null) return -1;

        if (resource instanceof FileSystemResource) {
            return ((FileSystemResource)resource).getFile().lastModified();
        }

        long last;
        URLConnection urlc = null;

        try {
            URL url = resource.getURL();
            if ("file".equals(url.getProtocol())) {
                File file=new File(url.getFile());
                if (file.exists()) {
                    return file.lastModified();
                }
            }
            urlc = url.openConnection();
            urlc.setDoInput(false);
            urlc.setDoOutput(false);
            last = urlc.getLastModified();
        }
        catch (FileNotFoundException fnfe) {
            last = -1;
        }
        catch (IOException e) {
            last = -1;
        }
        finally {
            if (urlc != null) {
                try {
                    InputStream is = urlc.getInputStream();
                    if (is != null) {
                        is.close();
                    }
                }
                catch (IOException e) {
                    // ignore
                }
            }
        }

        return last;
    }

    /**
     * Checks if this GSP has expired and should be reloaded (there is a newer source gsp available)
     * PrivilegedAction is used so that locating the Resource is lazily evaluated.
     *
     * lastModified checking is done only when enough time has expired since the last check. This setting is controlled by the grails.gsp.reload.interval System property,
     * by default it's value is 5000 (ms).
     *
     * @param resourceCallable call back that resolves the source gsp lazily
     * @return true if the available gsp source file is newer than the loaded one.
     */
    public boolean shouldReload(final PrivilegedAction<Resource> resourceCallable) {
        if (resourceCallable == null) return false;
        Resource resource=checkIfReloadableResourceHasChanged(resourceCallable);
        return (resource != null);
    }

    public Resource checkIfReloadableResourceHasChanged(final PrivilegedAction<Resource> resourceCallable) {
        Callable<Resource> checkerCallable = new Callable<Resource>() {
            public Resource call() {
                Resource resource=resourceCallable.run();
                if (resource != null && resource.exists()) {
                    long currentLastmodified=establishLastModified(resource);
                    // granularity is required since lastmodified information is rounded some where in copying & war (zip) file information
                    // usually the lastmodified time is 1000L apart in files and in files extracted from the zip (war) file
                    if (currentLastmodified > 0 && Math.abs(currentLastmodified - lastModified) > LASTMODIFIED_CHECK_GRANULARITY) {
                        return resource;
                    }
                }
                return null;
            }
        };
        return shouldReloadCacheEntry.getValue(LASTMODIFIED_CHECK_INTERVAL, checkerCallable, true, null);
    }

    public boolean isPrecompiledMode() {
        return precompiledMode;
    }

    public GrailsApplication getGrailsApplication() {
        return grailsApplication;
    }

    public void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication;
    }

    public String getPluginPath() {
        return pluginPath;
    }

    public GrailsPlugin getPagePlugin() {
        return pagePlugin;
    }

    public Encoder getOutEncoder() {
        return outEncoder;
    }

    public Encoder getStaticEncoder() {
        return staticEncoder;
    }

    public Encoder getExpressionEncoder() {
        return expressionEncoder;
    }

    public Encoder getTaglibEncoder() {
        return taglibEncoder;
    }

    public void setExpressionCodecName(String expressionCodecName) {
        this.expressionCodecName = expressionCodecName;
    }

    public void setStaticCodecName(String staticCodecName) {
        this.staticCodecName = staticCodecName;
    }

    public void setOutCodecName(String pageCodecName) {
        this.outCodecName = pageCodecName;
    }

    public void setTaglibCodecName(String taglibCodecName) {
        this.taglibCodecName = taglibCodecName;
    }

    public boolean isCompileStaticMode() {
        return compileStaticMode;
    }

    public void setCompileStaticMode(boolean compileStaticMode) {
        this.compileStaticMode = compileStaticMode;
    }

    public boolean isModelFieldsMode() {
        return modelFieldsMode;
    }

    public void setModelFieldsMode(boolean modelFieldsMode) {
        this.modelFieldsMode = modelFieldsMode;
    }

    public Set<Field> getModelFields() {
        if (modelFields == null) {
            initializeModelFields();
        }
        return modelFields;
    }

    public void removePageMetaClass() {
        metaClassShouldBeRemoved = true;
        if(pageClass!=null) { 
            GroovySystem.getMetaClassRegistry().removeMetaClass(pageClass);
        }
    }

    public void writeToFinished(Writer out) {
        if(metaClassShouldBeRemoved) {
            removePageMetaClass();
        }
    }
}
