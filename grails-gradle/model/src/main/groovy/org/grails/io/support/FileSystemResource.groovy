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
 * Based on Spring FileSystemResource implementation.
 *
 * @author Juergen Hoeller
 * @author Graeme Rocher
 *
 * @since 28.12.2003
 * @see java.io.File
 */
@CompileStatic
class FileSystemResource implements Resource {

    private final File file
    private final String path

    /**
     * Create a new FileSystemResource from a File handle.
     *
     * @param file a File handle
     */
    FileSystemResource(File file) {
        assertNotNull(file, 'File must not be null')
        this.file = file
        path = GrailsResourceUtils.cleanPath(file.getPath())
    }

    /**
     * Create a new FileSystemResource from a file path.
     *
     * @param path a file path
     */
    FileSystemResource(String path) {
        assertNotNull(path, 'Path must not be null')
        file = new File(path)
        this.path = GrailsResourceUtils.cleanPath(path)
    }

    /**
     * Return the file path for this resource.
     */
    final String getPath() {
        return path
    }

    /**
     * This implementation returns whether the underlying file exists.
     * @see java.io.File#exists()
     */
    boolean exists() {
        return file.exists()
    }

    /**
     * This implementation checks whether the underlying file is marked as readable
     * (and corresponds to an actual file with content, not to a directory).
     * @see java.io.File#canRead()
     * @see java.io.File#isDirectory()
     */
    boolean isReadable() {
        return file.canRead() && !file.isDirectory()
    }

    /**
     * This implementation opens a FileInputStream for the underlying file.
     * @see java.io.FileInputStream
     */
    InputStream getInputStream() throws IOException {
        return new FileInputStream(file)
    }

    /**
     * This implementation returns a URL for the underlying file.
     * @see java.io.File#toURI()
     */
    URL getURL() throws IOException {
        return file.toURI().toURL()
    }

    /**
     * This implementation returns a URI for the underlying file.
     * @see java.io.File#toURI()
     */
    URI getURI() throws IOException {
        return file.toURI()
    }

    /**
     * This implementation returns the underlying File reference.
     */
    File getFile() {
        return file
    }

    /**
     * This implementation returns the underlying File's length.
     */
    long contentLength() throws IOException {
        return file.length()
    }

    long lastModified() throws IOException {
        return file.lastModified()
    }

    /**
     * This implementation returns the name of the file.
     * @see java.io.File#getName()
     */
    String getFilename() {
        return file.getName()
    }

    /**
     * This implementation returns a description that includes the absolute
     * path of the file.
     * @see java.io.File#getAbsolutePath()
     */
    String getDescription() {
        return 'file [' + file.getAbsolutePath() + ']'
    }

    /**
     * This implementation creates a FileSystemResource, applying the given path
     * relative to the path of the underlying file of this resource descriptor.
     */
    Resource createRelative(String relativePath) {
        String pathToUse = GrailsResourceUtils.applyRelativePath(path, relativePath)
        return new FileSystemResource(pathToUse)
    }

    // implementation of WritableResource

    /**
     * This implementation checks whether the underlying file is marked as writable
     * (and corresponds to an actual file with content, not to a directory).
     * @see java.io.File#canWrite()
     * @see java.io.File#isDirectory()
     */
    boolean isWritable() {
        return file.canWrite() && !file.isDirectory()
    }

    /**
     * This implementation opens a FileOutputStream for the underlying file.
     * @see java.io.FileOutputStream
     */
    OutputStream getOutputStream() throws IOException {
        return new FileOutputStream(file)
    }

    /**
     * This implementation compares the underlying File references.
     */
    @Override
    boolean equals(Object obj) {
        return (this.is(obj) ||
                (obj instanceof FileSystemResource && path.equals(((FileSystemResource) obj).path)))
    }

    /**
     * This implementation returns the hash code of the underlying File reference.
     */
    @Override
    int hashCode() {
        return path.hashCode()
    }

    protected void assertNotNull(Object object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message)
        }
    }

    @Override
    String toString() {
        return file.toString()
    }

}
