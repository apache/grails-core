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
package org.grails.forge.io

import groovy.transform.CompileStatic
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException

/**
 * A file of an application could not be written where the application is generated. The message is
 * one line: the file, by its path in the project and never by the path of the project on this
 * computer, what went wrong, and what of the project was written before, which is not a whole
 * project. It is an {@link IllegalArgumentException}, as every other reason is for which an
 * application cannot be made where it was asked for.
 *
 * @since 9.0
 */
@CompileStatic
class OutputFailedException extends IllegalArgumentException {

    /** The most files a message names */
    public static final int MAX_NAMED = 10

    OutputFailedException(String message, Throwable cause) {
        super(message, cause)
    }

    /**
     * @param path the path of the file in the project
     * @param failure what the file system said
     * @param written the paths of the files that were written before
     */
    static OutputFailedException of(String path, IOException failure, List<String> written) {
        String before = written.isEmpty() ? 'Nothing was written' :
                "The project is not complete: ${written.size()} ${written.size() == 1 ? 'file was' : 'files were'} written before it " +
                        "(${written.take(MAX_NAMED).collect { String each -> shown(each) }.join(', ')}${written.size() > MAX_NAMED ? " and ${written.size() - MAX_NAMED} more" : ''})"
        return new OutputFailedException("Cannot write ${shown(path)}: ${reason(failure)}. ${before}", failure)
    }

    /**
     * @param failure what the file system said
     * @return that in words, without the path the file system names
     */
    static String reason(IOException failure) {
        if (failure instanceof AccessDeniedException) {
            return 'it, or the directory it is in, cannot be written to'
        }
        if (failure instanceof NoSuchFileException) {
            return 'a directory it is in is not there'
        }
        if (failure instanceof FileAlreadyExistsException) {
            return 'there is something of its name, or of the name of a directory it is in, that is not what the project needs there'
        }
        if (failure instanceof FileSystemException && ((FileSystemException) failure).getReason()) {
            // what the operating system says, which has no path in it
            return 'the file system says "' + shown(((FileSystemException) failure).getReason()) + '"'
        }
        return "the file system refused it (${failure.getClass().getSimpleName()})"
    }

    private static String shown(String text) {
        String safe = text.replaceAll(/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/, '?').replace('@|', '@ |')
        return safe.length() > 200 ? safe.substring(0, 200) + '...' : safe
    }
}
