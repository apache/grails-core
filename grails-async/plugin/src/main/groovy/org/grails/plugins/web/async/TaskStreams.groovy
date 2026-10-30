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
 * live one until the request completes, and then throws an {@link IllegalStateException}.
 *
 * <p>A call holds the read lock of the request's {@link TaskRequestLifecycle}, so a read or write a task is making
 * when the request completes finishes first. Asking whether the body is ready and setting a read or write listener
 * do not, nor does reading the body once a read listener was set through a task's stream: the container may answer
 * them on the connection's own lock, which a thread completing the request may hold while it waits for the
 * lifecycle's lock. They are checked before and after instead, so a call the request completing overtook reports
 * it, whatever it returned or threw, and a listener set through them is no longer called once the request has
 * completed.</p>
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
            TaskInputStream stream = new TaskInputStream(lifecycle, (ServletInputStream) live, "$what body".toString())
            guarded = stream
            detachable = stream
        }
        else if (live instanceof BufferedReader) {
            TaskReader reader = new TaskReader(lifecycle, (BufferedReader) live, "$what body".toString())
            guarded = reader
            detachable = reader
        }
        else if (live instanceof ServletOutputStream) {
            TaskOutputStream stream = new TaskOutputStream(lifecycle, (ServletOutputStream) live, "$what output".toString())
            guarded = stream
            detachable = stream
        }
        else {
            TaskWriter writer = new TaskWriter(lifecycle, (PrintWriter) live, "$what output".toString())
            guarded = new TaskPrintWriter(writer)
            detachable = writer
        }
        lifecycle.attach(detachable)
        return guarded
    }

    private static Throwable failed(TaskRequestLifecycle lifecycle, String what, Throwable failure) {
        return lifecycle.overtaken(what, failure)
    }

    // A call on a body stream or reader, which may throw what reading the body throws
    @FunctionalInterface
    private static interface BodyCall<S, T> {

        T call(S body) throws IOException
    }

    // A read of the body: under the lock, so a read a task is making when the request completes finishes first,
    // unless a read listener was set through a task's stream. A nonblocking read asks whether more is ready, which
    // the container may answer on the connection's own lock, held by a thread completing the request while it waits
    // for the lifecycle's lock, so then it is checked before and after instead
    private static <S, T> T readBody(TaskRequestLifecycle lifecycle, S live, Object reader, String what, BodyCall<S, T> call)
            throws IOException {
        if (!lifecycle.readingWithoutLock) {
            S body = lifecycle.enter(live, what)
            try {
                return call.call(body)
            }
            catch (IOException | RuntimeException failure) {
                throw lifecycle.abandoned(what, failure)
            }
            finally {
                lifecycle.exit()
            }
        }
        S body = lifecycle.check(live, what)
        T result
        try {
            result = call.call(body)
        }
        catch (IOException | RuntimeException failure) {
            throw failed(lifecycle, what, failure)
        }
        lifecycle.check(reader, what)
        return result
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

        // Written out, as the reads a task makes most
        @Override
        int read() throws IOException {
            if (lifecycle.readingWithoutLock) {
                return readBody(lifecycle, live, this, what, (ServletInputStream body) -> body.read())
            }
            ServletInputStream stream = lifecycle.enter(live, what)
            try {
                return stream.read()
            }
            catch (IOException | RuntimeException failure) {
                throw lifecycle.abandoned(what, failure)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        int read(byte[] destination, int offset, int length) throws IOException {
            if (lifecycle.readingWithoutLock) {
                return readBody(lifecycle, live, this, what, (ServletInputStream body) -> body.read(destination, offset, length))
            }
            ServletInputStream stream = lifecycle.enter(live, what)
            try {
                return stream.read(destination, offset, length)
            }
            catch (IOException | RuntimeException failure) {
                throw lifecycle.abandoned(what, failure)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        int readLine(byte[] destination, int offset, int length) throws IOException {
            return readBody(lifecycle, live, this, what, (ServletInputStream body) -> body.readLine(destination, offset, length))
        }

        @Override
        long skip(long count) throws IOException {
            return readBody(lifecycle, live, this, what, (ServletInputStream body) -> body.skip(count))
        }

        @Override
        int available() throws IOException {
            return readBody(lifecycle, live, this, what, (ServletInputStream body) -> body.available())
        }

        @Override
        boolean markSupported() {
            return readBody(lifecycle, live, this, what, (ServletInputStream body) -> body.markSupported())
        }

        @Override
        void mark(int readLimit) {
            readBody(lifecycle, live, this, what, (ServletInputStream body) -> {
                body.mark(readLimit)
                return null
            })
        }

        @Override
        void reset() throws IOException {
            readBody(lifecycle, live, this, what, (ServletInputStream body) -> {
                body.reset()
                return null
            })
        }

        @Override
        void close() throws IOException {
            readBody(lifecycle, live, this, what, (ServletInputStream body) -> {
                body.close()
                return null
            })
        }

        @Override
        boolean isFinished() {
            return readBody(lifecycle, live, this, what, (ServletInputStream body) -> body.isFinished())
        }

        // Without the lock: at the end of the body, off a thread of the container, the container answers it on the
        // connection's own lock
        @Override
        boolean isReady() {
            ServletInputStream stream = lifecycle.check(live, what)
            boolean ready
            try {
                ready = stream.isReady()
            }
            catch (RuntimeException failure) {
                throw failed(lifecycle, what, failure)
            }
            lifecycle.check(this, what)
            return ready
        }

        // Without the lock, as the container answers it on the connection's own lock. From now on the body is read
        // without the lock too
        @Override
        void setReadListener(ReadListener readListener) {
            ServletInputStream stream = lifecycle.check(live, what)
            lifecycle.settingReadListener()
            boolean set = false
            try {
                stream.setReadListener(new TaskReadListener(lifecycle, readListener))
                set = true
            }
            catch (RuntimeException failure) {
                throw failed(lifecycle, what, failure)
            }
            finally {
                lifecycle.readListenerSet(set)
            }
            lifecycle.check(this, what)
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

        // Written out, as the reads a task makes most
        @Override
        int read() throws IOException {
            if (lifecycle.readingWithoutLock) {
                return readBody(lifecycle, live, this, what, (BufferedReader body) -> body.read())
            }
            BufferedReader reader = lifecycle.enter(live, what)
            try {
                return reader.read()
            }
            catch (IOException | RuntimeException failure) {
                throw lifecycle.abandoned(what, failure)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        int read(char[] destination, int offset, int length) throws IOException {
            if (lifecycle.readingWithoutLock) {
                return readBody(lifecycle, live, this, what, (BufferedReader body) -> body.read(destination, offset, length))
            }
            BufferedReader reader = lifecycle.enter(live, what)
            try {
                return reader.read(destination, offset, length)
            }
            catch (IOException | RuntimeException failure) {
                throw lifecycle.abandoned(what, failure)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        String readLine() throws IOException {
            if (lifecycle.readingWithoutLock) {
                return readBody(lifecycle, live, this, what, (BufferedReader body) -> body.readLine())
            }
            BufferedReader reader = lifecycle.enter(live, what)
            try {
                return reader.readLine()
            }
            catch (IOException | RuntimeException failure) {
                throw lifecycle.abandoned(what, failure)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        long skip(long count) throws IOException {
            return readBody(lifecycle, live, this, what, (BufferedReader body) -> body.skip(count))
        }

        @Override
        boolean ready() throws IOException {
            return readBody(lifecycle, live, this, what, (BufferedReader body) -> body.ready())
        }

        @Override
        boolean markSupported() {
            return readBody(lifecycle, live, this, what, (BufferedReader body) -> body.markSupported())
        }

        @Override
        void mark(int readAheadLimit) throws IOException {
            readBody(lifecycle, live, this, what, (BufferedReader body) -> {
                body.mark(readAheadLimit)
                return null
            })
        }

        @Override
        void reset() throws IOException {
            readBody(lifecycle, live, this, what, (BufferedReader body) -> {
                body.reset()
                return null
            })
        }

        @Override
        void close() throws IOException {
            readBody(lifecycle, live, this, what, (BufferedReader body) -> {
                body.close()
                return null
            })
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

        private ServletOutputStream before() {
            return lifecycle.check(live, what)
        }

        private void after() {
            lifecycle.check(this, what)
        }

        @Override
        void write(int b) throws IOException {
            ServletOutputStream stream = lifecycle.enter(live, what)
            try {
                stream.write(b)
            }
            catch (IOException | RuntimeException failure) {
                throw lifecycle.abandoned(what, failure)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        void write(byte[] source, int offset, int length) throws IOException {
            ServletOutputStream stream = lifecycle.enter(live, what)
            try {
                stream.write(source, offset, length)
            }
            catch (IOException | RuntimeException failure) {
                throw lifecycle.abandoned(what, failure)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        void flush() throws IOException {
            ServletOutputStream stream = lifecycle.enter(live, what)
            try {
                stream.flush()
            }
            catch (IOException | RuntimeException failure) {
                throw lifecycle.abandoned(what, failure)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        void close() throws IOException {
            ServletOutputStream stream = lifecycle.enter(live, what)
            try {
                stream.close()
            }
            catch (IOException | RuntimeException failure) {
                throw lifecycle.abandoned(what, failure)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        boolean isReady() {
            ServletOutputStream stream = lifecycle.enter(live, what)
            try {
                return stream.isReady()
            }
            catch (IOException | RuntimeException failure) {
                throw lifecycle.abandoned(what, failure)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        void setWriteListener(WriteListener writeListener) {
            ServletOutputStream stream = before()
            try {
                stream.setWriteListener(new TaskWriteListener(lifecycle, writeListener))
                after()
            }
            catch (RuntimeException failure) {
                throw failed(lifecycle, what, failure)
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

        @Override
        void write(int c) {
            PrintWriter writer = lifecycle.enter(live, what)
            try {
                writer.write(c)
            }
            catch (IOException | RuntimeException failure) {
                throw lifecycle.abandoned(what, failure)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        void write(char[] source, int offset, int length) {
            PrintWriter writer = lifecycle.enter(live, what)
            try {
                writer.write(source, offset, length)
            }
            catch (IOException | RuntimeException failure) {
                throw lifecycle.abandoned(what, failure)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        void write(String source, int offset, int length) {
            PrintWriter writer = lifecycle.enter(live, what)
            try {
                writer.write(source, offset, length)
            }
            catch (IOException | RuntimeException failure) {
                throw lifecycle.abandoned(what, failure)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        void flush() {
            PrintWriter writer = lifecycle.enter(live, what)
            try {
                writer.flush()
            }
            catch (IOException | RuntimeException failure) {
                throw lifecycle.abandoned(what, failure)
            }
            finally {
                lifecycle.exit()
            }
        }

        @Override
        void close() {
            PrintWriter writer = lifecycle.enter(live, what)
            try {
                writer.close()
            }
            catch (IOException | RuntimeException failure) {
                throw lifecycle.abandoned(what, failure)
            }
            finally {
                lifecycle.exit()
            }
        }

        @PackageScope
        boolean liveError() {
            PrintWriter writer = lifecycle.enter(live, what)
            try {
                return writer.checkError()
            }
            catch (IOException | RuntimeException failure) {
                throw lifecycle.abandoned(what, failure)
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

    // A listener a task set, which is no longer called once the request has completed, in case the container was
    // setting it on a request the request completing had overtaken
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

    // A write listener a task set, as {@link TaskReadListener} is for reading
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
