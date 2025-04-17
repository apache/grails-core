/*
 * Copyright 2011 SpringSource
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
package org.grails.gsp.io;

import grails.plugins.GrailsPlugin;
import grails.plugins.GrailsPluginManager;
import grails.plugins.PluginManagerAware;
import grails.util.CollectionUtils;
import grails.util.Environment;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.grails.gsp.GroovyPage;
import org.grails.gsp.GroovyPageBinding;
import org.grails.io.support.GrailsResourceUtils;
import org.grails.plugins.BinaryGrailsPlugin;
import org.grails.taglib.TemplateVariableBinding;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Used to locate GSPs whether in development or WAR deployed mode from static
 * resources, custom resource loaders and binary plugins.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class DefaultGroovyPageLocator implements GroovyPageLocator, ResourceLoaderAware, ApplicationContextAware, PluginManagerAware {

    private static final Log LOG = LogFactory.getLog(DefaultGroovyPageLocator.class);
    public static final String PATH_TO_WEB_INF_VIEWS = "/WEB-INF/grails-app/views";
    private static final String SLASHED_VIEWS_DIR_PATH = "/" + GrailsResourceUtils.VIEWS_DIR_PATH;
    private static final String PLUGINS_PATH = "/plugins/";
    private static final String BLANK = "";
    protected Collection<ResourceLoader> resourceLoaders = new ConcurrentLinkedQueue<ResourceLoader>();
    protected GrailsPluginManager pluginManager;
    private ConcurrentMap<String, String> precompiledGspMap;
    protected boolean warDeployed = Environment.isWarDeployed();
    protected boolean reloadEnabled = !warDeployed;
    private Set<String> reloadedPrecompiledGspClassNames = new CopyOnWriteArraySet<String>();

    public void setResourceLoader(ResourceLoader resourceLoader) {
        addResourceLoader(resourceLoader);
    }

    public void addResourceLoader(ResourceLoader resourceLoader) {
        if (resourceLoader != null && !resourceLoaders.contains(resourceLoader)) {
            resourceLoaders.add(resourceLoader);
        }
    }

    public void setPrecompiledGspMap(Map<String, String> precompiledGspMap) {
        if (precompiledGspMap == null) {
            this.precompiledGspMap = null;
        } else {
            this.precompiledGspMap = new ConcurrentHashMap<String, String>(precompiledGspMap);
        }
    }

    public GroovyPageScriptSource findPage(final String uri) {
        GroovyPageScriptSource scriptSource = findResourceScriptSource(uri);
        if (scriptSource == null) {
            scriptSource = findBinaryScriptSource(uri);
        }
        if (scriptSource == null) {
            scriptSource = findResourceScriptSourceInPlugins(uri);
        }
        return scriptSource;
    }

    protected Resource findReloadablePage(final String uri) {
        Resource resource = findResource(uri);
        if (resource == null) {
            resource = findResourceInPlugins(uri);
        }
        return resource;
    }

    public GroovyPageScriptSource findPageInBinding(String pluginName, String uri, TemplateVariableBinding binding) {

        GroovyPageScriptSource scriptSource = null;
        String contextPath = resolveContextPath(pluginName, uri, binding);
        String fullURI = GrailsResourceUtils.appendPiecesForUri(contextPath, uri);

        if(pluginManager != null) {
            GrailsPlugin grailsPlugin = pluginManager.getGrailsPlugin(pluginName);
            if(grailsPlugin instanceof BinaryGrailsPlugin) {
                BinaryGrailsPlugin binaryGrailsPlugin = (BinaryGrailsPlugin) grailsPlugin;
                File projectDirectory = binaryGrailsPlugin.getProjectDirectory();
                if(projectDirectory != null) {
                    File f = new File(projectDirectory, GrailsResourceUtils.VIEWS_DIR_PATH + uri);
                    if(f.exists()) {
                        scriptSource = new GroovyPageResourceScriptSource(uri, new FileSystemResource(f));
                    }
                }
                else {
                    scriptSource = resolveViewInBinaryPlugin(binaryGrailsPlugin, uri);
                }
            }
        }

        if(scriptSource == null) {
            scriptSource = findPageInBinding(fullURI, binding);
        }

        if (scriptSource == null) {
            scriptSource = findResourceScriptSource(uri);
        }


        //last effort to resolve and force name of plugin to use camel case
        if (scriptSource == null) {
            contextPath = resolveContextPath(pluginName, uri, binding, true);
            scriptSource = findPageInBinding(GrailsResourceUtils.appendPiecesForUri(contextPath, uri), binding);
        }

        return scriptSource;
    }

    protected String resolveContextPath(String pluginName, String uri, TemplateVariableBinding binding) {
        return resolveContextPath(pluginName, uri, binding, false);
    }

    protected String resolveContextPath(String pluginName, String uri, TemplateVariableBinding binding, boolean forceCamelCase) {
        String contextPath = null;

        if (uri.startsWith("/plugins/")) {
            contextPath = BLANK;
        } else if (pluginName != null && pluginManager != null) {
            contextPath = pluginManager.getPluginPath(pluginName);
        } else if (binding instanceof GroovyPageBinding) {
            String pluginContextPath = ((GroovyPageBinding) binding).getPluginContextPath();
            contextPath = pluginContextPath != null ? pluginContextPath : BLANK;
        } else {
            contextPath = BLANK;
        }

        return contextPath;
    }

    public void removePrecompiledPage(GroovyPageCompiledScriptSource scriptSource) {
        reloadedPrecompiledGspClassNames.add(scriptSource.getCompiledClass().getName());
        if (scriptSource.getURI() != null && precompiledGspMap != null) {
            precompiledGspMap.remove(scriptSource.getURI());
        }
    }

    public GroovyPageScriptSource findPageInBinding(String uri, TemplateVariableBinding binding) {
        GroovyPageScriptSource scriptSource = findResourceScriptSource(uri);

        if (scriptSource == null) {
            GrailsPlugin pagePlugin = binding instanceof GroovyPageBinding ? ((GroovyPageBinding) binding).getPagePlugin() : null;
            if (pagePlugin instanceof BinaryGrailsPlugin) {
                BinaryGrailsPlugin binaryPlugin = (BinaryGrailsPlugin) pagePlugin;
                scriptSource = resolveViewInBinaryPlugin(binaryPlugin, uri);
            } else if (pagePlugin != null) {
                scriptSource = findResourceScriptSource(resolvePluginViewPath(uri, pagePlugin));
            }
        }

        if (scriptSource == null) {
            scriptSource = findBinaryScriptSource(uri);
        }

        return scriptSource;
    }

    protected GroovyPageScriptSource resolveViewInBinaryPlugin(BinaryGrailsPlugin binaryPlugin, String uri) {
        GroovyPageScriptSource scriptSource = null;
        uri = removeViewLocationPrefixes(uri);
        uri = GrailsResourceUtils.appendPiecesForUri(PATH_TO_WEB_INF_VIEWS, uri);
        Class<?> viewClass = binaryPlugin.resolveView(uri);
        if (viewClass != null && !reloadedPrecompiledGspClassNames.contains(viewClass.getName())) {
            scriptSource = createGroovyPageCompiledScriptSource(uri, uri, viewClass);
            // we know we have binary plugin, sp setting to null in the resourceCallable to skip reloading.
            ((GroovyPageCompiledScriptSource) scriptSource).setResourceCallable(null);
        }
        return scriptSource;
    }

    protected GroovyPageCompiledScriptSource createGroovyPageCompiledScriptSource(final String uri, String fullPath, Class<?> viewClass) {
        GroovyPageCompiledScriptSource scriptSource = new GroovyPageCompiledScriptSource(uri, fullPath, viewClass);
        if (reloadEnabled) {
            scriptSource.setResourceCallable(new PrivilegedAction<Resource>() {
                public Resource run() {
                    return findReloadablePage(uri);
                }
            });
        }
        return scriptSource;
    }

    protected GroovyPageScriptSource findBinaryScriptSource(String uri) {
        if (pluginManager == null) {
            return null;
        }

        List<GrailsPlugin> allPlugins = Arrays.asList(pluginManager.getAllPlugins());
        Collections.reverse(allPlugins);

        for (GrailsPlugin plugin : allPlugins) {
            if (!(plugin instanceof BinaryGrailsPlugin)) {
                continue;
            }

            BinaryGrailsPlugin binaryPlugin = (BinaryGrailsPlugin) plugin;
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("Searching plugin [%s] for GSP view [%s]", plugin.getName(), uri));
            }
            GroovyPageScriptSource scriptSource = resolveViewInBinaryPlugin(binaryPlugin, uri);
            if (scriptSource != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format("Found GSP view [%s] in plugin [%s]", uri, plugin.getName()));
                }
                return scriptSource;
            }
            else if(binaryPlugin.getProjectDirectory() != null) {
                scriptSource = resolveViewInPluginProjectDirectory(binaryPlugin, uri);
                if(scriptSource != null) {
                    return scriptSource;
                }
            }
        }

        return null;
    }

    private GroovyPageScriptSource resolveViewInPluginProjectDirectory(BinaryGrailsPlugin binaryPlugin, String uri) {
        File projectDirectory = binaryPlugin.getProjectDirectory();
        File f = new File(projectDirectory, "grails-app/views" + uri);
        if(f.exists()) {
            return new GroovyPageResourceScriptSource(uri, new FileSystemResource(f));
        }
        return null;
    }

    protected GroovyPageScriptSource findResourceScriptSourceInPlugins(String uri) {
        if (pluginManager == null) {
            return null;
        }

        for (GrailsPlugin plugin : pluginManager.getAllPlugins()) {
            if (plugin instanceof BinaryGrailsPlugin) {
                continue;
            }

            GroovyPageScriptSource scriptSource = findResourceScriptSource(resolvePluginViewPath(uri, plugin));
            if (scriptSource != null) {
                return scriptSource;
            }
        }

        return null;
    }

    protected Resource findResourceInPlugins(String uri) {
        if (pluginManager == null) {
            return null;
        }

        for (GrailsPlugin plugin : pluginManager.getAllPlugins()) {
            if (plugin instanceof BinaryGrailsPlugin) {
                continue;
            }

            Resource resource = findResource(resolvePluginViewPath(uri, plugin));
            if (resource != null) {
                return resource;
            }
        }

        return null;
    }

    protected String resolvePluginViewPath(String uri, GrailsPlugin plugin) {
        uri = removeViewLocationPrefixes(uri);
        return GrailsResourceUtils.appendPiecesForUri(plugin.getPluginPath(), GrailsResourceUtils.VIEWS_DIR_PATH, uri);
    }

    protected String removeViewLocationPrefixes(String uri) {
        uri = removePrefix(uri, GrailsResourceUtils.WEB_INF);
        uri = removePrefix(uri, SLASHED_VIEWS_DIR_PATH);
        uri = removePrefix(uri, GrailsResourceUtils.VIEWS_DIR_PATH);
        return uri;
    }

    protected String removePrefix(String uri, String prefix) {
        if (uri.startsWith(prefix)) {
            uri = uri.substring(prefix.length());
        }
        return uri;
    }

    protected GroovyPageScriptSource findResourceScriptSource(final String uri) {
        List<String> searchPaths = resolveSearchPaths(uri);

        return findResourceScriptPathForSearchPaths(uri, searchPaths);
    }

    protected List<String> resolveSearchPaths(String uri) {
        List<String> searchPaths = null;

        uri = removeViewLocationPrefixes(uri);
        if (warDeployed) {
            if (uri.startsWith(PLUGINS_PATH)) {
                PluginViewPathInfo pathInfo = getPluginViewPathInfo(uri);

                searchPaths = CollectionUtils.newList(
                        GrailsResourceUtils.appendPiecesForUri(GrailsResourceUtils.WEB_INF, PLUGINS_PATH, pathInfo.pluginName, GrailsResourceUtils.VIEWS_DIR_PATH, pathInfo.path),
                        GrailsResourceUtils.appendPiecesForUri(GrailsResourceUtils.WEB_INF, uri),
                        uri);
            } else {
                searchPaths = CollectionUtils.newList(
                        GrailsResourceUtils.appendPiecesForUri(PATH_TO_WEB_INF_VIEWS, uri),
                        uri);
            }
        } else {
            searchPaths = CollectionUtils.newList(
                GrailsResourceUtils.appendPiecesForUri(SLASHED_VIEWS_DIR_PATH, uri),
                GrailsResourceUtils.appendPiecesForUri(PATH_TO_WEB_INF_VIEWS, uri),
                uri);
        }
        return searchPaths;
    }

    @SuppressWarnings("unchecked")
    protected GroovyPageScriptSource findResourceScriptPathForSearchPaths(String uri, List<String> searchPaths) {
        if (isPrecompiledAvailable()) {
            for (String searchPath : searchPaths) {
                String gspClassName = precompiledGspMap.get(searchPath);
                if (gspClassName != null && !reloadedPrecompiledGspClassNames.contains(gspClassName)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(String.format("Found pre-compiled GSP template [%s] for path [%s]", gspClassName, searchPath));
                    }
                    Class<GroovyPage> gspClass = null;
                    try {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug(String.format("Loading GSP template [%s]", gspClassName));
                        }
                        gspClass = (Class<GroovyPage>) Class.forName(gspClassName, true, Thread.currentThread().getContextClassLoader());
                    } catch (ClassNotFoundException e) {
                        LOG.warn("Cannot load class " + gspClassName + ". Resuming on non-precompiled implementation.", e);
                    }
                    if (gspClass != null) {
                        GroovyPageCompiledScriptSource groovyPageCompiledScriptSource = createGroovyPageCompiledScriptSource(uri, searchPath, gspClass);
                        if (LOG.isDebugEnabled()) {
                            LOG.debug(String.format("Returning new GSP script source for class [%s]", gspClassName));
                        }
                        return groovyPageCompiledScriptSource;
                    }
                }
            }
        }

        Resource foundResource = findResource(searchPaths);
        return foundResource == null ? null : new GroovyPageResourceScriptSource(uri, foundResource);
    }

    protected Resource findResource(String uri) {
        return findResource(resolveSearchPaths(uri));
    }

    protected Resource findResource(List<String> searchPaths) {
        Resource foundResource = null;
        Resource resource;
        for (ResourceLoader loader : resourceLoaders) {
            for (String path : searchPaths) {
                resource = loader.getResource(path);
                if (resource != null && resource.exists()) {
                    foundResource = resource;
                    break;
                }
            }
            if (foundResource != null) break;
        }
        return foundResource;
    }

    private boolean isPrecompiledAvailable() {
        return precompiledGspMap != null && precompiledGspMap.size() > 0 && !Environment.isDevelopmentMode();
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        addResourceLoader(applicationContext);
    }

    public void setPluginManager(GrailsPluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    public static PluginViewPathInfo getPluginViewPathInfo(String uri) {
        return new PluginViewPathInfo(uri);
    }

    public static class PluginViewPathInfo {
        public String basePath;
        public String pluginName;
        public String path;

        public PluginViewPathInfo(String uri) {
            basePath = uri.substring(PLUGINS_PATH.length(), uri.length());
            int i = basePath.indexOf("/");
            if (i > -1) {
                pluginName = basePath.substring(0, i);
                path = basePath.substring(i, basePath.length());
            }
        }
    }

    public boolean isReloadEnabled() {
        return reloadEnabled;
    }

    public void setReloadEnabled(boolean reloadEnabled) {
        this.reloadEnabled = reloadEnabled;
    }
}
