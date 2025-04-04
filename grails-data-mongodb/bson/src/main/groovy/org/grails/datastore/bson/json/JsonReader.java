/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.bson.json;

import org.bson.*;
import org.bson.json.JsonParseException;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * A simplified fork of {@link org.bson.json.JsonReader} that works with readers and removes processing related to MongoDB
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class JsonReader extends AbstractBsonReader {

    private final JsonScanner scanner;
    private JsonToken pushedToken;
    private Object currentValue;
    private Mark mark;

    /**
     * Constructs a new instance with the given JSON string.
     *
     * @param json     A string representation of a JSON.
     */
    public JsonReader(final String json) {
        this(new StringReader(json));
    }

    /**
     * Constructs a new instance with the given JSON reader.
     *
     * @param reader The reader.
     */
    public JsonReader(final Reader reader) {
        super();
        scanner = new JsonScanner(reader);
        setContext(new Context(null, BsonContextType.TOP_LEVEL));
    }

    @Override
    protected BsonBinary doReadBinaryData() {
        return (BsonBinary) currentValue;
    }

    @Override
    protected byte doPeekBinarySubType() {
        return doReadBinaryData().getType();
    }

    @Override
    protected int doPeekBinarySize() {
        return doReadBinaryData().getData().length;
    }

    @Override
    protected boolean doReadBoolean() {
        return (Boolean) currentValue;
    }

    //CHECKSTYLE:OFF
    @Override
    public BsonType readBsonType() {
        if (isClosed()) {
            throw new IllegalStateException("This instance has been closed");
        }
        if (getState() == State.INITIAL || getState() == State.DONE || getState() == State.SCOPE_DOCUMENT) {
            // in JSON the top level value can be of any type so fall through
            setState(State.TYPE);
        }
        if (getState() != State.TYPE) {
            throwInvalidState("readBSONType", State.TYPE);
        }

        if (getContext().getContextType() == BsonContextType.DOCUMENT) {
            JsonToken nameToken = popToken();
            switch (nameToken.getType()) {
                case STRING:
                case UNQUOTED_STRING:
                    setCurrentName(nameToken.getValue(String.class));
                    break;
                case END_OBJECT:
                    setState(State.END_OF_DOCUMENT);
                    return BsonType.END_OF_DOCUMENT;
                default:
                    throw new JsonParseException("JSON reader was expecting a name but found '%s'.", nameToken.getValue());
            }

            JsonToken colonToken = popToken();
            if (colonToken.getType() != JsonTokenType.COLON) {
                throw new JsonParseException("JSON reader was expecting ':' but found '%s'.", colonToken.getValue());
            }
        }

        JsonToken token = popToken();
        if (getContext().getContextType() == BsonContextType.ARRAY && token.getType() == JsonTokenType.END_ARRAY) {
            setState(State.END_OF_ARRAY);
            return BsonType.END_OF_DOCUMENT;
        }

        boolean noValueFound = false;
        switch (token.getType()) {
            case BEGIN_ARRAY:
                setCurrentBsonType(BsonType.ARRAY);
                break;
            case BEGIN_OBJECT:
                visitExtendedJSON();
                break;
            case DOUBLE:
                setCurrentBsonType(BsonType.DOUBLE);
                currentValue = token.getValue();
                break;
            case END_OF_FILE:
                setCurrentBsonType(BsonType.END_OF_DOCUMENT);
                break;
            case INT32:
                setCurrentBsonType(BsonType.INT32);
                currentValue = token.getValue();
                break;
            case INT64:
                setCurrentBsonType(BsonType.INT64);
                currentValue = token.getValue();
                break;
            case REGULAR_EXPRESSION:
                setCurrentBsonType(BsonType.REGULAR_EXPRESSION);
                currentValue = token.getValue();
                break;
            case STRING:
                setCurrentBsonType(BsonType.STRING);
                currentValue = token.getValue();
                break;
            case UNQUOTED_STRING:
                String value = token.getValue(String.class);

                if (JsonToken.BOOLEAN_FALSE.equals(value) || JsonToken.BOOLEAN_TRUE.equals(value)) {
                    setCurrentBsonType(BsonType.BOOLEAN);
                    currentValue = Boolean.parseBoolean(value);
                } else if ("Infinity".equals(value)) {
                    setCurrentBsonType(BsonType.DOUBLE);
                    currentValue = Double.POSITIVE_INFINITY;
                } else if ("NaN".equals(value)) {
                    setCurrentBsonType(BsonType.DOUBLE);
                    currentValue = Double.NaN;
                } else if (JsonToken.NULL.equals(value)) {
                    setCurrentBsonType(BsonType.NULL);
                } else if ("undefined".equals(value)) {
                    setCurrentBsonType(BsonType.UNDEFINED);
                } else {
                    noValueFound = true;
                }
                break;
            default:
                noValueFound = true;
                break;
        }
        if (noValueFound) {
            throw new JsonParseException("JSON reader was expecting a value but found '%s'.", token.getValue());
        }

        if (getContext().getContextType() == BsonContextType.ARRAY || getContext().getContextType() == BsonContextType.DOCUMENT) {
            JsonToken commaToken = popToken();
            if (commaToken.getType() != JsonTokenType.COMMA) {
                pushToken(commaToken);
            }
        }

        switch (getContext().getContextType()) {
            case DOCUMENT:
            case SCOPE_DOCUMENT:
            default:
                setState(State.NAME);
                break;
            case ARRAY:
            case JAVASCRIPT_WITH_SCOPE:
            case TOP_LEVEL:
                setState(State.VALUE);
                break;
        }
        return getCurrentBsonType();
    }
    //CHECKSTYLE:ON

    @Override
    protected long doReadDateTime() {
        return (Long) currentValue;
    }

    @Override
    protected double doReadDouble() {
        return (Double) currentValue;
    }

    @Override
    protected void doReadEndArray() {
        setContext(getContext().getParentContext());

        if (getContext().getContextType() == BsonContextType.ARRAY || getContext().getContextType() == BsonContextType.DOCUMENT) {
            JsonToken commaToken = popToken();
            if (commaToken.getType() != JsonTokenType.COMMA) {
                pushToken(commaToken);
            }
        }
    }


    @Override
    protected void doReadEndDocument() {
        setContext(getContext().getParentContext());
        if (getContext() != null && getContext().getContextType() == BsonContextType.SCOPE_DOCUMENT) {
            setContext(getContext().getParentContext()); // JavaScriptWithScope
            verifyToken(JsonToken.CLOSE_BRACE); // outermost closing bracket for JavaScriptWithScope
        }

        if (getContext() == null) {
            throw new JsonParseException("Unexpected end of document.");
        }

        if (getContext().getContextType() == BsonContextType.ARRAY || getContext().getContextType() == BsonContextType.DOCUMENT) {
            JsonToken commaToken = popToken();
            if (commaToken.getType() != JsonTokenType.COMMA) {
                pushToken(commaToken);
            }
        }
    }

    @Override
    protected int doReadInt32() {
        return (Integer) currentValue;
    }

    @Override
    protected long doReadInt64() {
        return (Long) currentValue;
    }

    @Override
    protected Decimal128 doReadDecimal128() {
        return Decimal128.parse(currentValue.toString());
    }

    @Override
    protected String doReadJavaScript() {
        return (String) currentValue;
    }

    @Override
    protected String doReadJavaScriptWithScope() {
        return (String) currentValue;
    }

    @Override
    protected void doReadMaxKey() {
    }

    @Override
    protected void doReadMinKey() {
    }

    @Override
    protected void doReadNull() {
    }

    @Override
    protected ObjectId doReadObjectId() {
        return (ObjectId) currentValue;
    }

    @Override
    protected BsonRegularExpression doReadRegularExpression() {
        return (BsonRegularExpression) currentValue;
    }

    @Override
    protected BsonDbPointer doReadDBPointer() {
        return (BsonDbPointer) currentValue;
    }

    @Override
    protected void doReadStartArray() {
        setContext(new Context(getContext(), BsonContextType.ARRAY));
    }

    @Override
    protected void doReadStartDocument() {
        setContext(new Context(getContext(), BsonContextType.DOCUMENT));
    }

    @Override
    protected String doReadString() {
        return (String) currentValue;
    }

    @Override
    protected String doReadSymbol() {
        return (String) currentValue;
    }

    @Override
    protected BsonTimestamp doReadTimestamp() {
        return (BsonTimestamp) currentValue;
    }

    @Override
    protected void doReadUndefined() {
    }

    @Override
    protected void doSkipName() {
    }

    @Override
    protected void doSkipValue() {
        switch (getCurrentBsonType()) {
            case ARRAY:
                readStartArray();
                while (readBsonType() != BsonType.END_OF_DOCUMENT) {
                    skipValue();
                }
                readEndArray();
                break;
            case BINARY:
                readBinaryData();
                break;
            case BOOLEAN:
                readBoolean();
                break;
            case DATE_TIME:
                readDateTime();
                break;
            case DOCUMENT:
                readStartDocument();
                while (readBsonType() != BsonType.END_OF_DOCUMENT) {
                    skipName();
                    skipValue();
                }
                readEndDocument();
                break;
            case DOUBLE:
                readDouble();
                break;
            case INT32:
                readInt32();
                break;
            case INT64:
                readInt64();
                break;
            case DECIMAL128:
                readDecimal128();
                break;
            case JAVASCRIPT:
                readJavaScript();
                break;
            case JAVASCRIPT_WITH_SCOPE:
                readJavaScriptWithScope();
                readStartDocument();
                while (readBsonType() != BsonType.END_OF_DOCUMENT) {
                    skipName();
                    skipValue();
                }
                readEndDocument();
                break;
            case MAX_KEY:
                readMaxKey();
                break;
            case MIN_KEY:
                readMinKey();
                break;
            case NULL:
                readNull();
                break;
            case OBJECT_ID:
                readObjectId();
                break;
            case REGULAR_EXPRESSION:
                readRegularExpression();
                break;
            case STRING:
                readString();
                break;
            case SYMBOL:
                readSymbol();
                break;
            case TIMESTAMP:
                readTimestamp();
                break;
            case UNDEFINED:
                readUndefined();
                break;
            default:
        }
    }

    private JsonToken popToken() {
        if (pushedToken != null) {
            JsonToken token = pushedToken;
            pushedToken = null;
            return token;
        } else {
            try {
                return scanner.nextToken();
            } catch (IOException e) {
                throw new JsonParseException("Cannot parse JSON due to IO error: " + e.getMessage(), e);
            }
        }
    }

    private void pushToken(final JsonToken token) {
        if (pushedToken == null) {
            pushedToken = token;
        } else {
            throw new BsonInvalidOperationException("There is already a pending token.");
        }
    }

    private void verifyToken(final Object expected) {
        if (expected == null) {
            throw new IllegalArgumentException("Can't be null");
        }
        JsonToken token = popToken();
        if (!expected.equals(token.getValue())) {
            throw new JsonParseException("JSON reader expected '%s' but found '%s'.", expected, token.getValue());
        }
    }

    private void visitExtendedJSON() {
        JsonToken nameToken = popToken();

        pushToken(nameToken);
        setCurrentBsonType(BsonType.DOCUMENT);
    }

    @Override
    public BsonReaderMark getMark() {
        return new Mark();
    }

    @Override
    protected Context getContext() {
        return (Context) super.getContext();
    }

    protected class Mark extends AbstractBsonReader.Mark {
        private JsonToken pushedToken;
        private Object currentValue;
        private int position;

        protected Mark() {
            super();
            pushedToken = JsonReader.this.pushedToken;
            currentValue = JsonReader.this.currentValue;
            position = JsonReader.this.scanner.position;
        }

        @Override
        public void reset() {

            super.reset();
            JsonReader.this.pushedToken = pushedToken;
            JsonReader.this.currentValue = currentValue;
            try {
                JsonReader.this.scanner.reader.reset();
            } catch (IOException e) {
                throw new JsonParseException("Failed to reset reader: " + e.getMessage(), e);
            }
            JsonReader.this.setContext(new Context(getParentContext(), getContextType()));
            mark = null;
        }
    }


    protected class Context extends AbstractBsonReader.Context {
        protected Context(final AbstractBsonReader.Context parentContext, final BsonContextType contextType) {
            super(parentContext, contextType);
        }

        protected Context getParentContext() {
            return (Context) super.getParentContext();
        }

        protected BsonContextType getContextType() {
            return super.getContextType();
        }
    }
}
