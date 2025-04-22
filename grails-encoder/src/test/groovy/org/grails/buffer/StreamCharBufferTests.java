/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.buffer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;

import grails.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Unit tests for StreamCharBuffer.
 *
 * @author Lari Hotari, Sagire Software Oy
 */
public class StreamCharBufferTests {
    private static final int TESTROUNDS = 1;
    static char[] testbuffer = new char[Short.MAX_VALUE];

    @BeforeEach
    protected void setUp() throws Exception {
        if (testbuffer == null) {
            for (int i = 0; i < TESTROUNDS; i++) {
                for (int j = 0; j < Short.MAX_VALUE; j++) {
                    testbuffer[i * Short.MAX_VALUE + j] = (char) j;
                }
            }
        }
    }

    @Test
    public void testEquals() throws Exception {
        StreamCharBuffer charBuffer = new StreamCharBuffer();
        Writer writer=charBuffer.getWriter();
        writer.write("ABCDE");
        writer.flush();

        assertEquals(charBuffer,"ABCDE");
        assertEquals(charBuffer.hashCode(), "ABCDE".hashCode());
        assertFalse(charBuffer.equals("odifjdsof"));
        StreamCharBuffer charBuffer2 = new StreamCharBuffer();
        Writer writer2=charBuffer2.getWriter();
        writer2.write("ABCDE");
        writer2.flush();

        assertEquals(charBuffer,charBuffer2);
        assertEquals(charBuffer.hashCode(),charBuffer2.hashCode());
    }

    @Test
    public void testSerialization() throws Exception {
        StreamCharBuffer charBuffer = new StreamCharBuffer();
        Writer writer=charBuffer.getWriter();
        writer.write("ABCDE");
        writer.flush();

        // Serialize to a byte array
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(charBuffer);
        out.close();

        byte[] bytes = bos.toByteArray();
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));

        charBuffer = (StreamCharBuffer) in.readObject();

        assertEquals("ABCDE", charBuffer.toString());
        assertEquals(5, charBuffer.size());

        charBuffer.getWriter().write("12345");
        assertEquals("ABCDE12345", charBuffer.toString());
        assertEquals(10, charBuffer.size());
    }

    @Test
    public void testBufferedConnectedStringWriting() throws IOException {
        StreamCharBuffer charBuffer = new StreamCharBuffer(10,0,10);
        charBuffer.setSubStringChunkMinSize(6);
        charBuffer.setWriteDirectlyToConnectedMinSize(6);
        doBufferedTesting(charBuffer);
    }

    @Test
    public void testBufferedConnectedStringWritingResizeableChunkSize() throws IOException {
        StreamCharBuffer charBuffer = new StreamCharBuffer(10);
        charBuffer.setSubStringChunkMinSize(6);
        charBuffer.setWriteDirectlyToConnectedMinSize(6);
        doBufferedTesting(charBuffer);
    }

    private void doBufferedTesting(StreamCharBuffer charBuffer) throws IOException {
        StringWriter sw=new StringWriter();
        charBuffer.connectTo(sw);
        Writer writer=charBuffer.getWriter();
        writer.write("ABCDE");
        writer.write("12345".toCharArray());
        writer.write("A");
        assertEquals("ABCDE12345", sw.toString());
        writer.write("1234567");
        writer.flush();
        if (StringCharArrayAccessor.isEnabled()) {
            assertEquals("ABCDE12345A1234567", sw.toString());
        }
        writer.write("ABCDE");
        writer.write("67890".toCharArray());
        writer.close();
        assertEquals("ABCDE12345A1234567ABCDE67890", sw.toString());
        assertEquals(0, charBuffer.size());
    }

    @Test
    public void testSubStreamCharBuffer() throws IOException {
        StreamCharBuffer charBuffer = new StreamCharBuffer();
        Writer writer=charBuffer.getWriter();
        writer.write("ABCDE");
        writer.write("12345".toCharArray());
        writer.write("!");
        StreamCharBuffer charBuffer2 = new StreamCharBuffer(3);
        charBuffer2.setPreferSubChunkWhenWritingToOtherBuffer(true);
        Writer writer2=charBuffer2.getWriter();
        writer2.write(">OOOOO<");
        charBuffer2.writeTo(writer);
        writer.write("1234567");
        writer.write("ABCDE");
        writer.write("67890".toCharArray());
        writer.close();
        assertEquals("ABCDE12345!>OOOOO<1234567ABCDE67890", IOUtils.toString(charBuffer.getReader()));
        assertEquals("ABCDE12345!>OOOOO<1234567ABCDE67890", IOUtils.toString(charBuffer.getReader()));
        assertEquals(35, charBuffer.size());
        writer2.write("-----");
        assertEquals(35, charBuffer.size());
        writer2.flush();
        assertEquals(40, charBuffer.size());
        assertEquals("ABCDE12345!>OOOOO<-----1234567ABCDE67890", charBuffer.toString());
        assertEquals("ABCDE12345!>OOOOO<-----1234567ABCDE67890", IOUtils.toString(charBuffer.getReader()));
    }

    @Test
    public void testReaderWithStringArrays() throws IOException {
        StreamCharBuffer charBuffer = new StreamCharBuffer(10);
        charBuffer.setSubStringChunkMinSize(6);
        Writer writer=charBuffer.getWriter();
        writer.write("ABCDE");
        writer.write("12345".toCharArray());
        writer.write("!");
        writer.write("1234567");
        writer.write("ABCDE");
        writer.write("67890".toCharArray());
        writer.close();
        assertEquals("ABCDE12345!1234567ABCDE67890", IOUtils.toString(charBuffer.getReader()));
        assertEquals("ABCDE12345!1234567ABCDE67890", IOUtils.toString(charBuffer.getReader()));
        assertEquals(28, charBuffer.size());
    }

    @Test
    public void testStringCharArrays() throws IOException {
        StreamCharBuffer charBuffer = new StreamCharBuffer();
        Writer writer=charBuffer.getWriter();
        writer.write("ABCDE");
        writer.write("12345".toCharArray());
        writer.write("ABCDE");
        writer.write("ABCDE");
        writer.write("67890".toCharArray());
        writer.close();
        assertEquals(25, charBuffer.size());
        assertEquals("ABCDE12345ABCDEABCDE67890", charBuffer.toString());
        assertEquals(25, charBuffer.size());
    }

    @Test
    public void testWritingStringBuilder() throws IOException {
        StreamCharBuffer charBuffer = new StreamCharBuffer(10,0,10);
        Writer writer=charBuffer.getWriter();
        StringBuilder sb=new StringBuilder("ABCDE12345ABCDEABCDE67890");
        writer.append(sb);
        writer.close();
        assertEquals(25, charBuffer.size());
        assertEquals("ABCDE12345ABCDEABCDE67890", charBuffer.toString());

        assertEquals(25, charBuffer.size());
    }

    @Test
    public void testWritingStringBuffer() throws IOException {
        StreamCharBuffer charBuffer = new StreamCharBuffer(10,0,10);
        Writer writer=charBuffer.getWriter();
        StringBuffer sb=new StringBuffer("ABCDE12345ABCDEABCDE67890");
        writer.append(sb);
        writer.close();
        assertEquals(25, charBuffer.size());
        assertEquals("ABCDE12345ABCDEABCDE67890", charBuffer.toString());

        assertEquals(25, charBuffer.size());
    }

    @Test
    public void testWritingStringBuilder2() throws IOException {
        StreamCharBuffer charBuffer = new StreamCharBuffer(100,0,100);
        Writer writer=charBuffer.getWriter();
        StringBuilder sb=new StringBuilder("ABCDE12345ABCDEABCDE67890");
        writer.append(sb);
        writer.close();
        assertEquals(25, charBuffer.size());
        assertEquals("ABCDE12345ABCDEABCDE67890", charBuffer.toString());

        assertEquals(25, charBuffer.size());
    }

    @Test
    public void testWritingStringBuffer2() throws IOException {
        StreamCharBuffer charBuffer = new StreamCharBuffer(100,0,100);
        Writer writer=charBuffer.getWriter();
        StringBuffer sb=new StringBuffer("ABCDE12345ABCDEABCDE67890");
        writer.append(sb);
        writer.close();
        assertEquals(25, charBuffer.size());
        assertEquals("ABCDE12345ABCDEABCDE67890", charBuffer.toString());

        assertEquals(25, charBuffer.size());
    }

    @Test
    public void testStringCharArraysWriteTo() throws IOException {
        StreamCharBuffer charBuffer = new StreamCharBuffer();
        charBuffer.setSubStringChunkMinSize(0);
        Writer writer=charBuffer.getWriter();
        writer.write("ABCDE");
        writer.write("12345".toCharArray());
        writer.write("ABCDE");
        writer.write("ABCDE");
        writer.write("67890".toCharArray());
        writer.close();
        assertEquals(25, charBuffer.size());
        StringWriter sw=new StringWriter();
        charBuffer.writeTo(sw);
        assertEquals("ABCDE12345ABCDEABCDE67890", sw.toString());

        assertEquals(25, charBuffer.size());
    }

    @Test
    public void testStringCharArraysWriteTo2() throws IOException {
        StreamCharBuffer charBuffer = new StreamCharBuffer();
        charBuffer.setSubStringChunkMinSize(10000);
        Writer writer=charBuffer.getWriter();
        writer.write("ABCDE");
        writer.write("12345".toCharArray());
        writer.write("ABCDE");
        writer.write("ABCDE");
        writer.write("67890".toCharArray());
        writer.close();
        assertEquals(25, charBuffer.size());
        StringWriter sw=new StringWriter();
        charBuffer.writeTo(sw);
        assertEquals("ABCDE12345ABCDEABCDE67890", sw.toString());

        assertEquals(25, charBuffer.size());
    }

    @Test
    public void testToString() throws IOException {
        StreamCharBuffer charBuffer = new StreamCharBuffer();
        Writer writer=charBuffer.getWriter();
        writer.write("Hello world!");
        writer.close();
        //assertEquals(0, charBuffer.filledChunkCount());
        String str=charBuffer.toString();
        assertEquals("Hello world!", str);

        assertEquals(12, charBuffer.size());
    }

    @Test
    public void testToCharArray() throws IOException {
        StreamCharBuffer charBuffer = createTestInstance();
        char[] result = charBuffer.toCharArray();
        assertArrayEquals(testbuffer, result);

        assertEquals(testbuffer.length, charBuffer.size());
    }

    @Test
    public void testToReader() throws IOException {
        StreamCharBuffer charBuffer = createTestInstance();
        Reader input = charBuffer.getReader();
        CharArrayWriter charsOut = new CharArrayWriter(charBuffer
                .size());
        copy(input, charsOut, 2048);
        char[] result = charsOut.toCharArray();
        assertArrayEquals(testbuffer, result);

        assertEquals(testbuffer.length, charBuffer.size());
    }

    @Test
    public void testToReaderOneByOne() throws IOException {
        StreamCharBuffer charBuffer = createTestInstance();
        Reader input = charBuffer.getReader();
        CharArrayWriter charsOut = new CharArrayWriter(charBuffer
                .size());
        copyOneByOne(input, charsOut);
        char[] result = charsOut.toCharArray();
        assertTrue(Arrays.equals(testbuffer, result));

        assertEquals(testbuffer.length, charBuffer.size());
    }

    @Test
    public void testWriteTo() throws IOException {
        StreamCharBuffer charBuffer = createTestInstance();
        CharArrayWriter charsWriter = new CharArrayWriter(charBuffer
                .size());
        charBuffer.writeTo(charsWriter);
        char[] result = charsWriter.toCharArray();
        assertArrayEquals(testbuffer, result);

        assertEquals(testbuffer.length, charBuffer.size());
    }

    @Test
    public void testReadWrite() throws IOException {
        StreamCharBuffer buf=new StreamCharBuffer();
        Writer writer=buf.getWriter();
        Reader reader=buf.getReader();
        writer.write("12345");
        char[] b=new char[5];
        reader.read(b);
        assertEquals("12345", new String(b));
        StringWriter sw=new StringWriter();
        IOUtils.copy(reader, sw);
        assertEquals("", sw.toString());
        writer.write("12345");
        writer.write("12345");
        sw=new StringWriter();
        IOUtils.copy(reader, sw);
        assertEquals("1234512345", sw.toString());
    }

    @Test
    public void testReadWriteWithChunks() throws IOException {
        StreamCharBuffer buf=new StreamCharBuffer(5);
        Writer writer=buf.getWriter();
        Reader reader=buf.getReader();
        writer.write("12345");
        char[] b=new char[5];
        reader.read(b);
        assertEquals("12345", new String(b));
        StringWriter sw=new StringWriter();
        IOUtils.copy(reader, sw);
        assertEquals("", sw.toString());
        writer.write("12345");
        writer.write("12345");
        sw=new StringWriter();
        IOUtils.copy(reader, sw);
        assertEquals("1234512345", sw.toString());
    }

    @Test
    public void testReadWriteWithChunks2() throws IOException {
        StreamCharBuffer buf=new StreamCharBuffer(3);
        Writer writer=buf.getWriter();
        Reader reader=buf.getReader();
        writer.write("12345");
        char[] b=new char[5];
        reader.read(b);
        assertEquals("12345", new String(b));
        StringWriter sw=new StringWriter();
        IOUtils.copy(reader, sw);
        assertEquals("", sw.toString());
        writer.write("12345");
        writer.write("12345");
        sw=new StringWriter();
        IOUtils.copy(reader, sw);
        assertEquals("1234512345", sw.toString());
    }

    @Test
    public void testReadWriteWithChunks3() throws IOException {
        StreamCharBuffer buf=new StreamCharBuffer(3);
        Writer writer=buf.getWriter();
        Reader reader=buf.getReader();
        writer.write("12345");
        char[] b=new char[5];
        reader.read(b);
        assertEquals("12345", new String(b));
        StringWriter sw=new StringWriter();
        IOUtils.copy(reader, sw);
        assertEquals("", sw.toString());
        writer.write("12345");
        writer.write("12345");
        char[] b2=new char[8];
        reader.read(b2);
        assertEquals("12345123", new String(b2));
        int len=reader.read(b2);
        assertEquals("45", new String(b2, 0, len));
        // check that buffer isn't emptied by the reader
        assertEquals("123451234512345", buf.toString());
    }

    @Test
    public void testReadWriteWithChunksRemoveAfterReading() throws IOException {
        StreamCharBuffer buf=new StreamCharBuffer(3);
        doRemoveChunksAfterReadingTesting(buf);
    }

    @Test
    public void testIsEmptyAnsIsSizeLarger() throws IOException {
        StreamCharBuffer buf=new StreamCharBuffer(3);
        assertEquals(true, buf.isEmpty());
        assertEquals(false, buf.isSizeLarger(0));
        assertEquals(false, buf.isSizeLarger(4));
        Writer writer=buf.getWriter();
        writer.write("12345");
        assertEquals(true, buf.isSizeLarger(4));
        assertEquals(false, buf.isSizeLarger(5));
        assertEquals(false, buf.isSizeLarger(100));
        assertEquals(true, buf.isSizeLarger(0));
        assertEquals(false, buf.isEmpty());
    }

    private void doRemoveChunksAfterReadingTesting(StreamCharBuffer buf) throws IOException {
        Writer writer=buf.getWriter();
        Reader reader=buf.getReader(true);
        writer.write("12345");
        char[] b=new char[5];
        reader.read(b);
        assertEquals("12345", new String(b));
        StringWriter sw=new StringWriter();
        IOUtils.copy(reader, sw);
        assertEquals("", sw.toString());
        writer.write("12345");
        writer.write("12345");
        char[] b2=new char[8];
        reader.read(b2);
        assertEquals("12345123", new String(b2));
        int len=reader.read(b2);
        assertEquals("45", new String(b2, 0, len));
        // check that buffer is emptied by the reader
        assertEquals("", buf.toString());
    }

    @Test
    public void testReadWriteWithChunksRemoveAfterReading2() throws IOException {
        StreamCharBuffer buf=new StreamCharBuffer(3, 0);
        doRemoveChunksAfterReadingTesting(buf);
    }

    @Test
    public void testReadWriteWithChunksRemoveAfterReading3() throws IOException {
        StreamCharBuffer buf=new StreamCharBuffer(1, 0);
        doRemoveChunksAfterReadingTesting(buf);
    }

    @Test
    public void testReadWriteWithChunksRemoveAfterReading4() throws IOException {
        StreamCharBuffer buf=new StreamCharBuffer(1000, 0);
        doRemoveChunksAfterReadingTesting(buf);
    }

    @Test
    public void testReadWriteWithChunksRemoveAfterReading5() throws IOException {
        StreamCharBuffer buf=new StreamCharBuffer(5, 0);
        doRemoveChunksAfterReadingTesting(buf);
    }

    private int copy(Reader input, Writer output, int bufSize) throws IOException {
        char[] buffer = new char[bufSize];
        int count = 0;
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    private int copyOneByOne(Reader input, Writer output) throws IOException {
        int count = 0;
        int b;
        while (-1 != (b = input.read())) {
            output.write(b);
            count++;
        }
        return count;
    }

    private StreamCharBuffer createTestInstance() throws IOException {
        StreamCharBuffer charBuffer = new StreamCharBuffer();
        Writer output = charBuffer.getWriter();
        copyAllFromTestBuffer(output, 27);
        return charBuffer;
    }

    private void copyAllFromTestBuffer(Writer output, int partsize) throws IOException {
        int position = 0;
        int charsLeft = testbuffer.length;
        if (charsLeft < partsize) {
            partsize = charsLeft;
        }
        while (charsLeft > 0) {
            output.write(testbuffer, position, partsize);
            position += partsize;
            charsLeft -= partsize;
            if (charsLeft < partsize) {
                partsize = charsLeft;
            }
        }
    }
}
