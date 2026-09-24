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
package org.grails.io.support;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import groovy.xml.FactorySupport;
import groovy.xml.XmlSlurper;

import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import org.apache.grails.gradle.common.XmlParserFeature;

/**
 * Simple utility methods for file and stream copying.
 * All copy methods use a block size of 4096 bytes,
 * and close all affected streams when done.
 *
 * <p>Mainly for use within the framework,
 * but also useful for application code.
 *
 * @author Juergen Hoeller
 * @author Graeme Rocher
 *
 * @since 06.10.2003
 */
@SuppressWarnings("unchecked")
public class SpringIOUtils {

    @SuppressWarnings("rawtypes")
    private static Map algorithms = new HashMap();

    static {
        algorithms.put("md5", "MD5");
        algorithms.put("sha1", "SHA-1");
    }

    // byte to hex string converter
    private static final char[] CHARS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'a', 'b', 'c', 'd', 'e', 'f'};
    public static final int BUFFER_SIZE = 4096;

    /**
     * Convert a byte[] array to readable string format. This makes the "hex" readable!
     *
     * @return result in String format
     * @param in
     *            byte[] buffer to convert to string format
     */
    public static String byteArrayToHexString(byte[] in) {
        byte ch = 0x00;

        if (in == null || in.length <= 0) {
            return null;
        }

        StringBuilder out = new StringBuilder(in.length * 2);

        //CheckStyle:MagicNumber OFF
        for (int i = 0; i < in.length; i++) {
            ch = (byte) (in[i] & 0xF0); // Strip off high nibble
            ch = (byte) (ch >>> 4); // shift the bits down
            ch = (byte) (ch & 0x0F); // must do this is high order bit is on!

            out.append(CHARS[ch]); // convert the nibble to a String Character
            ch = (byte) (in[i] & 0x0F); // Strip off low nibble
            out.append(CHARS[ch]); // convert the nibble to a String Character
        }
        //CheckStyle:MagicNumber ON

        return out.toString();
    }

    public static String computeChecksum(File f, String algorithm) throws IOException {
        return byteArrayToHexString(compute(f, algorithm));
    }

    private static byte[] compute(File f, String algorithm) throws IOException {
        InputStream is = new FileInputStream(f);

        try {
            MessageDigest md = getMessageDigest(algorithm);
            md.reset();

            byte[] buf = new byte[BUFFER_SIZE];
            int len = 0;
            while ((len = is.read(buf)) != -1) {
                md.update(buf, 0, len);
            }
            return md.digest();
        } finally {
            is.close();
        }
    }

    private static MessageDigest getMessageDigest(String algorithm) {
        String mdAlgorithm = (String) algorithms.get(algorithm);
        if (mdAlgorithm == null) {
            throw new IllegalArgumentException("unknown algorithm " + algorithm);
        }
        try {
            return MessageDigest.getInstance(mdAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("unknown algorithm " + algorithm);
        }
    }

    /**
     * Adds the contents of 1 array to another
     *
     * @param array1 The target array
     * @param array2 The source array
     * @return The new array
     */
    public static Object[] addAll(Object[] array1, Object[] array2) {
        Object[] joinedArray = (Object[]) Array.newInstance(array1.getClass().getComponentType(), array1.length + array2.length);
        System.arraycopy(array1, 0, joinedArray, 0, array1.length);
        try {
            System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
        } catch (ArrayStoreException ase) {
            final Class<?> type1 = array1.getClass().getComponentType();
            final Class<?> type2 = array2.getClass().getComponentType();
            if (!type1.isAssignableFrom(type2)) {
                throw new IllegalArgumentException("Cannot store " + type2.getName() + " in an array of " + type1.getName());
            }
            throw ase; // No, so rethrow original
        }
        return joinedArray;
    }

    //---------------------------------------------------------------------
    // Copy methods for java.io.File
    //---------------------------------------------------------------------

    /**
     * Copies all the resources for the given target directory. The base resource serves to calculate the relative path such that the
     * directory structure is maintained
     *
     * @param base The base resource
     * @param resources The resources to copy
     * @param targetDir The target directory
     */
    public static void copyAll(Resource base, Resource[] resources, File targetDir) throws IOException {
        final URL baseUrl = base.getURL();
        for (Resource resource : resources) {
            final InputStream input = resource.getInputStream();
            final File target = new File(targetDir, resource.getURL().toString().substring(baseUrl.toString().length()));
            copy(new BufferedInputStream(input), new BufferedOutputStream(new FileOutputStream(target)));
        }
    }

    /**
     * Copy the contents of the given input File to the given output File.
     * @param in the file to copy from
     * @param out the file to copy to
     * @return the number of bytes copied
     * @throws java.io.IOException in case of I/O errors
     */
    public static int copy(File in, File out) throws IOException {
        assert in != null : "No input File specified";
        assert out != null : "No output File specified";
        return copy(new BufferedInputStream(new FileInputStream(in)),
                new BufferedOutputStream(new FileOutputStream(out)));
    }

    /**
     * Copy the contents of the given input File to the given output File.
     * @param in the file to copy from
     * @param out the file to copy to
     * @return the number of bytes copied
     * @throws java.io.IOException in case of I/O errors
     */
    public static int copy(Resource in, File out) throws IOException {
        assert in != null : "No input File specified";
        assert out != null : "No output File specified";
        return copy(new BufferedInputStream(in.getInputStream()),
                new BufferedOutputStream(new FileOutputStream(out)));
    }

    /**
     * Copy the contents of the given byte array to the given output File.
     * @param in the byte array to copy from
     * @param out the file to copy to
     * @throws IOException in case of I/O errors
     */
    public static void copy(byte[] in, File out) throws IOException {
        assert in != null : "No input byte array specified";
        assert out != null : "No output File specified";
        ByteArrayInputStream inStream = new ByteArrayInputStream(in);
        OutputStream outStream = new BufferedOutputStream(new FileOutputStream(out));
        copy(inStream, outStream);
    }

    /**
     * Copy the contents of the given input File into a new byte array.
     * @param in the file to copy from
     * @return the new byte array that has been copied to
     * @throws IOException in case of I/O errors
     */
    public static byte[] copyToByteArray(File in) throws IOException {
        assert in != null : "No input File specified";

        return copyToByteArray(new BufferedInputStream(new FileInputStream(in)));
    }

    //---------------------------------------------------------------------
    // Copy methods for java.io.InputStream / java.io.OutputStream
    //---------------------------------------------------------------------

    /**
     * Copy the contents of the given InputStream to the given OutputStream.
     * Closes both streams when done.
     * @param in the stream to copy from
     * @param out the stream to copy to
     * @return the number of bytes copied
     * @throws IOException in case of I/O errors
     */
    public static int copy(InputStream in, OutputStream out) throws IOException {
        assert in != null : "No input stream specified";
        assert out != null : "No output stream specified";
        try {
            int byteCount = 0;
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead = -1;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                byteCount += bytesRead;
            }
            out.flush();
            return byteCount;
        }
        finally {
            try {
                in.close();
            }
            catch (IOException ex) {
            }
            try {
                out.close();
            }
            catch (IOException ex) {
            }
        }
    }

    /**
     * Copy the contents of the given byte array to the given OutputStream.
     * Closes the stream when done.
     * @param in the byte array to copy from
     * @param out the OutputStream to copy to
     * @throws IOException in case of I/O errors
     */
    public static void copy(byte[] in, OutputStream out) throws IOException {
        assert in != null : "No input byte array specified";
        assert out != null : "No output stream specified";
        try {
            out.write(in);
        }
        finally {
            try {
                out.close();
            }
            catch (IOException ex) {
            }
        }
    }

    /**
     * Copy the contents of the given InputStream into a new byte array.
     * Closes the stream when done.
     * @param in the stream to copy from
     * @return the new byte array that has been copied to
     * @throws IOException in case of I/O errors
     */
    public static byte[] copyToByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(BUFFER_SIZE);
        copy(in, out);
        return out.toByteArray();
    }

    //---------------------------------------------------------------------
    // Copy methods for java.io.Reader / java.io.Writer
    //---------------------------------------------------------------------

    /**
     * Copy the contents of the given Reader to the given Writer.
     * Closes both when done.
     * @param in the Reader to copy from
     * @param out the Writer to copy to
     * @return the number of characters copied
     * @throws IOException in case of I/O errors
     */
    public static int copy(Reader in, Writer out) throws IOException {
        assert in != null : "No input Reader specified";
        assert out != null : "No output Writer specified";

        try {
            int byteCount = 0;
            char[] buffer = new char[BUFFER_SIZE];
            int bytesRead = -1;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                byteCount += bytesRead;
            }
            out.flush();
            return byteCount;
        }
        finally {
            try {
                in.close();
            }
            catch (IOException ex) {
            }
            try {
                out.close();
            }
            catch (IOException ex) {
            }
        }
    }

    /**
     * Copy the contents of the given String to the given output Writer.
     * Closes the write when done.
     * @param in the String to copy from
     * @param out the Writer to copy to
     * @throws IOException in case of I/O errors
     */
    public static void copy(String in, Writer out) throws IOException {
        assert in != null : "No input String specified";
        assert out != null : "No output Writer specified";

        try {
            out.write(in);
        }
        finally {
            try {
                out.close();
            }
            catch (IOException ex) {
            }
        }
    }

    /**
     * Closes a closeable gracefully without throwing exceptions etc.
     *
     * @param closeable The closeable
     */
    public static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null)
                closeable.close();
        } catch (IOException e) {
            // ignore
        }
    }

    /**
     * Copy the contents of the given Reader into a String.
     * Closes the reader when done.
     * @param in the reader to copy from
     * @return the String that has been copied to
     * @throws IOException in case of I/O errors
     */
    public static String copyToString(Reader in) throws IOException {
        StringWriter out = new StringWriter();
        copy(in, out);
        return out.toString();
    }

    /**
     * Creates an {@link XmlSlurper} for untrusted documents such as HTTP request bodies.
     *
     * <p>The parser refuses a {@code DOCTYPE} declaration outright, on top of the entity and DTD
     * hardening every parser handed out by this class applies. Readers of trusted descriptors that
     * declare one use {@link #createXmlSlurper(boolean)} with {@code true} instead.
     *
     * @return a namespace-aware, non-validating slurper that rejects {@code DOCTYPE} declarations
     */
    public static XmlSlurper createXmlSlurper() throws ParserConfigurationException, SAXException {
        return createXmlSlurper(false);
    }

    /**
     * Creates an {@link XmlSlurper}, optionally tolerating a {@code DOCTYPE} declaration.
     *
     * <p>Documents parsed through this class fall into two trust levels, and each level gets its
     * own parser. Request bodies are untrusted and are refused if they declare a {@code DOCTYPE};
     * that is the {@code false} form and the default. Descriptors read from the application
     * classpath, such as JSP tag library descriptors and {@code web.xml}, are trusted and routinely
     * declare one, so their readers pass {@code true}.
     *
     * <p>Tolerating the declaration does not reopen the XXE vector. External general entities,
     * external parameter entities, DTD grammar loading and external DTD retrieval stay off on both
     * parsers, so an entity that points at a file on disk contributes nothing and the DTD a
     * document names is skipped rather than fetched.
     *
     * @param allowDocTypeDeclaration {@code true} to parse documents that declare a {@code DOCTYPE}
     * @return a namespace-aware, non-validating slurper with the hardening described above
     * @since 8.0.0
     */
    public static XmlSlurper createXmlSlurper(boolean allowDocTypeDeclaration) throws ParserConfigurationException, SAXException {
        return new XmlSlurper(newSAXParser(allowDocTypeDeclaration));
    }

    /**
     * Creates a {@link SAXParser} for untrusted documents such as HTTP request bodies.
     *
     * <p>The parser refuses a {@code DOCTYPE} declaration outright; see
     * {@link #createXmlSlurper(boolean)} for the two trust levels.
     *
     * @return a namespace-aware, non-validating parser that rejects {@code DOCTYPE} declarations
     */
    public static SAXParser newSAXParser() throws ParserConfigurationException, SAXException {
        return newSAXParser(false);
    }

    /**
     * Creates a {@link SAXParser}, optionally tolerating a {@code DOCTYPE} declaration.
     *
     * <p>Applies the same hardening as {@link #createXmlSlurper(boolean)}.
     *
     * @param allowDocTypeDeclaration {@code true} to parse documents that declare a {@code DOCTYPE}
     * @return a namespace-aware, non-validating parser
     * @since 8.0.0
     */
    public static SAXParser newSAXParser(boolean allowDocTypeDeclaration) throws ParserConfigurationException, SAXException {
        return parserFactory(allowDocTypeDeclaration).newSAXParser();
    }

    /**
     * Parser features switched off for every parser this class hands out.
     *
     * <p>{@link XmlParserFeature#DISALLOW_DOCTYPE_DECL} is handled separately because it is the
     * one feature that differs between the two parsers; see {@link #createXmlSlurper(boolean)}.
     */
    private static final XmlParserFeature[] DISABLED_PARSER_FEATURES = {
        XmlParserFeature.EXTERNAL_GENERAL_ENTITIES,
        XmlParserFeature.EXTERNAL_PARAMETER_ENTITIES,
        XmlParserFeature.LOAD_DTD_GRAMMAR,
        XmlParserFeature.LOAD_EXTERNAL_DTD
    };

    private static volatile SAXParserFactory strictParserFactory;

    private static volatile SAXParserFactory docTypeParserFactory;

    private static SAXParserFactory parserFactory(boolean allowDocTypeDeclaration) throws ParserConfigurationException {
        if (allowDocTypeDeclaration) {
            SAXParserFactory factory = docTypeParserFactory;
            if (factory == null) {
                factory = buildParserFactory(true);
                docTypeParserFactory = factory;
            }
            return factory;
        }
        SAXParserFactory factory = strictParserFactory;
        if (factory == null) {
            factory = buildParserFactory(false);
            strictParserFactory = factory;
        }
        return factory;
    }

    private static SAXParserFactory buildParserFactory(boolean allowDocTypeDeclaration) throws ParserConfigurationException {
        SAXParserFactory factory = FactorySupport.createSaxParserFactory();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        try {
            factory.setXIncludeAware(false);
        } catch (UnsupportedOperationException e) {
            // a parser without XInclude support cannot expand an include either
        }
        setFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setFeature(factory, XmlParserFeature.DISALLOW_DOCTYPE_DECL.getFeatureName(), !allowDocTypeDeclaration);
        for (XmlParserFeature feature : DISABLED_PARSER_FEATURES) {
            setFeature(factory, feature.getFeatureName(), false);
        }
        return factory;
    }

    /**
     * Sets a feature, tolerating a parser that lacks it.
     *
     * <p>The tolerance keeps this class usable with any SAX provider, but it is also how an
     * unrecognised feature identifier once switched the hardening off without a trace. A parser
     * that rejects a feature is therefore reported rather than ignored. The logger is looked up
     * here rather than held in a static field because this class is used before logging is
     * configured during startup.
     */
    private static void setFeature(SAXParserFactory factory, String name, boolean value) {
        try {
            factory.setFeature(name, value);
        } catch (ParserConfigurationException | SAXException e) {
            LoggerFactory.getLogger(SpringIOUtils.class).warn(
                    "XML parser factory [{}] does not support feature [{}]: {}",
                    factory.getClass().getName(), name, e.getMessage());
        }
    }
}
