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
package org.grails.plugins.web.async

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener

/**
 * The guarded body streams and readers of a task's request and the guarded output of its response: each calls the
 * live one under the read lock of the request's {@link TaskRequestLifecycle}, until the request completes, and
 * then throws an {@link IllegalStateException}.
 */
@PackageScope
@CompileStatic
final class TaskStreams {

    private TaskStreams() {
    }

    /**
     * Guards what a live request or response returned.
     *
     * @param lifecycle the lifecycle of the request
     * @param live a ServletInputStream, BufferedReader, ServletOutputStream or PrintWriter
     * @param what what returned it, for the failure
     * @return the guarded one, attached to the lifecycle
     */
    static Object guard(TaskRequestLifecycle lifecycle, Object live, String what) {
        Object guarded
        TaskRequestLifecycle.Detachable detachable
        if (live instanceof ServletInputStream) {
            TaskInputStream stream = new TaskInputStream(lifecycle, (ServletInputStream) live, what)
            guarded = stream
            detachable = stream
        }
        else if (live instanceof BufferedReader) {
            TaskReader reader = new TaskReader(lifecycle, (BufferedReader) live, what)
            guarded = reader
            detachable = reader
        }
        else if (live instanceof ServletOutputStream) {
            TaskOutputStream stream = new TaskOutputStream(lifecycle, (ServletOutputStream) live, what)
            guarded = stream
            detachable = stream
        }
        else {
            TaskWriter writer = new TaskWriter(lifecycle, (PrintWriter) live, what)
            guarded = new TaskPrintWriter(writer)
            detachable = writer
        }
        lifecycle.attach(detachable)
        return guarded
    }

    private static final class TaskInputStream extends ServletInputStream implements TaskRequestLifecycle.Detachable {

        private final TaskRequestLifecycle lifecycle
        private final String what
        private volatile ServletInputStream live

        private TaskInputStream(TaskRequestLifecycle lifecycle, ServletInputStream live, String what) {
            this.lifecycle = lifecycle
            this.live = live
            this.what = what
        }

        @Override
        void detach() {
            live = null
        }

        private ServletInputStream enter() {
            return lifecycle.enter(live, "$what body".toString())
        }

        // After a call made without the lock, which the request completing may have overtaken: whatever the call did
        // or threw, it reports that the request has completed
        private void checkLive() {
            if (lifecycle.completed) {
                throw TaskRequestLifecycle.completedFailure("$what body".toString())
            }
        }

        @Override
        int read() throws IOException {
            ServletInputStream stream = enter()
            try {
                return stream.read()
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        int read(byte[] destination, int offset, int length) throws IOException {
            ServletInputStream stream = enter()
            try {
                return stream.read(destination, offset, length)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        int readLine(byte[] destination, int offset, int length) throws IOException {
            ServletInputStream stream = enter()
            try {
                return stream.readLine(destination, offset, length)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        long skip(long count) throws IOException {
            ServletInputStream stream = enter()
            try {
                return stream.skip(count)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        int available() throws IOException {
            ServletInputStream stream = enter()
            try {
                return stream.available()
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        boolean markSupported() {
            ServletInputStream stream = enter()
            try {
                return stream.markSupported()
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        void mark(int readLimit) {
            ServletInputStream stream = enter()
            try {
                stream.mark(readLimit)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        void reset() throws IOException {
            ServletInputStream stream = enter()
            try {
                stream.reset()
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        void close() throws IOException {
            ServletInputStream stream = enter()
            try {
                stream.close()
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        boolean isFinished() {
            ServletInputStream stream = enter()
            try {
                return stream.isFinished()
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        boolean isReady() {
            ServletInputStream stream = enter()
            lifecycle.exit()
            // Without the lock, as the container may answer it on the connection's own lock
            try {
                boolean ready = stream.isReady()
                checkLive()
                return ready
            }
            catch (RuntimeException failure) {
                checkLive()
                throw failure
            }
        }

        @Override
        void setReadListener(ReadListener readListener) {
            ServletInputStream stream = enter()
            lifecycle.exit()
            // Called without the lock: the container answers it on the connection's own lock, which a thread
            // completing the request may hold while it waits for the lock
            try {
                stream.setReadListener(new TaskReadListener(lifecycle, readListener))
                checkLive()
            }
            catch (RuntimeException failure) {
                checkLive()
                throw failure
            }
        }
    }

    private static final class TaskReader extends BufferedReader implements TaskRequestLifecycle.Detachable {

        private final TaskRequestLifecycle lifecycle
        private final String what
        private volatile BufferedReader live

        private TaskReader(TaskRequestLifecycle lifecycle, BufferedReader live, String what) {
            super(Reader.nullReader())
            this.lifecycle = lifecycle
            this.live = live
            this.what = what
        }

        @Override
        void detach() {
            live = null
        }

        private BufferedReader enter() {
            return lifecycle.enter(live, "$what body".toString())
        }

        @Override
        int read() throws IOException {
            BufferedReader reader = enter()
            try {
                return reader.read()
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        int read(char[] destination, int offset, int length) throws IOException {
            BufferedReader reader = enter()
            try {
                return reader.read(destination, offset, length)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        String readLine() throws IOException {
            BufferedReader reader = enter()
            try {
                return reader.readLine()
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        long skip(long count) throws IOException {
            BufferedReader reader = enter()
            try {
                return reader.skip(count)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        boolean ready() throws IOException {
            BufferedReader reader = enter()
            try {
                return reader.ready()
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        boolean markSupported() {
            BufferedReader reader = enter()
            try {
                return reader.markSupported()
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        void mark(int readAheadLimit) throws IOException {
            BufferedReader reader = enter()
            try {
                reader.mark(readAheadLimit)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        void reset() throws IOException {
            BufferedReader reader = enter()
            try {
                reader.reset()
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        void close() throws IOException {
            BufferedReader reader = enter()
            try {
                reader.close()
            }
            finally {
                lifecycle.exit()
            }
        }
    }

    private static final class TaskOutputStream extends ServletOutputStream implements TaskRequestLifecycle.Detachable {

        private final TaskRequestLifecycle lifecycle
        private final String what
        private volatile ServletOutputStream live

        private TaskOutputStream(TaskRequestLifecycle lifecycle, ServletOutputStream live, String what) {
            this.lifecycle = lifecycle
            this.live = live
            this.what = what
        }

        @Override
        void detach() {
            live = null
        }

        private ServletOutputStream enter() {
            return lifecycle.enter(live, "$what output".toString())
        }

        // After a call made without the lock, which the request completing may have overtaken: whatever the call did
        // or threw, it reports that the request has completed
        private void checkLive() {
            if (lifecycle.completed) {
                throw TaskRequestLifecycle.completedFailure("$what output".toString())
            }
        }

        @Override
        void write(int b) throws IOException {
            ServletOutputStream stream = enter()
            try {
                stream.write(b)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        void write(byte[] source, int offset, int length) throws IOException {
            ServletOutputStream stream = enter()
            try {
                stream.write(source, offset, length)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        void flush() throws IOException {
            ServletOutputStream stream = enter()
            try {
                stream.flush()
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        void close() throws IOException {
            ServletOutputStream stream = enter()
            try {
                stream.close()
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        boolean isReady() {
            ServletOutputStream stream = enter()
            lifecycle.exit()
            // Without the lock, as the container may answer it on the connection's own lock
            try {
                boolean ready = stream.isReady()
                checkLive()
                return ready
            }
            catch (RuntimeException failure) {
                checkLive()
                throw failure
            }
        }

        @Override
        void setWriteListener(WriteListener writeListener) {
            ServletOutputStream stream = enter()
            lifecycle.exit()
            // Called without the lock: the container answers it on the connection's own lock, which a thread
            // completing the request may hold while it waits for the lock
            try {
                stream.setWriteListener(new TaskWriteListener(lifecycle, writeListener))
                checkLive()
            }
            catch (RuntimeException failure) {
                checkLive()
                throw failure
            }
        }
    }

    // What a task's PrintWriter writes through: the live writer, which reports its own errors through checkError
    private static final class TaskWriter extends Writer implements TaskRequestLifecycle.Detachable {

        private final TaskRequestLifecycle lifecycle
        private final String what
        private volatile PrintWriter live

        private TaskWriter(TaskRequestLifecycle lifecycle, PrintWriter live, String what) {
            this.lifecycle = lifecycle
            this.live = live
            this.what = what
        }

        @Override
        void detach() {
            live = null
        }

        private PrintWriter enter() {
            return lifecycle.enter(live, "$what output".toString())
        }

        @Override
        void write(int c) {
            PrintWriter writer = enter()
            try {
                writer.write(c)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        void write(char[] source, int offset, int length) {
            PrintWriter writer = enter()
            try {
                writer.write(source, offset, length)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        void write(String source, int offset, int length) {
            PrintWriter writer = enter()
            try {
                writer.write(source, offset, length)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        void flush() {
            PrintWriter writer = enter()
            try {
                writer.flush()
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        void close() {
            PrintWriter writer = enter()
            try {
                writer.close()
            }
            finally {
                lifecycle.exit()
            }
        }

        @PackageScope
        boolean liveError() {
            PrintWriter writer = enter()
            try {
                return writer.checkError()
            }
            finally {
                lifecycle.exit()
            }
        }
    }

    // A PrintWriter swallows what an IOException its writer throws, but the failure of a completed request is an
    // IllegalStateException, which it passes on
    private static final class TaskPrintWriter extends PrintWriter {

        private final TaskWriter writer

        private TaskPrintWriter(TaskWriter writer) {
            super(writer)
            this.writer = writer
        }

        @Override
        boolean checkError() {
            return super.checkError() || writer.liveError()
        }
    }

    // A listener a task set without the lock, which is no longer called once the request has completed, in case the
    // container was setting it on a request it had already recycled
    private static final class TaskReadListener implements ReadListener {

        private final TaskRequestLifecycle lifecycle
        private final ReadListener listener

        @PackageScope
        TaskReadListener(TaskRequestLifecycle lifecycle, ReadListener listener) {
            this.lifecycle = lifecycle
            this.listener = listener
        }

        @Override
        void onDataAvailable() throws IOException {
            if (!lifecycle.completed) {
                listener.onDataAvailable()
            }
        }

        @Override
        void onAllDataRead() throws IOException {
            if (!lifecycle.completed) {
                listener.onAllDataRead()
            }
        }

        @Override
        void onError(Throwable failure) {
            if (!lifecycle.completed) {
                listener.onError(failure)
            }
        }
    }

    // A write listener a task set without the lock, as {@link TaskReadListener} is for reading
    private static final class TaskWriteListener implements WriteListener {

        private final TaskRequestLifecycle lifecycle
        private final WriteListener listener

        @PackageScope
        TaskWriteListener(TaskRequestLifecycle lifecycle, WriteListener listener) {
            this.lifecycle = lifecycle
            this.listener = listener
        }

        @Override
        void onWritePossible() throws IOException {
            if (!lifecycle.completed) {
                listener.onWritePossible()
            }
        }

        @Override
        void onError(Throwable failure) {
            if (!lifecycle.completed) {
                listener.onError(failure)
            }
        }
    }
}
