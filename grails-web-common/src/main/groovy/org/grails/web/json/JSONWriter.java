/*
Public Domain.
*/

package org.grails.web.json;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.Stack;

import groovy.lang.Writable;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;

import static org.grails.web.json.JSONWriter.Mode.ARRAY;
import static org.grails.web.json.JSONWriter.Mode.DONE;
import static org.grails.web.json.JSONWriter.Mode.INIT;
import static org.grails.web.json.JSONWriter.Mode.KEY;
import static org.grails.web.json.JSONWriter.Mode.OBJECT;

/**
 * JSONWriter provides a quick and convenient way of producing JSON text.
 * The texts produced strictly conform to JSON syntax rules. Each instance of
 * JSONWriter can produce one JSON text.
 * <p>
 * A JSONWriter instance provides a <code>value</code> method for appending
 * values to the
 * text, and a <code>key</code>
 * method for adding keys before values in objects. There are <code>array</code>
 * and <code>endArray</code> methods that make and bound array values, and
 * <code>object</code> and <code>endObject</code> methods which make and bound
 * object values. All of these methods return the JSONWriter instance,
 * permitting a cascade style. For example, <pre>
 * new JSONWriter(myWriter)
 *     .object()
 *         .key("JSON")
 *         .value("Hello, World!")
 *     .endObject();</pre> which writes <pre>
 * {"JSON":"Hello, World!"}</pre>
 * <p>
 * There are no methods for adding commas or colons. JSONWriter adds them for
 * you. A single value, outside any object or array, is also a JSON text.
 * <p>
 * The text is written by a Jackson {@link JsonGenerator}, from the {@link JsonMapperSupport JsonMapper} given, or
 * a default one: string escaping, numbers and pretty printing are Jackson's, and strings are also escaped for an HTML
 * {@code <script>} element (see {@link HtmlSafeJsonWriter}). A {@link JsonMapperValue} is written by its mapper. The
 * generator buffers the text, and writes it to the {@link Writer} as its buffer fills, once the JSON text is complete,
 * and on {@link #flush()}. It never flushes or closes the {@code Writer}.
 *
 * @author JSON.org
 * @version 3
 */
public class JSONWriter {

    /**
     * The comma flag determines if a comma should be output before the next
     * value.
     */
    protected boolean comma;

    /**
     * The current mode. Values:
     * 'a' (array),
     * 'd' (done),
     * 'i' (initial),
     * 'k' (key),
     * 'o' (object).
     */
    protected Mode mode;

    private Stack<Mode> stack = new Stack<>();

    private int nesting;

    /**
     * The writer that will receive the output.
     */
    protected Writer writer;

    private final JsonMapperSupport jsonMapper;

    private final JsonGenerator generator;

    /**
     * Makes a fresh JSONWriter, which writes with a default JsonMapper.
     *
     * @param w the writer to write the JSON text to
     */
    public JSONWriter(Writer w) {
        this(w, JsonMapperSupport.DEFAULT, false);
    }

    /**
     * Makes a fresh JSONWriter.
     *
     * @param w the writer to write the JSON text to
     * @param jsonMapper the mapper to write the JSON text with
     * @param prettyPrint whether to indent the JSON text with the mapper's default pretty printer
     * @since 9.0
     */
    public JSONWriter(Writer w, JsonMapperSupport jsonMapper, boolean prettyPrint) {
        this.comma = false;
        this.mode = INIT;
        this.writer = w;
        this.jsonMapper = jsonMapper;
        this.generator = w != null ? jsonMapper.createGenerator(w, prettyPrint) : null;
    }

    /**
     * Writes a raw JSON text as a value.
     *
     * @param s the JSON text
     * @return this
     */
    protected JSONWriter append(String s) {
        if (s == null) {
            throw new JSONException("Null pointer");
        }
        return write(() -> generator.writeRawValue(s), s);
    }

    /**
     * Writes the JSON text a {@link Writable} writes as a value.
     *
     * @param writableValue the value
     * @return this
     */
    protected JSONWriter append(Writable writableValue) {
        return write(() -> generator.writeRawValue(text(writableValue)), writableValue);
    }

    /**
     * The generator writes separators itself.
     */
    protected void comma() {
    }

    /**
     * Begin appending a new array. All values until the balancing
     * <code>endArray</code> will be appended to this array. The
     * <code>endArray</code> method must be called to mark the array's end.
     *
     * @return this
     * @throws JSONException If the nesting is too deep, or if the object is
     *                       started in the wrong place (for example as a key or after the end of the
     *                       outermost array or object).
     */
    public JSONWriter array() {
        if (this.mode == INIT || this.mode == OBJECT || this.mode == ARRAY) {
            this.push(ARRAY);
            generate(generator::writeStartArray);
            this.comma = false;
            return this;
        }
        throw new JSONException("Misplaced array: expected mode of INIT, OBJECT or ARRAY but was " + this.mode);
    }

    /**
     * End something.
     *
     * @param m Mode
     * @param c Closing character
     * @return this
     * @throws JSONException If unbalanced.
     */
    protected JSONWriter end(Mode m, char c) {
        if (this.mode != m) {
            throw new JSONException(m == OBJECT ? "Misplaced endObject." :
                    "Misplaced endArray.");
        }
        this.pop(m);
        generate(c == ']' ? generator::writeEndArray : generator::writeEndObject);
        this.comma = true;
        closeIfDone();
        return this;
    }

    /**
     * End an array. This method most be called to balance calls to
     * <code>array</code>.
     *
     * @return this
     * @throws JSONException If incorrectly nested.
     */
    public JSONWriter endArray() {
        return end(ARRAY, ']');
    }

    /**
     * End an object. This method most be called to balance calls to
     * <code>object</code>.
     *
     * @return this
     * @throws JSONException If incorrectly nested.
     */
    public JSONWriter endObject() {
        return end(KEY, '}');
    }

    /**
     * Append a key. The key will be associated with the next value. In an
     * object, every value must be preceded by a key.
     *
     * @param s A key string.
     * @return this
     * @throws JSONException If the key is out of place. For example, keys
     *                       do not belong in arrays or if the key is null.
     */
    public JSONWriter key(String s) {
        if (s == null) {
            throw new JSONException("Null key.");
        }
        if (this.mode == KEY) {
            generate(() -> generator.writeName(s));
            this.comma = false;
            this.mode = OBJECT;
            return this;
        }
        throw new JSONException("Misplaced key: expected mode of KEY but was " + this.mode);
    }

    /**
     * Begin appending a new object. All keys and values until the balancing
     * <code>endObject</code> will be appended to this object. The
     * <code>endObject</code> method must be called to mark the object's end.
     *
     * @return this
     * @throws JSONException If the nesting is too deep, or if the object is
     *                       started in the wrong place (for example as a key or after the end of the
     *                       outermost array or object).
     */
    public JSONWriter object() {
        if (this.mode == INIT) {
            this.mode = OBJECT;
        }
        if (this.mode == OBJECT || this.mode == ARRAY) {
            generate(generator::writeStartObject);
            if (this.mode == OBJECT) {
                this.mode = KEY;
            }
            this.push(KEY);
            this.comma = false;
            return this;
        }
        throw new JSONException("Misplaced object: expected mode of INIT, OBJECT or ARRAY but was " + this.mode);
    }

    /**
     * Pop an array or object scope.
     *
     * @param c The scope to close.
     * @throws JSONException If nesting is wrong.
     */
    protected void pop(Mode c) {
        if (this.stack.size() == 0 || this.stack.pop() != c) {
            throw new JSONException("Nesting error.");
        }
        if (this.stack.size() > 0)
            this.mode = this.stack.peek();
        else
            this.mode = DONE;
    }

    /**
     * Push an array or object scope.
     *
     * @param c The scope to open.
     * @throws JSONException If nesting is too deep.
     */
    protected void push(Mode c) {
        this.stack.push(c);
        this.mode = c;
    }

    /**
     * Append either the value <code>true</code> or the value
     * <code>false</code>.
     *
     * @param b A boolean.
     * @return this
     */
    public JSONWriter value(boolean b) {
        return write(() -> generator.writeBoolean(b), b);
    }

    /**
     * Append a double value.
     *
     * @param d A double.
     * @return this
     */
    public JSONWriter value(double d) {
        return write(() -> generator.writeNumber(d), d);
    }

    /**
     * Append a long value.
     *
     * @param l A long.
     * @return this
     */
    public JSONWriter value(long l) {
        return write(() -> generator.writeNumber(l), l);
    }

    /**
     * Append a number value.
     *
     * @param number A Number.
     * @return this
     */
    public JSONWriter value(Number number) {
        return number != null ? write(() -> writeNumber(number), number) : valueNull();
    }

    /**
     * Append the value <code>null</code>.
     *
     * @return this
     */
    public JSONWriter valueNull() {
        return write(generator::writeNull, null);
    }

    /**
     * Append an object value: a {@link JsonMapperValue} as its mapper writes it, {@code null} (and
     * {@link JSONObject#NULL}), numbers and booleans as JSON literals, a {@code Date} as a JavaScript
     * {@code new Date(...)} (for the javascript JSON date format), a {@link JSONElement} as the JSON text it writes, a
     * {@code String} as a JSON string, and any other value as the quoted, JSON encoded text of {@link JSONObject}.
     *
     * @param o The object to append.
     * @return this
     */
    public JSONWriter value(Object o) {
        if (o == null || o.equals(null)) {
            return valueNull();
        }
        if (o instanceof JsonMapperValue mapperValue && mapperValue.getJsonMapper() == jsonMapper) {
            return write(() -> jsonMapper.writeValue(generator, mapperValue.getValue(), mapperValue.getNestedValueWriter()), o);
        }
        if (o instanceof Number number) {
            return value(number);
        }
        if (o instanceof Boolean b) {
            return value(b.booleanValue());
        }
        if (o instanceof Date date) {
            return write(() -> generator.writeRawValue("new Date(" + date.getTime() + ")"), o);
        }
        if (o instanceof JSONElement element) {
            return write(() -> generator.writeRawValue(text(element)), o);
        }
        if (o.getClass() == String.class || o.getClass() == StringBuilder.class || o.getClass() == StringBuffer.class) {
            return write(() -> generator.writeString(o.toString()), o);
        }
        return write(() -> generator.writeRawValue(quoted(o)), o);
    }

    /**
     * Writes a value nested in a {@link JsonMapperValue} that this writer is writing, such as a value that a Jackson
     * serializer writes with {@link JsonGenerator#writePOJO(Object)}, from the value's nested value writer. The writer
     * accepts one value, as at the start of a JSON text, and then returns to the state it was in.
     *
     * @param writeValue writes one value to this writer
     * @throws JSONException if {@code writeValue} does not write one complete value
     * @since 9.0
     */
    public void writeNested(Runnable writeValue) {
        Mode outerMode = this.mode;
        Stack<Mode> outerStack = this.stack;
        boolean outerComma = this.comma;
        this.mode = INIT;
        this.stack = new Stack<>();
        this.nesting++;
        try {
            writeValue.run();
            if (this.mode != DONE) {
                throw new JSONException("Incomplete nested value: expected mode of DONE but was " + this.mode);
            }
        }
        finally {
            this.mode = outerMode;
            this.stack = outerStack;
            this.comma = outerComma;
            this.nesting--;
        }
    }

    /**
     * The location of the nested value that {@link #writeNested(Runnable)} is about to write, relative to the
     * {@link JsonMapperValue} it is nested in, such as {@code .inner} or {@code [0]}.
     */
    String nestedPath() {
        return generator != null ? NestedValueSerializer.nestedPath(generator) : "";
    }

    /**
     * Writes the JSON text buffered so far to the {@link Writer}, without flushing the {@code Writer}.
     *
     * @since 9.0
     */
    public void flush() {
        if (generator != null) {
            generate(generator::flush);
        }
    }

    private JSONWriter write(Runnable writeValue, Object value) {
        if (this.mode == INIT) {
            generate(writeValue);
            this.mode = DONE;
            closeIfDone();
            return this;
        }
        if (this.mode == OBJECT || this.mode == ARRAY) {
            generate(writeValue);
            if (this.mode == OBJECT) {
                this.mode = KEY;
            }
            this.comma = true;
            return this;
        }
        throw new JSONException("Value out of sequence: expected mode to be OBJECT or ARRAY when writing '" + value + "' but was " + this.mode);
    }

    /**
     * Once the JSON text is complete, closes the generator, which writes the rest of the text to the {@link Writer}
     * and returns its buffers to Jackson.
     */
    private void closeIfDone() {
        if (this.mode == DONE && this.nesting == 0) {
            generate(generator::close);
        }
    }

    private void writeNumber(Number number) {
        if (number instanceof Integer || number instanceof Short || number instanceof Byte) {
            generator.writeNumber(number.intValue());
        }
        else if (number instanceof Long) {
            generator.writeNumber(number.longValue());
        }
        else if (number instanceof Double) {
            generator.writeNumber(number.doubleValue());
        }
        else if (number instanceof Float) {
            generator.writeNumber(number.floatValue());
        }
        else if (number instanceof BigDecimal bigDecimal) {
            generator.writeNumber(bigDecimal);
        }
        else if (number instanceof BigInteger bigInteger) {
            generator.writeNumber(bigInteger);
        }
        else {
            jsonMapper.writeValue(generator, number);
        }
    }

    private static void generate(Runnable write) {
        try {
            write.run();
        }
        catch (JacksonException e) {
            throw new JSONException(e);
        }
    }

    private static String quoted(Object value) {
        StringWriter text = new StringWriter();
        try {
            JSONObject.writeQuoted(text, value);
        }
        catch (IOException e) {
            throw new JSONException(e);
        }
        return text.toString();
    }

    private static String text(Writable writable) {
        StringWriter text = new StringWriter();
        try {
            writable.writeTo(text);
        }
        catch (IOException e) {
            throw new JSONException(e);
        }
        return text.toString();
    }

    protected enum Mode {
        INIT,
        OBJECT,
        ARRAY,
        KEY,
        DONE
    }
}
