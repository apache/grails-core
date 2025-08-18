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
package org.grails.web.util;

import org.grails.buffer.GrailsPrintWriterAdapter;
import org.grails.buffer.StreamByteBuffer;
import org.grails.buffer.StreamCharBuffer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * Response wrapper used to capture the content of a response (such as within in an include).
 *
 * @author Graeme Rocher
 * @since 1.2.1
 */
public class IncludeResponseWrapper extends HttpServletResponseWrapper {

    private StreamCharBuffer charBuffer;
    private PrintWriter pw;
    private StreamByteBuffer byteBuffer;
    private OutputStream os;
    private ServletOutputStream sos;
    private boolean usingStream;
    private boolean usingWriter;
    private int status;
    private String contentType;
    private boolean committed;
    private String redirectURL;

    public IncludeResponseWrapper(HttpServletResponse httpServletResponse) {
        super(httpServletResponse);
    }

    public String getRedirectURL() {
        return redirectURL;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public void setStatus(int i) {
        status = i;
    }

    @Override
    public boolean isCommitted() {
        return committed;
    }

    @Override
    public void sendRedirect(String s) throws IOException {
        committed = true;
        redirectURL = s;
        super.sendRedirect(s);
    }

    // don't add @Override since it's only a method as of Servlet 3.0
    public int getStatus() {
        return status;
    }

    @Override
    public void setContentType(String s) {
        contentType = s;
    }

    @Override
    public void setLocale(Locale locale) {
        // do nothing
    }

    @Override
    public void sendError(int i, String s) throws IOException {
        if(isCommitted()) throw new IllegalStateException("Response already committed");
        setStatus(i);
        flushBuffer();
    }

    @Override
    public void sendError(int i) throws IOException {
        if(isCommitted()) throw new IllegalStateException("Response already committed");
        setStatus(i);
        flushBuffer();
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (usingWriter) throw new IllegalStateException("Method getWriter() already called");

        if (!usingStream) {
            usingStream = true;
            byteBuffer = new StreamByteBuffer();
            os = byteBuffer.getOutputStream();
            sos = new ServletOutputStream() {
                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    os.write(b, off, len);
                }

                @Override
                public void write(byte[] b) throws IOException {
                    os.write(b);
                }

                @Override
                public void write(int b) throws IOException {
                    os.write(b);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener writeListener) {
                    //no op
                }
            };
        }

        return sos;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (usingStream) throw new IllegalStateException("Method getOutputStream() already called");

        if (!usingWriter) {
            usingWriter = true;
            charBuffer = new StreamCharBuffer();
            charBuffer.setNotifyParentBuffersEnabled(false);
            pw = GrailsPrintWriterAdapter.newInstance(charBuffer.getWriter());
        }
        return pw;
    }

    public Object getContent() throws CharacterCodingException {
        return getContent(StandardCharsets.UTF_8.name());
    }

    public Object getContent(String encoding) throws CharacterCodingException {
        if (usingWriter) {
            return charBuffer;
        }

        if (usingStream) {
            return byteBuffer.readAsString(encoding);
        }

        return "";
    }
    
    @Override
    public void resetBuffer() {
       if(isCommitted()) throw new IllegalStateException("Response already committed");
       if (usingWriter) {
          charBuffer.reset();
       }

       if (usingStream) {
          byteBuffer.reset();
       }
    }

    @Override
    public void reset() {
        resetBuffer();
    }    

    @Override
    public void setContentLength(int len) {
       // do nothing
    }

    @Override
    public void flushBuffer() {
       // do nothing
    }
}
