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
package grails.web.servlet.mvc

import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

import jakarta.servlet.ServletContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession

import groovy.transform.CompileStatic

/**
 * An adapter class that takes a regular HttpSession and allows you to access it like a Groovy map.
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class GrailsHttpSession implements HttpSession {

    private final Lock sessionLock = new ReentrantLock()
    private HttpSession adaptee
    private final HttpServletRequest request

    GrailsHttpSession(HttpServletRequest request) {
        this.request = request
    }

    /* (non-Javadoc)
     * @see jakarta.servlet.http.HttpSession#getAttribute(java.lang.String)
     */
    Object getAttribute(String name) {
        sessionLock.lock()
        try {
            return createSessionIfNecessary().getAttribute(name)
        }
        finally {
            sessionLock.unlock()
        }
    }

    private HttpSession createSessionIfNecessary() {
        if (adaptee == null) adaptee = request.getSession(true)
        return adaptee
    }

    /* (non-Javadoc)
     * @see jakarta.servlet.http.HttpSession#getAttributeNames()
     */
    @SuppressWarnings([ 'rawtypes', 'unchecked' ])
    Enumeration getAttributeNames() {
        sessionLock.lock()
        try {
            return createSessionIfNecessary().getAttributeNames()
        }
        finally {
            sessionLock.unlock()
        }
    }

    /* (non-Javadoc)
     * @see jakarta.servlet.http.HttpSession#getCreationTime()
     */
    long getCreationTime() {
        sessionLock.lock()
        try {
            return createSessionIfNecessary().getCreationTime()
        }
        finally {
            sessionLock.unlock()
        }
    }

    /* (non-Javadoc)
     * @see jakarta.servlet.http.HttpSession#getId()
     */
    String getId() {
        sessionLock.lock()
        try {
            return createSessionIfNecessary().getId()
        }
        finally {
            sessionLock.unlock()
        }
    }

    /* (non-Javadoc)
     * @see jakarta.servlet.http.HttpSession#getLastAccessedTime()
     */
    long getLastAccessedTime() {
        sessionLock.lock()
        try {
            return createSessionIfNecessary().getLastAccessedTime()
        }
        finally {
            sessionLock.unlock()
        }
    }

    /* (non-Javadoc)
     * @see jakarta.servlet.http.HttpSession#getMaxInactiveInterval()
     */
    int getMaxInactiveInterval() {
        sessionLock.lock()
        try {
            return createSessionIfNecessary().getMaxInactiveInterval()
        }
        finally {
            sessionLock.unlock()
        }
    }

    /* (non-Javadoc)
     * @see jakarta.servlet.http.HttpSession#getServletContext()
     */
    ServletContext getServletContext() {
        sessionLock.lock()
        try {
            return createSessionIfNecessary().getServletContext()
        }
        finally {
            sessionLock.unlock()
        }
    }

    /**
     * @see jakarta.servlet.http.HttpSession#getSessionContext()
     * @deprecated
     */
    /*
    @Deprecated
    public jakarta.servlet.http.HttpSessionContext getSessionContext() {
        createSessionIfNecessary();
        synchronized (this) {
            return adaptee.getSessionContext();
        }
    }
    */

    /**
     * @see jakarta.servlet.http.HttpSession#getValue(java.lang.String)
     * @deprecated
     */
    /*
    @Deprecated
    public Object getValue(String name) {
        createSessionIfNecessary();
        synchronized (this) {
            return adaptee.getAttribute(name);
        }
    }
    */

    /**
     * @see jakarta.servlet.http.HttpSession#getValueNames()
     * @deprecated
     */
    /*
    @Deprecated
    public String[] getValueNames() {
        createSessionIfNecessary();
        synchronized (this) {
            return adaptee.getValueNames();
        }
    }
    */

    /**
     * @see jakarta.servlet.http.HttpSession#putValue(java.lang.String, java.lang.Object)
     * @deprecated
     */
    /*
    @Deprecated
    public void putValue(String name, Object value) {
        createSessionIfNecessary();
        synchronized (this) {
            adaptee.setAttribute(name, value);
        }
    }
    */

    /**
     * @see jakarta.servlet.http.HttpSession
     * @deprecated
     */
    /*
    @Deprecated
    public void removeValue(String name) {
        createSessionIfNecessary();
        synchronized (this) {
            adaptee.removeAttribute(name);
        }
    }
    */

    /* (non-Javadoc)
     * @see jakarta.servlet.http.HttpSession#invalidate()
     */
    @Deprecated
    void invalidate() {
        sessionLock.lock()
        try {
            HttpSession session = adaptee
            if (session == null) session = request.getSession(false)
            if (session != null) {
                adaptee = session
                session.invalidate()
            }
        }
        finally {
            sessionLock.unlock()
        }
    }

    /* (non-Javadoc)
     * @see jakarta.servlet.http.HttpSession#isNew()
     */
    boolean isNew() {
        sessionLock.lock()
        try {
            return createSessionIfNecessary().isNew()
        }
        finally {
            sessionLock.unlock()
        }
    }

    /* (non-Javadoc)
     * @see jakarta.servlet.http.HttpSession#removeAttribute(java.lang.String)
     */
    void removeAttribute(String name) {
        sessionLock.lock()
        try {
            createSessionIfNecessary().removeAttribute(name)
        }
        finally {
            sessionLock.unlock()
        }
    }

    /* (non-Javadoc)
     * @see jakarta.servlet.http.HttpSession#setAttribute(java.lang.String, java.lang.Object)
     */
    void setAttribute(String name, Object value) {
        sessionLock.lock()
        try {
            createSessionIfNecessary().setAttribute(name, value)
        }
        finally {
            sessionLock.unlock()
        }
    }

    /* (non-Javadoc)
     * @see jakarta.servlet.http.HttpSession#setMaxInactiveInterval(int)
     */
    void setMaxInactiveInterval(int arg0) {
        sessionLock.lock()
        try {
            createSessionIfNecessary().setMaxInactiveInterval(arg0)
        }
        finally {
            sessionLock.unlock()
        }
    }

    @SuppressWarnings('rawtypes')
    @Override
    String toString() {
        sessionLock.lock()
        try {
            HttpSession session = createSessionIfNecessary()
            StringBuilder sb = new StringBuilder('Session Content:\n')
            Enumeration e = session.getAttributeNames()
            while (e.hasMoreElements()) {
                String name = (String) e.nextElement()
                sb.append('  ')
                sb.append(name)
                sb.append(' = ')
                sb.append(session.getAttribute(name))
                sb.append('\n')
            }
            return sb.toString()
        }
        finally {
            sessionLock.unlock()
        }
    }

}
