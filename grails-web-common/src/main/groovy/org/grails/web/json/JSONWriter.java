/*
Public Domain.
*/

package org.grails.web.json;

import java.io.IOException;
import java.io.Writer;
import java.util.Stack;

import groovy.lang.Writable;

import static org.grails.web.json.JSONWriter.Mode.ARRAY;
import static org.grails.web.json.JSONWriter.Mode.DONE;
import static org.grails.web.json.JSONWriter.Mode.INIT;
import static org.grails.web.json.JSONWriter.Mode.KEY;
import static org.grails.web.json.JSONWriter.Mode.OBJECT;

/**
 * JSONWriter provides a quick and convenient way of producing JSON text.
 * The texts produced strictly conform to JSON syntax rules. No whitespace is
 * added, so the results are ready for transmission or storage. Each instance of
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
 * The first method called must be <code>array</code> or <code>object</code>.
 * There are no methods for adding commas or colons. JSONWriter adds them for
 * you. Objects and arrays can be nested up to 20 levels deep.
 * <p>
 * This can sometimes be easier than using a JSONObject to build a string.
 *
 * @author JSON.org
 * @version 2
 */
public class JSONWriter {

    /**
     * The comma flag determines if a comma should be output before the next
     * value.
     */
    protected boolean comma;

    /**
     * The current mode. Values:
     */
    protected Mode mode;

    /**
     * The Mode stack.
     */
    private Stack<Mode> stack = new Stack<Mode>();

    /**
     * The writer that will receive the output.
     */
    protected Writer writer;

    /**
     * Make a fresh JSONWriter. It can be used to build one JSON text.
     */
    public JSONWriter(Writer w) {
        this.comma = false;
        this.mode = INIT;
        this.writer = w;
    }

    private static class WritableString implements Writable {
        private String string;

        WritableString(String string) {
            this.string = string;
        }

        @Override
        public Writer writeTo(Writer out) throws IOException {
            out.write(string);
            return out;
        }

        public String toString() {
            return string;
        }
    }

    /**
     * Append a value.
     * @param s A string value.
     * @return this
     */
    protected JSONWriter append(String s) {
        if (s == null) {
            throw new JSONException("Null pointer");
        }
        return append(new WritableString(s));
    }

    protected JSONWriter append(Writable writableValue) {
        if (this.mode == OBJECT || this.mode == ARRAY) {
            try {
                if (this.comma && this.mode == ARRAY) {
                    this.comma();
                }
                writableValue.writeTo(writer);
            } catch (IOException e) {
                throw new JSONException(e);
            }
            if (this.mode == OBJECT) {
                this.mode = KEY;
            }
            this.comma = true;
            return this;
        }
        throw new JSONException("Value out of sequence: expected mode to be OBJECT or ARRAY when writing '" + writableValue + "' but was " + this.mode);
    }

    protected void comma() {
        try {
            this.writer.write(',');
        } catch (IOException e) {
            throw new JSONException(e);
        }
    }

    /**
     * Begin appending a new array. All values until the balancing
     * <code>endArray</code> will be appended to this array. The
     * <code>endArray</code> method must be called to mark the array's end.
     *
     * @return this
     */
    public JSONWriter array() {
        if (this.mode == INIT || this.mode == OBJECT || this.mode == ARRAY) {
            this.push(ARRAY);
            this.append("[");
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
     */
    protected JSONWriter end(Mode m, char c) {
        if (this.mode != m) {
            throw new JSONException(m == OBJECT ? "Misplaced endObject." :
                    "Misplaced endArray.");
        }
        this.pop(m);
        try {
            this.writer.write(c);
        } catch (IOException e) {
            throw new JSONException(e);
        }
        this.comma = true;
        return this;
    }

    /**
     * End an array. This method most be called to balance calls to
     * <code>array</code>.
     *
     * @return this
     */
    public JSONWriter endArray() {
        return end(ARRAY, ']');
    }

    /**
     * End an object. This method most be called to balance calls to
     * <code>object</code>.
     *
     * @return this
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
     */
    public JSONWriter key(String s) {
        if (s == null) {
            throw new JSONException("Null key.");
        }
        if (this.mode == KEY) {
            try {
                if (this.comma) {
                    this.comma();
                }
                JSONObject.writeQuoted(this.writer, s);
                this.writer.write(':');
                this.comma = false;
                this.mode = OBJECT;
                return this;
            } catch (IOException e) {
                throw new JSONException(e);
            }
        }
        throw new JSONException("Misplaced key: expected mode of KEY but was " + this.mode);
    }


    /**
     * Begin appending a new object. All keys and values until the balancing
     * <code>endObject</code> will be appended to this object. The
     * <code>endObject</code> method must be called to mark the object's end.
     *
     * @return this
     */
    public JSONWriter object() {
        if (this.mode == INIT) {
            this.mode = OBJECT;
        }
        if (this.mode == OBJECT || this.mode == ARRAY) {
            this.append("{");
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
        return append(b ? "true" : "false");
    }

    /**
     * Append a double value.
     *
     * @param d A double.
     * @return this
     */
    public JSONWriter value(double d) {
        return value(Double.valueOf(d));
    }

    /**
     * Append a long value.
     *
     * @param l A long.
     * @return this
     */
    public JSONWriter value(long l) {
        return append(Long.toString(l));
    }

    /**
     * Append a number value
     *
     * @param number
     * @return
     */
    public JSONWriter value(Number number) {
        return number != null ? append(number.toString()) : valueNull();
    }

    public JSONWriter valueNull() {
        return append(nullWritable);
    }

    static Writable nullWritable = new NullWritable();

    private static class NullWritable implements Writable {
        @Override
        public Writer writeTo(Writer out) throws IOException {
            out.write("null");
            return out;
        }
    }

    /**
     * Append an object value.
     *
     * @param o The object to append. It can be null, or a Boolean, Number,
     *          String, JSONObject, or JSONArray.
     * @return this
     */
    public JSONWriter value(Object o) {
        return  o != null ? append(new QuotedWritable(o)) : valueNull();
    }

    private static class QuotedWritable implements Writable {
        Object o;

        QuotedWritable(Object o) {
            this.o = o;
        }

        @Override
        public Writer writeTo(Writer out) throws IOException {
            JSONObject.writeValue(out, o);
            return out;
        }

        public String toString() {
            return String.valueOf(o);
        }
    }

    /**
     * Enumeration of the possible modes of the JSONWriter
     */
    protected enum Mode {
        INIT,
        OBJECT,
        ARRAY,
        KEY,
        DONE
    }
}
