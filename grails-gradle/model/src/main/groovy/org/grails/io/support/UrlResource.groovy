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
package org.grails.io.support

import groovy.transform.CompileStatic

/**
 * Resource implementation for <code>java.net.URL</code> locators.
 * Obviously supports resolution as URL, and also as File in case of
 * the "file:" protocol.
 *
 * @author Juergen Hoeller
 * @since 28.12.2003
 * @see java.net.URL
 */
@CompileStatic
class UrlResource extends AbstractFileResolvingResource {

    /**
     * Original URL, used for actual access.
     */
    private final URL url

    /**
     * Cleaned URL (with normalized path), used for comparisons.
     */
    private final URL cleanedUrl

    /**
     * Original URI, if available; used for URI and File access.
     */
    private final URI uri

    /**
     * Create a new UrlResource.
     * @param url a URL
     */
    UrlResource(URL url) {
        this.url = url
        cleanedUrl = getCleanedUrl(url, url.toString())
        uri = null
    }

    /**
     * Create a new UrlResource.
     * @param uri a URI
     * @throws java.net.MalformedURLException if the given URL path is not valid
     */
    UrlResource(URI uri) throws MalformedURLException {
        url = uri.toURL()
        cleanedUrl = getCleanedUrl(url, uri.toString())
        this.uri = uri
    }

    /**
     * Create a new UrlResource.
     * @param path a URL path
     * @throws MalformedURLException if the given URL path is not valid
     */
    UrlResource(String path) throws MalformedURLException {
        url = new URL(path)
        cleanedUrl = getCleanedUrl(url, path)
        uri = null
    }

    /**
     * Determine a cleaned URL for the given original URL.
     * @param originalUrl the original URL
     * @param originalPath the original URL path
     * @return the cleaned URL
     */
    private URL getCleanedUrl(URL originalUrl, String originalPath) {
        try {
            return new URL(GrailsResourceUtils.cleanPath(originalPath))
        }
        catch (MalformedURLException ex) {
            // Cleaned URL path cannot be converted to URL
            // -> take original URL.
            return originalUrl
        }
    }

    /**
     * This implementation opens an InputStream for the given URL.
     * It sets the "UseCaches" flag to <code>false</code>,
     * mainly to avoid jar file locking on Windows.
     * @see java.net.URL#openConnection()
     * @see java.net.URLConnection#setUseCaches(boolean)
     * @see java.net.URLConnection#getInputStream()
     */
    InputStream getInputStream() throws IOException {
        URLConnection con = url.openConnection()
        useCachesIfNecessary(con)
        try {
            return con.getInputStream()
        }
        catch (IOException ex) {
            // Close the HTTP connection (if applicable).
            if (con instanceof HttpURLConnection) {
                ((HttpURLConnection) con).disconnect()
            }
            throw ex
        }
    }

    private static void useCachesIfNecessary(URLConnection con) {
        con.setUseCaches(con.getClass().getName().startsWith('JNLP'))
    }

    /**
     * This implementation returns the underlying URL reference.
     */
    URL getURL() throws IOException {
        return url
    }

    /**
     * This implementation returns the underlying URI directly,
     * if possible.
     */
    URI getURI() throws IOException {
        if (uri != null) {
            return uri
        }
        return getFile().toURI()
    }

    /**
     * This implementation returns a File reference for the underlying URL/URI,
     * provided that it refers to a file in the file system.
     */
    @Override
    File getFile() throws IOException {
        return uri == null ? super.getFile() : super.getFile(uri)
    }

    /**
     * This implementation creates a UrlResource, applying the given path
     * relative to the path of the underlying URL of this resource descriptor.
     * @see java.net.URL#URL(java.net.URL, String)
     */
    Resource createRelative(String relativePath) {
        if (relativePath.startsWith('/')) {
            relativePath = relativePath.substring(1)
        }
        try {
            return new UrlResource(new URL(url, relativePath))
        } catch (MalformedURLException e) {
            return null
        }
    }

    /**
     * This implementation returns the name of the file that this URL refers to.
     * @see java.net.URL#getFile()
     * @see java.io.File#getName()
     */
    String getFilename() {
        return new File(url.getFile()).getName()
    }

    /**
     * This implementation returns a description that includes the URL.
     */
    String getDescription() {
        return 'URL [' + url + ']'
    }

    /**
     * This implementation compares the underlying URL references.
     */
    @Override
    boolean equals(Object obj) {
        return (this.is(obj) ||
                (obj instanceof UrlResource && cleanedUrl.equals(((UrlResource) obj).cleanedUrl)))
    }

    /**
     * This implementation returns the hash code of the underlying URL reference.
     */
    @Override
    int hashCode() {
        return cleanedUrl.hashCode()
    }

    @Override
    String toString() {
        return getDescription()
    }

}
