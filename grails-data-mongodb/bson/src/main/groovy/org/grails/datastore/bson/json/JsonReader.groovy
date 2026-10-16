/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.bson.json

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.bson.AbstractBsonReader
import org.bson.AbstractBsonReader.State
import org.bson.BsonBinary
import org.bson.BsonContextType
import org.bson.BsonDbPointer
import org.bson.BsonInvalidOperationException
import org.bson.BsonReaderMark
import org.bson.BsonRegularExpression
import org.bson.BsonTimestamp
import org.bson.BsonType
import org.bson.json.JsonParseException
import org.bson.types.Decimal128
import org.bson.types.ObjectId

/**
 * A simplified fork of {@link org.bson.json.JsonReader} that works with readers and removes processing related to MongoDB
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class JsonReader extends AbstractBsonReader {

    @PackageScope final JsonScanner scanner
    @PackageScope JsonToken pushedToken
    @PackageScope Object currentValue

    /**
     * Constructs a new instance with the given JSON string.
     *
     * @param json     A string representation of a JSON.
     */
    JsonReader(final String json) {
        this(new StringReader(json))
    }

    /**
     * Constructs a new instance with the given JSON reader.
     *
     * @param reader The reader.
     */
    JsonReader(final Reader reader) {
        super()
        scanner = new JsonScanner(reader)
        setContext(new Context(null, BsonContextType.TOP_LEVEL))
    }

    @Override
    protected BsonBinary doReadBinaryData() {
        return (BsonBinary) currentValue
    }

    @Override
    protected byte doPeekBinarySubType() {
        return doReadBinaryData().getType()
    }

    @Override
    protected int doPeekBinarySize() {
        return doReadBinaryData().getData().length
    }

    @Override
    protected boolean doReadBoolean() {
        return (Boolean) currentValue
    }

    @Override
    BsonType readBsonType() {
        if (isClosed()) {
            throw new IllegalStateException('This instance has been closed')
        }
        if (getState() == State.INITIAL || getState() == State.DONE || getState() == State.SCOPE_DOCUMENT) {
            // in JSON the top level value can be of any type so fall through
            setState(State.TYPE)
        }
        if (getState() != State.TYPE) {
            throwInvalidState('readBSONType', State.TYPE)
        }

        if (getContext().getContextType() == BsonContextType.DOCUMENT) {
            JsonToken nameToken = popToken()
            switch (nameToken.getType()) {
                case JsonTokenType.STRING:
                case JsonTokenType.UNQUOTED_STRING:
                    setCurrentName(nameToken.getValue(String))
                    break
                case JsonTokenType.END_OBJECT:
                    setState(State.END_OF_DOCUMENT)
                    return BsonType.END_OF_DOCUMENT
                default:
                    throw new JsonParseException("JSON reader was expecting a name but found '%s'.", nameToken.getValue())
            }

            JsonToken colonToken = popToken()
            if (colonToken.getType() != JsonTokenType.COLON) {
                throw new JsonParseException("JSON reader was expecting ':' but found '%s'.", colonToken.getValue())
            }
        }

        JsonToken token = popToken()
        if (getContext().getContextType() == BsonContextType.ARRAY && token.getType() == JsonTokenType.END_ARRAY) {
            setState(State.END_OF_ARRAY)
            return BsonType.END_OF_DOCUMENT
        }

        boolean noValueFound = false
        switch (token.getType()) {
            case JsonTokenType.BEGIN_ARRAY:
                setCurrentBsonType(BsonType.ARRAY)
                break
            case JsonTokenType.BEGIN_OBJECT:
                visitExtendedJSON()
                break
            case JsonTokenType.DOUBLE:
                setCurrentBsonType(BsonType.DOUBLE)
                currentValue = token.getValue()
                break
            case JsonTokenType.END_OF_FILE:
                setCurrentBsonType(BsonType.END_OF_DOCUMENT)
                break
            case JsonTokenType.INT32:
                setCurrentBsonType(BsonType.INT32)
                currentValue = token.getValue()
                break
            case JsonTokenType.INT64:
                setCurrentBsonType(BsonType.INT64)
                currentValue = token.getValue()
                break
            case JsonTokenType.REGULAR_EXPRESSION:
                setCurrentBsonType(BsonType.REGULAR_EXPRESSION)
                currentValue = token.getValue()
                break
            case JsonTokenType.STRING:
                setCurrentBsonType(BsonType.STRING)
                currentValue = token.getValue()
                break
            case JsonTokenType.UNQUOTED_STRING:
                String value = token.getValue(String)

                if (JsonToken.BOOLEAN_FALSE == value || JsonToken.BOOLEAN_TRUE == value) {
                    setCurrentBsonType(BsonType.BOOLEAN)
                    currentValue = Boolean.parseBoolean(value)
                } else if ('Infinity' == value) {
                    setCurrentBsonType(BsonType.DOUBLE)
                    currentValue = Double.POSITIVE_INFINITY
                } else if ('NaN' == value) {
                    setCurrentBsonType(BsonType.DOUBLE)
                    currentValue = Double.NaN
                } else if (JsonToken.NULL == value) {
                    setCurrentBsonType(BsonType.NULL)
                } else if ('undefined' == value) {
                    setCurrentBsonType(BsonType.UNDEFINED)
                } else {
                    noValueFound = true
                }
                break
            default:
                noValueFound = true
                break
        }
        if (noValueFound) {
            throw new JsonParseException("JSON reader was expecting a value but found '%s'.", token.getValue())
        }

        if (getContext().getContextType() == BsonContextType.ARRAY || getContext().getContextType() == BsonContextType.DOCUMENT) {
            JsonToken commaToken = popToken()
            if (commaToken.getType() != JsonTokenType.COMMA) {
                pushToken(commaToken)
            }
        }

        switch (getContext().getContextType()) {
            case BsonContextType.ARRAY:
            case BsonContextType.JAVASCRIPT_WITH_SCOPE:
            case BsonContextType.TOP_LEVEL:
                setState(State.VALUE)
                break
            default:
                // DOCUMENT, SCOPE_DOCUMENT and anything else
                setState(State.NAME)
                break
        }
        return getCurrentBsonType()
    }

    @Override
    protected long doReadDateTime() {
        return (Long) currentValue
    }

    @Override
    protected double doReadDouble() {
        return (Double) currentValue
    }

    @Override
    protected void doReadEndArray() {
        setContext(getContext().getParentContext())

        if (getContext().getContextType() == BsonContextType.ARRAY || getContext().getContextType() == BsonContextType.DOCUMENT) {
            JsonToken commaToken = popToken()
            if (commaToken.getType() != JsonTokenType.COMMA) {
                pushToken(commaToken)
            }
        }
    }

    @Override
    protected void doReadEndDocument() {
        setContext(getContext().getParentContext())
        if (getContext() != null && getContext().getContextType() == BsonContextType.SCOPE_DOCUMENT) {
            setContext(getContext().getParentContext()) // JavaScriptWithScope
            verifyToken(JsonToken.CLOSE_BRACE) // outermost closing bracket for JavaScriptWithScope
        }

        if (getContext() == null) {
            throw new JsonParseException('Unexpected end of document.')
        }

        if (getContext().getContextType() == BsonContextType.ARRAY || getContext().getContextType() == BsonContextType.DOCUMENT) {
            JsonToken commaToken = popToken()
            if (commaToken.getType() != JsonTokenType.COMMA) {
                pushToken(commaToken)
            }
        }
    }

    @Override
    protected int doReadInt32() {
        return (Integer) currentValue
    }

    @Override
    protected long doReadInt64() {
        return (Long) currentValue
    }

    @Override
    protected Decimal128 doReadDecimal128() {
        return Decimal128.parse(currentValue.toString())
    }

    @Override
    protected String doReadJavaScript() {
        return (String) currentValue
    }

    @Override
    protected String doReadJavaScriptWithScope() {
        return (String) currentValue
    }

    @Override
    protected void doReadMaxKey() {
        // no-op
    }

    @Override
    protected void doReadMinKey() {
        // no-op
    }

    @Override
    protected void doReadNull() {
        // no-op
    }

    @Override
    protected ObjectId doReadObjectId() {
        return (ObjectId) currentValue
    }

    @Override
    protected BsonRegularExpression doReadRegularExpression() {
        return (BsonRegularExpression) currentValue
    }

    @Override
    protected BsonDbPointer doReadDBPointer() {
        return (BsonDbPointer) currentValue
    }

    @Override
    protected void doReadStartArray() {
        setContext(new Context(getContext(), BsonContextType.ARRAY))
    }

    @Override
    protected void doReadStartDocument() {
        setContext(new Context(getContext(), BsonContextType.DOCUMENT))
    }

    @Override
    protected String doReadString() {
        return (String) currentValue
    }

    @Override
    protected String doReadSymbol() {
        return (String) currentValue
    }

    @Override
    protected BsonTimestamp doReadTimestamp() {
        return (BsonTimestamp) currentValue
    }

    @Override
    protected void doReadUndefined() {
        // no-op
    }

    @Override
    protected void doSkipName() {
        // no-op
    }

    @Override
    protected void doSkipValue() {
        switch (getCurrentBsonType()) {
            case BsonType.ARRAY:
                readStartArray()
                while (readBsonType() != BsonType.END_OF_DOCUMENT) {
                    skipValue()
                }
                readEndArray()
                break
            case BsonType.BINARY:
                readBinaryData()
                break
            case BsonType.BOOLEAN:
                readBoolean()
                break
            case BsonType.DATE_TIME:
                readDateTime()
                break
            case BsonType.DOCUMENT:
                readStartDocument()
                while (readBsonType() != BsonType.END_OF_DOCUMENT) {
                    skipName()
                    skipValue()
                }
                readEndDocument()
                break
            case BsonType.DOUBLE:
                readDouble()
                break
            case BsonType.INT32:
                readInt32()
                break
            case BsonType.INT64:
                readInt64()
                break
            case BsonType.DECIMAL128:
                readDecimal128()
                break
            case BsonType.JAVASCRIPT:
                readJavaScript()
                break
            case BsonType.JAVASCRIPT_WITH_SCOPE:
                readJavaScriptWithScope()
                readStartDocument()
                while (readBsonType() != BsonType.END_OF_DOCUMENT) {
                    skipName()
                    skipValue()
                }
                readEndDocument()
                break
            case BsonType.MAX_KEY:
                readMaxKey()
                break
            case BsonType.MIN_KEY:
                readMinKey()
                break
            case BsonType.NULL:
                readNull()
                break
            case BsonType.OBJECT_ID:
                readObjectId()
                break
            case BsonType.REGULAR_EXPRESSION:
                readRegularExpression()
                break
            case BsonType.STRING:
                readString()
                break
            case BsonType.SYMBOL:
                readSymbol()
                break
            case BsonType.TIMESTAMP:
                readTimestamp()
                break
            case BsonType.UNDEFINED:
                readUndefined()
                break
            default:
                break
        }
    }

    private JsonToken popToken() {
        if (pushedToken != null) {
            JsonToken token = pushedToken
            pushedToken = null
            return token
        }
        try {
            return scanner.nextToken()
        } catch (IOException e) {
            throw new JsonParseException('Cannot parse JSON due to IO error: ' + e.getMessage(), e)
        }
    }

    private void pushToken(final JsonToken token) {
        if (pushedToken == null) {
            pushedToken = token
        } else {
            throw new BsonInvalidOperationException('There is already a pending token.')
        }
    }

    private void verifyToken(final Object expected) {
        if (expected == null) {
            throw new IllegalArgumentException("Can't be null")
        }
        JsonToken token = popToken()
        if (!expected.equals(token.getValue())) {
            throw new JsonParseException("JSON reader expected '%s' but found '%s'.", expected, token.getValue())
        }
    }

    private void visitExtendedJSON() {
        JsonToken nameToken = popToken()

        pushToken(nameToken)
        setCurrentBsonType(BsonType.DOCUMENT)
    }

    @Override
    BsonReaderMark getMark() {
        return new Mark()
    }

    @Override
    protected Context getContext() {
        return (Context) super.getContext()
    }

    // Lets the inner Mark restore the context: setContext is protected on AbstractBsonReader, and a
    // nested class is a different class to the JVM, so it cannot invoke it on the enclosing instance.
    @PackageScope
    void resetContext(final AbstractBsonReader.Context parentContext, final BsonContextType contextType) {
        setContext(new Context(parentContext, contextType))
    }

    protected class Mark extends AbstractBsonReader.Mark {

        private JsonToken pushedToken
        private Object currentValue
        private int position

        protected Mark() {
            // The superclass is an inner class of AbstractBsonReader: its constructor takes the
            // enclosing reader as a hidden first argument, which javac supplies implicitly but
            // Groovy requires to be passed explicitly.
            super(JsonReader.this)
            pushedToken = JsonReader.this.pushedToken
            currentValue = JsonReader.this.currentValue
            position = JsonReader.this.scanner.position
        }

        @Override
        void reset() {
            super.reset()
            JsonReader.this.pushedToken = pushedToken
            JsonReader.this.currentValue = currentValue
            try {
                JsonReader.this.scanner.reader.reset()
            } catch (IOException e) {
                throw new JsonParseException('Failed to reset reader: ' + e.getMessage(), e)
            }
            JsonReader.this.resetContext(getParentContext(), getContextType())
        }

    }

    protected class Context extends AbstractBsonReader.Context {

        protected Context(final AbstractBsonReader.Context parentContext, final BsonContextType contextType) {
            // See Mark(): the enclosing reader is the inner superclass's hidden first argument.
            super(JsonReader.this, parentContext, contextType)
        }

        @Override
        protected Context getParentContext() {
            return (Context) super.getParentContext()
        }

        // Not redundant: the inherited method is protected on org.bson's Context, so JsonReader
        // can only call it through this re-declaration in its own nested class.
        @Override
        @SuppressWarnings('UnnecessaryOverridingMethod')
        protected BsonContextType getContextType() {
            return super.getContextType()
        }

    }

}
