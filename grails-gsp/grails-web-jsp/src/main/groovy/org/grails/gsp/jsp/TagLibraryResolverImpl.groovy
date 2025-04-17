/*
 * Copyright 2004-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.gsp.jsp

import groovy.transform.CompileStatic

import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

import jakarta.servlet.ServletContext

import grails.core.GrailsApplication
import grails.core.support.GrailsApplicationAware
import org.springframework.beans.factory.BeanClassLoaderAware
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ResourceLoaderAware
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.web.context.ServletContextAware
import org.springframework.web.context.support.ServletContextResource

/**
 * Resolves all of the available tag libraries from web.xml and all available JAR files.
 *
 * Kudos to the Freemarker (http://freemarker.sourceforge.net/) library for providing the inspiration for this code.
 *
 * @author Graeme Rocher
 */
@CompileStatic
class TagLibraryResolverImpl implements ServletContextAware, GrailsApplicationAware, TagLibraryResolver, ResourceLoaderAware, BeanClassLoaderAware {
    protected Map<String, JspTagLib> tagLibs = new ConcurrentHashMap<String, JspTagLib>()
    GrailsApplication grailsApplication
    ServletContext servletContext
    ClassLoader classLoader
    ResourceLoader resourceLoader
    @Value('${grails.gsp.tldScanPattern:}')
    String[] tldScanPatterns = [] as String[]
    volatile boolean initialized = false

    /**
     * Resolves a JspTagLib instance for the given URI
     */
    JspTagLib resolveTagLibrary(String uri) {
        if(!initialized) {
            initialize()
        }
        return tagLibs[uri]
    }

    public synchronized void initialize() {
        if(servletContext) {
            Resource webXml = getWebXmlFromServletContext()
            if (webXml?.exists()) {
                loadTagLibLocations(webXml)
            }
        }
        if(resourceLoader && tldScanPatterns) {
            PathMatchingResourcePatternResolver patternResolver=new PathMatchingResourcePatternResolver(resourceLoader)
            for(String tldResourcePattern : tldScanPatterns) {
                patternResolver.getResources(tldResourcePattern).each { Resource resource ->
                    JspTagLib jspTagLib = loadJspTagLib(resource.getInputStream())
                    if(jspTagLib) {
                        tagLibs.put(jspTagLib.URI, jspTagLib)
                    }
                }
            }
        }
        initialized = true
    }
    
    private loadTagLibLocations(Resource webXml) {
        if (!webXml) {
            return
        }
        WebXmlTagLibraryReader webXmlReader = new WebXmlTagLibraryReader(webXml.getInputStream())
        webXmlReader.getTagLocations().each { String uri, String location ->
            JspTagLib jspTagLib
            if (location.startsWith("jar:")) {
                jspTagLib = loadFromJar(uri, location)
            }
            else {
                jspTagLib = loadJspTagLib(getTldFromServletContext(location), uri)
            }
            if(jspTagLib) {
                tagLibs.put(uri,jspTagLib)
            }
        }
    }

    private JspTagLib loadFromJar(String uri, String loc) {
        JspTagLib jspTagLib = null
        List<URL> jarURLs = resolveJarUrls()
        def fileLoc = loc[4..loc.indexOf('!')-1]
        String pathWithinZip = loc[loc.indexOf('!')+1..-1]
        URL jarFile = jarURLs.find { URL url -> url.toExternalForm() == fileLoc}
        if (jarFile) {
            jarFile.openStream().withStream { InputStream jarFileInputStream ->
                ZipInputStream zipInput = new ZipInputStream(jarFileInputStream)
                ZipEntry entry = zipInput.getNextEntry()
                while (entry) {
                    if (entry.name == pathWithinZip) {
                        jspTagLib = loadJspTagLib(zipInput, uri)
                        break
                    }
                    entry = zipInput.getNextEntry()
                }
            }
        }
        return jspTagLib
    }
    
    private List resolveJarUrls() {
        List<URL> jarURLs = grailsApplication.isWarDeployed() ? getJarsFromServletContext() : resolveRootLoader()?.getURLs() as List
        return jarURLs
    }

    protected InputStream getTldFromServletContext(String loc) {
        servletContext.getResourceAsStream(loc)
    }

    protected Resource getWebXmlFromServletContext() {
        return new ServletContextResource(servletContext, "/WEB-INF/web.xml")
    }

    protected List<URL> getJarsFromServletContext() {
        def files = servletContext.getResourcePaths("/WEB-INF/lib")
        files = files.findAll { String path ->  path.endsWith(".jar") || path.endsWith(".zip")}
        files.collect { String path -> servletContext.getResource(path) } as List
    }

    /**
     * Obtains a reference to the first parent classloader that is a URLClassLoader and contains some URLs
     * 
     */
    protected URLClassLoader resolveRootLoader() {
        def classLoader = getClass().classLoader
        while(classLoader != null) {
            if(classLoader instanceof URLClassLoader && ((URLClassLoader)classLoader).getURLs()) {
                return (URLClassLoader)classLoader
            }
            classLoader = classLoader.parent
        }
        return null
    }

    private JspTagLib loadJspTagLib(InputStream inputStream, String specifiedUri = null) {
        TldReader tldReader = new TldReader(inputStream)
        String uri = specifiedUri?:tldReader.uri
        if(tldReader.tags) {
            return new JspTagLibImpl(uri, tldReader.tags, classLoader)
        } else {
            return null
        }
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader
    }

}
