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
package org.grails.forge.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility class for ZIP operations.
 *
 * @author graemerocher
 * @since 6.0.0
 */
public class ZipUtil {

    /**
     * Are the given bytes a zip file.
     * @param bytes The bytes
     * @return True if they are
     */
    public static boolean isZip(byte[] bytes) {
        if (bytes != null) {
            try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                ZipEntry nextEntry = zipInputStream.getNextEntry();
                return nextEntry != null;
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Does the specified zip bytes contain the specified file.
     * @param bytes The bytes
     * @param filename The file name
     * @return True if it does
     */
    public static boolean containsFile(byte[] bytes, String filename) {
        Objects.requireNonNull(bytes, "Byte cannot be null");
        Objects.requireNonNull(filename, "File name cannot be null");

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry nextEntry = zipInputStream.getNextEntry();
            while (nextEntry != null) {
                String name = nextEntry.getName();
                if (name.equals(filename)) {
                    return true;
                }
                nextEntry = zipInputStream.getNextEntry();
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Does the specified zip bytes contain the specified file.
     * @param bytes The bytes
     * @param filename The file name
     * @param contents The file contents
     * @return True if it does
     */
    public static boolean containsFileWithContents(byte[] bytes, String filename, String contents) {
        Objects.requireNonNull(bytes, "Byte cannot be null");
        Objects.requireNonNull(filename, "File name cannot be null");
        Objects.requireNonNull(contents, "Contents cannot be null");

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry nextEntry = zipInputStream.getNextEntry();
            while (nextEntry != null) {
                String name = nextEntry.getName();
                if (name.equals(filename)) {
                    String zipContents = readZipContents(zipInputStream);
                    return zipContents.contains(contents);
                }
                nextEntry = zipInputStream.getNextEntry();
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static String readZipContents(ZipInputStream zipInputStream) {
        Scanner sc = new Scanner(zipInputStream);
        StringBuilder builder = new StringBuilder();
        while (sc.hasNextLine()) {
            builder.append(sc.nextLine());
        }
        return builder.toString();
    }

}
