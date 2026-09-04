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
package org.grails.taglib.index;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Properties;
import java.util.TreeSet;

/**
 * Writes the compile-time descriptor for a single tag library.
 *
 * <p>Two files are produced per tag library: a descriptor named after the tag library class, and an
 * entry in a shared {@code index.properties} manifest naming it. The manifest exists because a
 * classpath directory cannot be enumerated from inside a jar, so the reader needs the names up front.
 * Both live under {@link TagLibraryIndex#INDEX_LOCATION} and merge across jars without a build step.
 *
 * @since 8.0.0
 */
public final class TagLibraryIndexWriter {

    /**
     * Serialises the read-modify-write of the shared manifest across threads of this JVM.
     */
    private static final Object MANIFEST_MONITOR = new Object();

    private TagLibraryIndexWriter() {
    }

    /**
     * Removes any index previously written beneath a directory, so that a regenerated index describes
     * only the tag libraries that exist now. Without this a renamed or deleted tag library would keep
     * a descriptor, and the manifest naming it, until the build directory was cleaned.
     *
     * @param outputDirectory the directory the index is written beneath
     * @throws IOException if an existing index cannot be removed
     */
    public static void clear(File outputDirectory) throws IOException {
        if (outputDirectory == null) {
            return;
        }
        File indexDirectory = new File(outputDirectory, TagLibraryIndex.INDEX_LOCATION);
        File[] existing = indexDirectory.listFiles();
        if (existing == null) {
            return;
        }
        for (File file : existing) {
            if (file.isFile() && file.getName().endsWith(".properties")) {
                Files.deleteIfExists(file.toPath());
            }
        }
    }

    /**
     * Writes the descriptor for a tag library into a compiler output directory.
     *
     * @param outputDirectory the compilation target directory; nothing is written when {@code null}
     * @param className the binary name of the tag library
     * @param namespace the namespace the tag library declares
     * @param tagNames the tag names the tag library declares
     * @throws IOException if the descriptor cannot be written
     */
    public static void write(File outputDirectory, String className, String namespace,
            Collection<String> tagNames) throws IOException {
        if (outputDirectory == null || className == null || className.isEmpty() ||
                namespace == null || namespace.isEmpty()) {
            return;
        }
        File indexDirectory = new File(outputDirectory, TagLibraryIndex.INDEX_LOCATION);
        if (!indexDirectory.isDirectory() && !indexDirectory.mkdirs() && !indexDirectory.isDirectory()) {
            return;
        }

        Properties descriptor = new Properties();
        descriptor.setProperty(TagLibraryIndex.VERSION_KEY, String.valueOf(TagLibraryIndex.FORMAT_VERSION));
        descriptor.setProperty(TagLibraryIndex.NAMESPACE_KEY, namespace);
        descriptor.setProperty(TagLibraryIndex.CLASS_KEY, className);
        // Sorted so that recompiling unchanged sources produces byte-identical output, which keeps
        // the build reproducible and avoids spurious up-to-date checks failing downstream.
        descriptor.setProperty(TagLibraryIndex.TAGS_KEY, String.join(",", new TreeSet<>(tagNames)));
        store(new File(indexDirectory, className + ".properties"), descriptor);

        addToManifest(new File(indexDirectory, "index.properties"), className);
    }

    /**
     * Adds one class to the manifest naming every described tag library.
     *
     * <p>The manifest is shared by every tag library compiled into the same directory, and adding to
     * it is a read, a change and a write back. Two compilations writing to one directory at the same
     * time - joint compilation, or parallel tasks sharing an output - would otherwise interleave and
     * one would write back a copy that never saw the other's entry. The lost entry is silent: the
     * descriptor is there, nothing names it, so its tags simply resolve dynamically for evermore.
     *
     * <p>Guarded twice, because the two cases are different. The monitor covers threads in this JVM,
     * which is what joint compilation and a parallel Gradle task within one daemon are. The file lock
     * covers a second process, which a forked compiler or a second daemon is; it is advisory and only
     * held for the read-modify-write.
     *
     * @param manifest the manifest to add to
     * @param className the tag library to name in it
     * @throws IOException if the manifest cannot be read or written
     */
    private static void addToManifest(File manifest, String className) throws IOException {
        synchronized (MANIFEST_MONITOR) {
            try (FileChannel channel = FileChannel.open(manifest.toPath(),
                    StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                try (FileLock ignored = channel.lock()) {
                    Properties names = new Properties();
                    channel.position(0);
                    // Reads the channel rather than reopening the file, so the content read is the
                    // content the lock is held over.
                    byte[] existing = new byte[(int) channel.size()];
                    ByteBuffer buffer = ByteBuffer.wrap(existing);
                    while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
                        // read until the buffer is filled or the channel is exhausted
                    }
                    if (existing.length > 0) {
                        names.load(new InputStreamReader(new ByteArrayInputStream(existing),
                                StandardCharsets.UTF_8));
                    }
                    names.setProperty(className, "");
                    byte[] updated = render(names).getBytes(StandardCharsets.UTF_8);
                    channel.truncate(0);
                    channel.position(0);
                    channel.write(ByteBuffer.wrap(updated));
                }
            }
        }
    }

    /**
     * Records what could not be described, so that a call to a tag of an incompletely described
     * namespace is never reported as a misspelling.
     *
     * @param outputDirectory the directory the index is written beneath
     * @param namespaces the namespaces known to be missing some of their tags
     * @param everything true when what was missed could not be attributed to a namespace at all, in
     *        which case nothing in the index may be treated as complete
     * @throws IOException if the record cannot be written
     */
    public static void writeIncomplete(File outputDirectory, Collection<String> namespaces,
            boolean everything) throws IOException {
        if (outputDirectory == null) {
            return;
        }
        if (namespaces.isEmpty() && !everything) {
            return;
        }
        File indexDirectory = new File(outputDirectory, TagLibraryIndex.INDEX_LOCATION);
        if (!indexDirectory.isDirectory() && !indexDirectory.mkdirs() && !indexDirectory.isDirectory()) {
            return;
        }
        Properties recorded = new Properties();
        recorded.setProperty(TagLibraryIndex.INCOMPLETE_NAMESPACES_KEY,
                String.join(",", new TreeSet<>(namespaces)));
        recorded.setProperty(TagLibraryIndex.INCOMPLETE_ALL_KEY, String.valueOf(everything));
        store(new File(indexDirectory, "incomplete.properties"), recorded);
    }

    private static void store(File file, Properties properties) throws IOException {
        try (OutputStream out = Files.newOutputStream(file.toPath());
             Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writer.write(render(properties));
        }
    }

    /**
     * @param properties the entries to write
     * @return the properties as text, sorted and without the timestamp comment {@code Properties.store}
     *         stamps in, which would make otherwise identical builds differ
     */
    private static String render(Properties properties) {
        StringBuilder text = new StringBuilder();
        for (String key : new TreeSet<>(properties.stringPropertyNames())) {
            text.append(escape(key)).append('=').append(escape(properties.getProperty(key))).append('\n');
        }
        return text.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("=", "\\=").replace(":", "\\:");
    }
}
