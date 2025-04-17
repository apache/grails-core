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
package org.grails.gsp;

import grails.config.Config;
import grails.config.Settings;
import grails.core.GrailsApplication;
import grails.core.GrailsClass;
import grails.io.IOUtils;
import grails.util.CacheEntry;
import grails.util.Environment;
import grails.util.GrailsUtil;
import groovy.lang.GroovyClassLoader;
import groovy.text.Template;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.IOGroovyMethods;
import org.grails.core.artefact.DomainClassArtefactHandler;
import org.grails.core.exceptions.DefaultErrorsPrinter;
import org.grails.exceptions.ExceptionUtils;
import org.grails.gsp.compiler.GroovyPageParser;
import org.grails.gsp.io.*;
import org.grails.gsp.jsp.TagLibraryResolver;
import org.grails.taglib.TagLibraryLookup;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.*;
import org.springframework.scripting.ScriptSource;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Based on (but not extending) the existing TemplateEngine implementations
 * within Groovy. It allows GSP pages to be re-used in different context using code like the below:
 *
 * <code>
 *      Template t = new GroovyPagesTemplateEngine()
 *                          .createTemplate(context,request,response);
 *      t.make()
 *       .writeTo(out);
 * </code>
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 *
 * @since 0.1
 */
public class GroovyPagesTemplateEngine extends ResourceAwareTemplateEngine implements ResourceLoaderAware, ApplicationContextAware, InitializingBean, BeanClassLoaderAware {

    public static final String CONFIG_PROPERTY_DISABLE_CACHING_RESOURCES = Settings.GSP_DISABLE_CACHING_RESOURCES;
    public static final String CONFIG_PROPERTY_GSP_ENABLE_RELOAD = Settings.GSP_ENABLE_RELOAD;
    public static final String BEAN_ID = ResourceAwareTemplateEngine.BEAN_ID;

    private static final String GENERATED_GSP_NAME_PREFIX = "gsp_script_";
    private static final Log LOG = LogFactory.getLog(GroovyPagesTemplateEngine.class);
    private static File dumpLineNumbersTo;

    private ConcurrentMap<String, CacheEntry<GroovyPageMetaInfo>> pageCache = new ConcurrentHashMap<String, CacheEntry<GroovyPageMetaInfo>>();
    private ClassLoader classLoader;
    private AtomicInteger scriptNameCount=new AtomicInteger(0);

    private GroovyPageLocator groovyPageLocator = new DefaultGroovyPageLocator();

    private boolean reloadEnabled;
    private TagLibraryLookup tagLibraryLookup;
    private TagLibraryResolver jspTagLibraryResolver;
    private boolean cacheResources = true;
    private String gspEncoding = System.getProperty("file.encoding", GroovyPageParser.DEFAULT_ENCODING);

    private GrailsApplication grailsApplication;
    private Map<String, Class<?>> cachedDomainsWithoutPackage;

    private List<GroovyPageSourceDecorator> groovyPageSourceDecorators = new ArrayList();

    static {
        String dirPath = System.getProperty("grails.dump.gsp.line.numbers.to.dir");
        if (dirPath != null) {
            File dir = new File(dirPath);
            if (dir.exists() || dir.mkdirs()) {
                dumpLineNumbersTo = dir;
            }
        }
    }

	private class GroovyPagesTemplateEngineCacheEntry extends CacheEntry<GroovyPageMetaInfo>{
		private final String pageName;

		public GroovyPagesTemplateEngineCacheEntry(String pageName){
			this.pageName = pageName;
		}

		 @Override
         protected boolean hasExpired(long timeout, Object cacheRequestObject) {
             GroovyPageMetaInfo meta = getValue();
             Resource resource = (Resource)cacheRequestObject;
             return meta == null || isGroovyPageReloadable(resource, meta);
         }

         @Override
         protected GroovyPageMetaInfo updateValue(GroovyPageMetaInfo oldValue, Callable<GroovyPageMetaInfo> updater, Object cacheRequestObject)
                 throws Exception {
             if(oldValue != null) {
                 oldValue.removePageMetaClass();
             }
             Resource resource = (Resource)cacheRequestObject;
             return buildPageMetaInfo(resource, pageName);
         }
	}

	private static class GroovyPagesTemplateEngineCallable implements Callable<CacheEntry<GroovyPageMetaInfo>> {

		private final CacheEntry<GroovyPageMetaInfo> cacheEntry;

		public GroovyPagesTemplateEngineCallable(CacheEntry<GroovyPageMetaInfo> cacheEntry){
			this.cacheEntry = cacheEntry;
		}

		@Override
		public CacheEntry<GroovyPageMetaInfo> call() throws Exception {
			return cacheEntry;
		}

	}

    public GroovyPagesTemplateEngine() {
        // default
    }

    public void setGroovyPageSourceDecorators(List<GroovyPageSourceDecorator> groovyPageSourceDecorators){
    	this.groovyPageSourceDecorators = groovyPageSourceDecorators;
    }

    public List<GroovyPageSourceDecorator> getGroovyPageSourceDecorators(){
    	return groovyPageSourceDecorators;
    }

    public void setGroovyPageLocator(GroovyPageLocator groovyPageLocator) {
        this.groovyPageLocator = groovyPageLocator;
    }

    public GroovyPageLocator getGroovyPageLocator() {
        return groovyPageLocator;
    }

    public void setResourceLoader(ResourceLoader resourceLoader) {
        groovyPageLocator.addResourceLoader(resourceLoader);
    }

    public void afterPropertiesSet() {
        if (classLoader == null) {
            classLoader = initGroovyClassLoader(Thread.currentThread().getContextClassLoader());
        }else if (!classLoader.getClass().equals(GroovyPageClassLoader.class)) {
            classLoader = initGroovyClassLoader(classLoader);
        }
        if (!Environment.isDevelopmentMode()) {
            cachedDomainsWithoutPackage = createDomainClassMap();
        }
    }

    private GroovyClassLoader initGroovyClassLoader(ClassLoader parent) {
        CompilerConfiguration compConfig = new CompilerConfiguration();
        compConfig.setSourceEncoding(GroovyPageParser.GROOVY_SOURCE_CHAR_ENCODING);
        return new GroovyPageClassLoader(parent, compConfig);
    }

    public void setTagLibraryLookup(TagLibraryLookup tagLibraryLookup) {
        this.tagLibraryLookup = tagLibraryLookup;
    }

    public void setJspTagLibraryResolver(TagLibraryResolver jspTagLibraryResolver) {
        this.jspTagLibraryResolver = jspTagLibraryResolver;
    }

    /**
     * Sets the ClassLoader that the TemplateEngine should use to
     * @param classLoader The ClassLoader to use when compilation of Groovy Pages occurs
     */
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Retrieves a line number matrix for the specified page that can be used
     * to retrieve the actual line number within the GSP page if the line number within the
     * compiled GSP is known
     *
     * @param url The URL of the page
     * @return An array where the index is the line number witin the compiled GSP and the value is the line number within the source
     */
    public int[] calculateLineNumbersForPage(String url) {
        try {
            Template t = createTemplate(url);
            if (t instanceof GroovyPageTemplate) {
                return ((GroovyPageTemplate)t).getMetaInfo().getLineNumbers();
            }
        }
        catch (Exception e) {
            // ignore, non critical method used for retrieving debug info
            LOG.warn("Exception retrieving line numbers from GSP: " + url + ", message: " + e.getMessage());
            LOG.debug("Full stack trace of error", e);
        }
        return new int[0];
    }

    public int mapStackLineNumber(String url, int lineNumber) {
        int[] lineNumbers = calculateLineNumbersForPage(url);
        if (lineNumber < lineNumbers.length) {
            lineNumber = lineNumbers[lineNumber - 1];
        }
        return lineNumber;
    }

    /**
     * Creates a Template for the given Spring Resource instance
     *
     * @param resource The Resource to create the Template for
     * @return The Template instance
     */
    @Override
    public Template createTemplate(Resource resource) {
        return createTemplate(resource, cacheResources);
    }

    /**
     * Creates a Template for the given Spring Resource instance
     *
     * @param resource The Resource to create the Template for
     * @param cacheable The resource can be cached or not
     * @return The Template instance
     */
    @Override
    public Template createTemplate(Resource resource, final boolean cacheable) {
        if (resource == null) {
            throw new GroovyPagesException("Resource is null. No Groovy page found.");
        }
        //Yags: Because, "pageName" was sent as null originally, it is never go in pageCache, but will force to compile the String again and till the time this request
        // is getting executed, it will occupy space in PermGen space. So if there are 1000 request for the same resource at a particular instance, there will be 1000 instance
        // class in PermGen instead of ideally being 1 as they as essentially same resource.
        //we will cache metaInfo only is Developer wants-to. Developer will make sure that he creates unique key for every unique pages s/he wants to put in cache
        final String pageName = establishPageName(resource, cacheable);
        try {
            return createTemplate(resource, pageName, cacheable);
        }
        catch (IOException e) {
            throw new GroovyPagesException("Error loading template", e);
        }
    }

    protected Template createTemplate(Resource resource, final String pageName, final boolean cacheable) throws IOException {
        GroovyPageMetaInfo meta;
        if(cacheable) {
            meta = CacheEntry.getValue(pageCache, pageName, -1, null,
                    new GroovyPagesTemplateEngineCallable(new GroovyPagesTemplateEngineCacheEntry(pageName))
                    , true, resource);
        } else {
            meta = buildPageMetaInfo(resource, pageName);
        }
        return new GroovyPageTemplate(meta);
    }

    protected String establishPageName(Resource resource, final boolean cacheable) {
        String name;
        if (cacheable) {
            name = establishPageName(resource, getPathForResource(resource));
        } else {
            name = establishPageName(resource, null);
        }
        return name;
    }

    /**
     * Creates a Template using the given URI.
     *
     * @param uri The URI of the page to create the template for
     * @return The Template instance
     * @throws CompilationFailedException
     */
    @Override
    public Template createTemplate(String uri) {
        return createTemplateForUri(uri);
    }

    private Template createTemplateFromPrecompiled(GroovyPageCompiledScriptSource compiledScriptSource) {
        GroovyPageMetaInfo meta = initializeCompiledMetaInfo(compiledScriptSource.getGroovyPageMetaInfo());
        if (isReloadEnabled()) {
            GroovyPageResourceScriptSource changedResourceScriptSource = compiledScriptSource.getReloadableScriptSource();
            if (changedResourceScriptSource != null) {
                groovyPageLocator.removePrecompiledPage(compiledScriptSource);
                return createTemplate(changedResourceScriptSource);
            }
        }
        return new GroovyPageTemplate(meta);
    }

    private GroovyPageMetaInfo initializeCompiledMetaInfo(GroovyPageMetaInfo meta) {
        meta.initializeOnDemand(new GroovyPageMetaInfo.GroovyPageMetaInfoInitializer() {
            public void initialize(GroovyPageMetaInfo metaInfo) {
                metaInfo.setGrailsApplication(grailsApplication);
                metaInfo.setJspTagLibraryResolver(jspTagLibraryResolver);
                metaInfo.setTagLibraryLookup(tagLibraryLookup);
                metaInfo.initialize();
                GroovyPagesMetaUtils.registerMethodMissingForGSP(metaInfo.getPageClass(), tagLibraryLookup);
            }
        });
        return meta;
    }

    public Template createTemplateForUri(String uri) {
        return createTemplateForUri(new String[]{uri});
    }

    public Template createTemplateForUri(String[] uris)  {
        GroovyPageScriptSource scriptSource = findScriptSource(uris);

        if (scriptSource != null) {
            return createTemplate(scriptSource);
        }
        return null;
    }

    public GroovyPageScriptSource findScriptSource(String uri) {
        return findScriptSource(new String[]{uri});
    }

    public GroovyPageScriptSource findScriptSource(String[] uris) {
        GroovyPageScriptSource scriptSource = null;

        for (String uri : uris) {
            scriptSource = groovyPageLocator.findPage(uri);
            if (scriptSource != null) break;
        }
        return scriptSource;
    }

    public Template createTemplate(ScriptSource scriptSource) {
        if (scriptSource instanceof GroovyPageCompiledScriptSource) {
            // handle pre-compiled
            return createTemplateFromPrecompiled((GroovyPageCompiledScriptSource) scriptSource);
        }

        if (scriptSource instanceof ResourceScriptSource) {
            ResourceScriptSource resourceSource = (ResourceScriptSource) scriptSource;
            Resource resource = resourceSource.getResource();
            return createTemplate(resource, true);
        }

        try {
            return createTemplate(scriptSource.getScriptAsString(), scriptSource.suggestedClassName());
        } catch (IOException e) {
            throw new RuntimeException("IOException in createTemplate", e);
        }
    }

    /**
     * Creates a Template using the given text for the Template and the given name. The name
     * of the template is required
     *
     * @param txt The URI of the page to create the template for
     * @param pageName The name of the page being parsed
     *
     * @return The Template instance
     * @throws CompilationFailedException
     * @throws IOException Thrown if an IO exception occurs creating the Template
     */
    public Template createTemplate(String txt, String pageName) throws IOException {
        Assert.hasLength(txt, "Argument [txt] cannot be null or blank");
        Assert.hasLength(pageName, "Argument [pageName] cannot be null or blank");

        return createTemplate(new ByteArrayResource(txt.getBytes("UTF-8"), pageName), pageName, pageName != null);
    }

    /**
     * Creates a Template for the given file
     *
     * @param file The File to use to construct the template with
     * @return A Groovy Template instance
     *
     * @throws CompilationFailedException When an error occured compiling the Template
     * @throws ClassNotFoundException When a Class cannot be found within the given Template
     * @throws IOException When a I/O Exception occurs reading the Template
     */
    @Override
    public Template createTemplate(File file) throws CompilationFailedException, ClassNotFoundException, IOException {
        return createTemplate(new FileSystemResource(file));
    }

    /**
     * Creates a Template for the given URL
     *
     * @param url The URL to use to construct the template with
     * @return A Groovy Template instance
     *
     * @throws CompilationFailedException When an error occured compiling the Template
     * @throws ClassNotFoundException When a Class cannot be found within the given Template
     * @throws IOException When a I/O Exception occurs reading the Template
     */
    @Override
    public Template createTemplate(URL url) throws CompilationFailedException, ClassNotFoundException, IOException {
        return createTemplate(new UrlResource(url));
    }

    /**
     * Create a Template for the given InputStream
     * @param inputStream The InputStream to create the Template for
     * @return The Template instance
     */
    @Override
    public Template createTemplate(InputStream inputStream) {
        GroovyPageMetaInfo metaInfo = buildPageMetaInfo(inputStream, null, null);
        return new GroovyPageTemplate(metaInfo);
    }

    protected GroovyPageMetaInfo buildPageMetaInfo(Resource resource, String pageName) throws IOException {
        InputStream inputStream = resource.getInputStream();
        try {
            return buildPageMetaInfo(inputStream, resource, pageName);
        }
        finally {
            inputStream.close();
        }
    }

    private StringBuilder decorateGroovyPageSource(StringBuilder source) throws IOException {
    	for(GroovyPageSourceDecorator groovyPageSourceDecorator : groovyPageSourceDecorators){
    		source = groovyPageSourceDecorator.decorate(source);
    	}
    	return source;
    }

    /**
     * Establishes whether a Groovy page is reloadable. A GSP is only reloadable in the development environment.
     *
     * @param resource The Resource to check.
     * @param meta The current GroovyPageMetaInfo instance
     * @return true if it is reloadable
     */
    private boolean isGroovyPageReloadable(final Resource resource, GroovyPageMetaInfo meta) {
        return isReloadEnabled() && meta.shouldReload(new PrivilegedAction<Resource>() {
            public Resource run() {
                return resource;
            }
        });
    }

    /**
     * Return whether reload is enabled for the GroovyPagesTemplateEngine
     *
     * @return true if it is
     */
    public boolean isReloadEnabled() {
        return reloadEnabled;
    }

    /**
     * Sets whether reloading is enabled
     *
     * @param b True if it is enabled
     */
    public void setReloadEnabled(boolean b) {
        reloadEnabled = b;
    }

    /**
     * Attempts to retrieve a reference to a GSP as a Spring Resource instance for the given URI.
     *
     * @param uri The URI to check
     * @return A Resource instance
     */
    public Resource getResourceForUri(String uri) {
        GroovyPageScriptSource scriptSource = getResourceWithinContext(uri);
        if (scriptSource != null && (scriptSource instanceof GroovyPageResourceScriptSource)) {
            return ((GroovyPageResourceScriptSource)scriptSource).getResource();
        }
        return null;
    }

    private GroovyPageScriptSource getResourceWithinContext(String uri) {
        Assert.state(groovyPageLocator != null, "TemplateEngine not initialised correctly, no [groovyPageLocator] specified!");
        GroovyPageScriptSource scriptSource = groovyPageLocator.findPage(uri);
        if (scriptSource != null) {
            return scriptSource;
        }
        return null;
    }

    /**
     * Constructs a GroovyPageMetaInfo instance which holds the script class, modified date and so on
     *
     * @param inputStream The InputStream to construct the GroovyPageMetaInfo instance from
     * @param res The Spring Resource to construct the MetaInfo from
     * @param pageName The name of the page (can be null, in which case method responsible for calculating appropriate alternative)
     * @return The GroovyPageMetaInfo instance
     */
    protected GroovyPageMetaInfo buildPageMetaInfo(InputStream inputStream, Resource res, String pageName) {
        String name = establishPageName(res, pageName);

        GroovyPageParser parser;
        String path = getPathForResource(res);
        try {
        	String gspSource = IOUtils.toString(inputStream, getGspEncoding());
            parser = new GroovyPageParser(name, path, path, decorateGroovyPageSource(new StringBuilder(gspSource)).toString(),
                    grailsApplication != null? grailsApplication.getConfig() : null);
        }
        catch (IOException e) {
            throw new GroovyPagesException("I/O parsing Groovy page ["+(res != null ? res.getDescription() : name)+"]: " + e.getMessage(),e);
        }

        InputStream in = parser.parse();

        // Make a new metaInfo
        GroovyPageMetaInfo metaInfo = createPageMetaInfo(parser, in);
        metaInfo.applyLastModifiedFromResource(res);
        try {
            metaInfo.setPageClass(compileGroovyPage(in, name, path, metaInfo));
            metaInfo.setHtmlParts(parser.getHtmlPartsArray());
        }
        catch (GroovyPagesException e) {
            metaInfo.setCompilationException(e);
        }

        return metaInfo;
    }

    private String getPathForResource(Resource res) {
        if (res == null) return "";

        String path = null;
        try {
            File file = res.getFile();
            if (file != null) {
                path = file.getAbsolutePath();
            }
        }
        catch (IOException e) {
            // ignore
        }
        if (path != null) {
            return path;
        }
        if (res.getDescription() != null) {
            return res.getDescription();
        }
        return "";
    }

    /**
     * Attempts to compile the given InputStream into a Groovy script using the given name
     * @param in The InputStream to read the Groovy code from
     * @param name The name of the class to use
     * @param pageName The page name
     * @param metaInfo
     * @return The compiled java.lang.Class, which is an instance of groovy.lang.Script
     */
    private Class<?> compileGroovyPage(InputStream in, String name, String pageName, GroovyPageMetaInfo metaInfo) {
        GroovyClassLoader groovyClassLoader = findOrInitGroovyClassLoader();

        // Compile the script into an object
        Class<?> scriptClass;
        try {
            String groovySource = IOGroovyMethods.getText(in, GroovyPageParser.GROOVY_SOURCE_CHAR_ENCODING);
            //System.out.println(groovySource);
            scriptClass = groovyClassLoader.parseClass(groovySource, name);
        }
        catch (CompilationFailedException e) {
            LOG.error("Compilation error compiling GSP ["+name+"]:" + e.getMessage(), e);

            int lineNumber = ExceptionUtils.extractLineNumber(e);

            final int[] lineMappings = metaInfo.getLineNumbers();
            if (lineNumber>0 && lineNumber < lineMappings.length) {
                lineNumber = lineMappings[lineNumber-1];
            }
            String relativePageName = DefaultErrorsPrinter.makeRelativeIfPossible(pageName);
            throw new GroovyPagesException("Could not parse script [" + relativePageName + "]: " + e.getMessage(),e, lineNumber, pageName);
        }
        catch (IOException e) {
            String relativePageName = DefaultErrorsPrinter.makeRelativeIfPossible(pageName);
            throw new GroovyPagesException("IO exception parsing script ["+ relativePageName + "]: " + e.getMessage(), e);
        }
        GroovyPagesMetaUtils.registerMethodMissingForGSP(scriptClass, tagLibraryLookup);

        return scriptClass;
    }

    private synchronized GroovyClassLoader findOrInitGroovyClassLoader() {
        if (!(classLoader instanceof GroovyPageClassLoader)) {
            classLoader = initGroovyClassLoader(classLoader);
        }
        return (GroovyClassLoader)classLoader;
    }

    /**
     * Creates a GroovyPageMetaInfo instance from the given Parse object, and initialises it with the the specified
     * last modifed date and InputStream
     *
     * @param parse The Parse object
     * @param in The InputStream instance
     * @return A GroovyPageMetaInfo instance
     */
    private GroovyPageMetaInfo createPageMetaInfo(GroovyPageParser parse, InputStream in) {
        GroovyPageMetaInfo pageMeta = new GroovyPageMetaInfo();
        pageMeta.setGrailsApplication(grailsApplication);
        pageMeta.setJspTagLibraryResolver(jspTagLibraryResolver);
        pageMeta.setTagLibraryLookup(tagLibraryLookup);
        pageMeta.setContentType(parse.getContentType());
        pageMeta.setLineNumbers(parse.getLineNumberMatrix());
        pageMeta.setJspTags(parse.getJspTags());

        pageMeta.setStaticCodecName(parse.getStaticCodecDirectiveValue());
        pageMeta.setExpressionCodecName(parse.getExpressionCodecDirectiveValue());
        pageMeta.setOutCodecName(parse.getOutCodecDirectiveValue());
        pageMeta.setTaglibCodecName(parse.getTaglibCodecDirectiveValue());
        pageMeta.setCompileStaticMode(parse.isCompileStaticMode());
        pageMeta.setModelFieldsMode(parse.isModelFieldsMode());

        pageMeta.initialize();
        // just return groovy and don't compile if asked
        if (GrailsUtil.isDevelopmentEnv()) {
            pageMeta.setGroovySource(in);
        }

        if (dumpLineNumbersTo != null) {
            String fileName = parse.getClassName() + GroovyPageMetaInfo.LINENUMBERS_DATA_POSTFIX;
            File file = new File(dumpLineNumbersTo, fileName);
            try {
                parse.writeLineNumbers(file);
            }
            catch (IOException ignored) {
                if (file.exists()) {
                    file.delete();
                }
            }
        }

        return pageMeta;
    }

    /**
     * Establishes the name to use for the given resource
     *
     * @param res The Resource to calculate the name for
     * @param pageName The name of the page, can be null, in which case method responsible for calculation
     *
     * @return  The name as a String
     */
    protected String establishPageName(Resource res, String pageName) {
        if (res == null) {
            return generateTemplateName();
        }

        try {
            String name = pageName != null ? pageName : res.getURL().getPath();
            // As the name take the first / off and then replace all characters that aren't
            // a word character or a digit with an underscore
            if (name.startsWith("/")) name = name.substring(1);
            return name.replaceAll("[^\\w\\d]", "_");
        }
        catch (IllegalStateException e) {
            return generateTemplateName();
        }
        catch (IOException ioex) {
            return generateTemplateName();
        }
    }

    /**
     * Generates the template name to use if it cannot be established from the Resource
     *
     * @return The template name
     */
    private String generateTemplateName() {
        return GENERATED_GSP_NAME_PREFIX + scriptNameCount.incrementAndGet();
    }

    /**
     * Sets the ResourceLoader from the ApplicationContext
     *
     * @param applicationContext The ApplicationContext
     * @throws BeansException Thrown when an error occurs with the ApplicationContext
     */
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (applicationContext.containsBean(GrailsApplication.APPLICATION_ID)) {
            this.grailsApplication = applicationContext.getBean(GrailsApplication.APPLICATION_ID, GrailsApplication.class);
            Config config = grailsApplication.getConfig();
            this.gspEncoding = config.getProperty(GroovyPageParser.CONFIG_PROPERTY_GSP_ENCODING, System.getProperty("file.encoding", GroovyPageParser.DEFAULT_ENCODING));
        }
    }

    /**
     * Returns the path to the view of the relative URI within the Grails views directory
     *
     * @param relativeUri The relative URI
     * @return The path of the URI within the Grails view directory
     */
    protected String getUriWithinGrailsViews(String relativeUri) {
        StringBuilder buf = new StringBuilder();
        String[] tokens;
        if (relativeUri.startsWith("/")) {
            relativeUri = relativeUri.substring(1);
        }

        if (relativeUri.indexOf('/')>-1) {
            tokens = relativeUri.split("/");
        }
        else {
            tokens = new String[]{relativeUri};
        }

        buf.append(DefaultGroovyPageLocator.PATH_TO_WEB_INF_VIEWS);
        for (String token : tokens) {
            buf.append('/').append(token);
        }
        if (!relativeUri.endsWith(GroovyPage.EXTENSION)) {
            buf.append(GroovyPage.EXTENSION);
        }
        return buf.toString();
    }

    /**
     * Clears the page cache. Views will be re-compiled.
     */
    public void clearPageCache() {
        for(Iterator<Map.Entry<String, CacheEntry<GroovyPageMetaInfo>>> it = pageCache.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, CacheEntry<GroovyPageMetaInfo>> entry = it.next();
            GroovyPageMetaInfo metaInfo = entry.getValue().getValue();
            if(metaInfo != null) {
                metaInfo.removePageMetaClass();
            }
            it.remove();
        }
    }

    public boolean isCacheResources() {
        return cacheResources;
    }

    public void setCacheResources(boolean cacheResources) {
        this.cacheResources = cacheResources;
    }

    public Map<String, Class<?>> getDomainClassMap() {
        if (cachedDomainsWithoutPackage != null) {
            return cachedDomainsWithoutPackage;
        }
        return createDomainClassMap();
    }

    /**
     * The domainClassMap is used in GSP binding to "auto-import" domain classes in packages without package prefix.
     * real imports aren't used, instead each class is added to the binding
     *
     * This feature has existed earlier, the code has just been refactored and moved to GroovyPagesTemplateEngine
     * to prevent using the static cache that was used previously.
     */
    private Map<String, Class<?>> createDomainClassMap() {
        Map<String, Class<?>> domainsWithoutPackage = new HashMap<String, Class<?>>();
        if (grailsApplication != null) {
            GrailsClass[] domainClasses = grailsApplication.getArtefacts(DomainClassArtefactHandler.TYPE);
            for (GrailsClass domainClass : domainClasses) {
                final Class<?> theClass = domainClass.getClazz();
                domainsWithoutPackage.put(theClass.getName(), theClass);
            }
        }
        return domainsWithoutPackage;
    }

    @Override
    public void setBeanClassLoader(ClassLoader beanClassLoader) {
        // support passing BeanClassLoader as parent classloader for templates
        // don't set the classLoader field if it already has an explicit value
        if(beanClassLoader != null && this.classLoader == null) {
            this.classLoader = beanClassLoader;
        }
    }

    public String getGspEncoding() {
        return this.gspEncoding;
    }
}
