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
package org.apache.grails.data.mongodb.core.querytail

import groovy.transform.CompileStatic
import org.bson.BsonDocument
import org.bson.BsonValue

import org.apache.grails.datamapping.core.querytail.QueryTailRedactor

/**
 * Takes the values out of a MongoDB command before it is captured. A command is a document, so
 * there is nothing to parse: every value becomes {@link QueryTailRedactor#PLACEHOLDER}, and the
 * keys, which are the options of the command, its operators and the field names of the collection,
 * are kept, except where a key may be data. The keys of a document stored in a map property, or in
 * a property or a collection the {@linkplain MongoMappedFields mapping} does not know, are recorded
 * as the placeholder too, and so is every segment of a dotted path past such a key. So is a key at
 * the top of stored data, the documents an insert writes, a replacement document, the fields an
 * update writes and the operand of a literal, unless the mapping knows it, as the name of a dynamic
 * attribute may be data; the keys at the top of a condition, a pipeline, a sort or a projection
 * are the fields the application asks for, and are kept. An update that is a pipeline writes the
 * documents its stages build: the keys at the top of what a {@code $set}, {@code $addFields} or
 * {@code $project} stage holds, of the new root of a {@code $replaceWith} or {@code $replaceRoot}, and of a
 * document {@code $mergeObjects} merges into it are fields written, kept only when the mapping knows
 * them, as those of a classic update are. A numeric key
 * is kept only where it indexes an array the mapping knows; in a map, in an element of an array,
 * which is a map or a document unless the field is a list of lists, or at the top of a document,
 * it is data. A {@code $} key is kept as an operator only where operators go, in a condition, a
 * pipeline and the operators of an update, and in a condition only when it is one of MongoDB's query
 * operators: any other, such as a key beside the geometry of {@code $near}, is recorded as the
 * placeholder, and so is everything under it. The operand of {@code $elemMatch} is a condition wherever
 * it is, in a projection as in a filter; in stored data, the documents an insert writes, a
 * replacement document and the values an update operator writes, it is a field name, which MongoDB
 * allows to start with {@code $}, and is recorded as the placeholder unless the mapping knows it.
 * So is a {@code $} key in the operand of a comparison in a condition, such as {@code $eq} or
 * {@code $in}, or an implicit document or array equality, which holds the value the field is compared
 * with, and in a DBRef, whose {@code $ref}, {@code $id} and {@code $db} a condition reads as fields
 * compared with their whole value, in any order; in an aggregation expression, which
 * {@code $expr}, the {@code let} of a command and the stages that compute fields hold, the operands
 * of a comparison are expressions, and their operators are kept, except in the operand of a
 * {@code $literal} or {@code $const}, which is stored data. The keys of the command itself are its
 * options, and are kept, but a document under an option is read only where the option is known to
 * hold one: the fields, a condition, a pipeline, an update or stored data, or options in turn, as
 * the concerns, the collation and the cursor of a command, the statements of a write, the indexes it
 * builds and the operations of {@code applyOps} do. Any other option, as a comment, the constants of
 * an update, the scope of a map-reduce command and the document an operation of {@code applyOps}
 * applies are, may hold any document, and none of its keys are kept; nor are those of a
 * {@code $comment}. The entries the driver adds for its
 * session, the cluster time and the transaction, which say nothing about the command, are left out.
 *
 * <p>An array is recorded with each of its different elements once: an element recorded as one before
 * it in the array is left out, so that the documents of a batch and the values of an {@code $in} are
 * recorded the same whatever their number, one or many, and a command is counted as the one statement
 * it is: {@code {"$in": [?]}}. An empty array is recorded as {@code []}.</p>
 *
 * <p>A command is recorded as its name, the database and collection it runs against, and the
 * document of its remaining entries: {@code find shop.book {"filter": {"title": ?}, "limit": ?}}.
 * The collection is taken only from a command that runs against one; the first value of any other
 * command is an argument, such as a user name, and is recorded as a value:
 * {@code dropUser admin {"dropUser": ?}}.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class MongoCommandRedactor {

    private static final String PLACEHOLDER = QueryTailRedactor.PLACEHOLDER

    private static final String DATABASE = '$db'

    private static final String COLLECTION = 'collection'

    /**
     * The commands whose first value is the collection they run against. The first value of any other
     * command, such as the user {@code dropUser} drops, is an argument, and is recorded as a value.
     */
    private static final Set<String> COLLECTION_COMMANDS = ['find', 'insert', 'update', 'delete', 'aggregate',
            'count', 'distinct', 'findAndModify', 'findandmodify', 'mapReduce', 'mapreduce', 'killCursors',
            'create', 'drop', 'collMod', 'createIndexes', 'dropIndexes', 'deleteIndexes', 'listIndexes', 'reIndex',
            'compact', 'validate', 'collStats', 'convertToCapped', 'cloneCollectionAsCapped', 'analyze',
            'checkMetadataConsistency', 'createSearchIndexes', 'dropSearchIndex', 'updateSearchIndex',
            'planCacheClear', 'planCacheClearFilters', 'planCacheListFilters', 'planCacheSetFilter'] as Set<String>

    /** The command that names its collection in its {@code collection} option, as it continues a cursor. */
    private static final String GET_MORE = 'getMore'

    /** The entries the driver adds, which describe its session and transaction rather than the command. */
    private static final Set<String> LEFT_OUT = ['lsid', '$clusterTime', 'txnNumber', 'autocommit',
            'startTransaction', '$readPreference', 'apiVersion', 'apiStrict', 'apiDeprecationErrors'] as Set<String>

    /**
     * The options whose document, or documents, are keyed by the fields of the collection, wherever they appear,
     * including those of the operations of a client-level {@code bulkWrite}.
     */
    private static final Set<String> FIELD_KEYED = ['filter', 'query', 'q', 'u', 'update', 'document', 'documents',
            'updateMods', 'pipeline', 'sort', 'projection', 'fields', 'key', 'hint', 'min', 'max', 'arrayFilters',
            'partialFilterExpression', 'wildcardProjection', 'validator', 'weights'] as Set<String>

    /** Documents that match fields, whose field values may be implicit equality operands. */
    private static final Set<String> CONDITION_KEYED = ['filter', 'query', 'q', 'arrayFilters',
            'partialFilterExpression', 'validator', 'restrictSearchWithMatch'] as Set<String>

    /** The options whose documents are stored data: what an insert writes. */
    private static final Set<String> DATA_KEYED = ['documents', 'document'] as Set<String>

    /**
     * The options whose documents, or the documents of whose arrays, hold options in turn: the concerns and the
     * collation of a command, the cursor it opens, the statements of a write, the indexes it builds, the operations
     * of a client-level {@code bulkWrite} and of {@code applyOps} and their preconditions, where a map-reduce
     * command writes, the command {@code explain} runs, the options of a collection {@code create} builds, and the
     * sessions {@code endSessions} ends. A
     * document under any other option may hold any document, as a comment, the constants of an update, the scope
     * of a map-reduce command and the document an operation of {@code applyOps} applies do, and none of its keys
     * are kept.
     */
    private static final Set<String> OPTION_KEYED = ['writeConcern', 'readConcern', 'collation', 'cursor', 'updates',
            'deletes', 'indexes', 'ops', 'nsInfo', 'applyOps', 'preCondition', 'out', 'explain', 'timeseries',
            'clusteredIndex', 'changeStreamPreAndPostImages', 'endSessions'] as Set<String>

    /** The operator of a condition that may hold any document, none of whose keys say what the command does. */
    private static final String COMMENT = '$comment'

    /**
     * The operator that selects the elements of an array by a condition, in a condition and in a projection alike,
     * where it selects the first element that matches.
     */
    private static final String ELEM_MATCH = '$elemMatch'

    /** The option whose values are aggregation expressions. */
    private static final String LET = 'let'

    /** The options that hold an update: update operators, a replacement document or a pipeline. */
    private static final Set<String> UPDATE_KEYED = ['u', 'update', 'updateMods'] as Set<String>

    /** The update operators whose fields take a condition rather than the value to write. */
    private static final Set<String> CONDITION_OPERATORS = ['$pull', '$currentDate'] as Set<String>

    /** The update operators that add to an array, whose value may hold modifiers. */
    private static final Set<String> PUSH_OPERATORS = ['$push', '$addToSet'] as Set<String>

    private static final String EACH = '$each'

    /** The modifiers of a {@code $push} or an {@code $addToSet}. */
    private static final Set<String> PUSH_MODIFIERS = [EACH, '$slice', '$sort', '$position'] as Set<String>

    /**
     * The operators of a pipeline whose operand is literal data rather than an expression, including
     * {@code $const}, which MongoDB accepts for {@code $literal}.
     */
    private static final Set<String> LITERAL_OPERATORS = ['$literal', '$const', '$documents'] as Set<String>

    /**
     * The fields of a DBRef, which a condition reads as fields compared with their whole value, never as
     * operators, in any order and wherever a condition has fields.
     */
    private static final Set<String> DBREF_FIELDS = ['$ref', '$id', '$db'] as Set<String>

    /**
     * The query operators whose operand holds values rather than a condition: the comparisons, whose operand
     * is the value a field is compared with, a JSON schema, whose {@code enum} lists values, and a
     * GeoJSON geometry, which can carry additional data alongside its type and coordinates.
     */
    private static final Set<String> VALUE_OPERATORS = ['$eq', '$ne', '$gt', '$gte', '$lt', '$lte', '$in', '$nin',
            '$all', '$jsonSchema', '$geometry'] as Set<String>

    /**
     * The operators of a condition, the only {@code $} keys a condition holds but the fields of a DBRef: the query
     * operators of MongoDB, including {@code $within}, the older name of {@code $geoWithin}, which GORM writes for a
     * box, a polygon or a circle, and the options of {@code $regex}, {@code $text} and the geospatial ones.
     */
    private static final Set<String> QUERY_OPERATORS = ['$eq', '$ne', '$gt', '$gte', '$lt', '$lte', '$in', '$nin',
            '$and', '$or', '$not', '$nor', '$exists', '$type', '$expr', '$jsonSchema', '$mod', '$regex', '$options',
            '$text', '$search', '$language', '$caseSensitive', '$diacriticSensitive', '$where', '$geoIntersects',
            '$geoWithin', '$within', '$near', '$nearSphere', '$geometry', '$maxDistance', '$minDistance', '$box', '$center',
            '$centerSphere', '$polygon', '$uniqueDocs', '$all', '$elemMatch', '$size', '$bitsAllClear', '$bitsAllSet',
            '$bitsAnyClear', '$bitsAnySet', '$comment', '$rand', '$sampleRate'] as Set<String>

    /** The operator of a condition, and the stages of a pipeline, whose operand is an aggregation expression. */
    private static final Set<String> EXPRESSION_OPERATORS = ['$expr', '$addFields', '$set', '$project', '$group',
            '$replaceRoot', '$replaceWith', '$redact', '$bucket', '$bucketAuto', '$sortByCount', '$setWindowFields',
            '$fill'] as Set<String>

    /** The stages of an update pipeline whose operand is the document they write, or the new root that replaces it. */
    private static final Set<String> WRITING_STAGES = ['$set', '$addFields', '$project', '$replaceWith'] as Set<String>

    /** The stage of an update pipeline whose options name the new root that replaces the document. */
    private static final String REPLACE_ROOT = '$replaceRoot'

    /** The option of {@code $replaceRoot} that holds the new root. */
    private static final String NEW_ROOT = 'newRoot'

    /** The operator whose operands are documents merged into one, which in an update pipeline is written. */
    private static final String MERGE_OBJECTS = '$mergeObjects'

    /** The kinds of document whose keys at the top are stored data: what an insert, a replacement or an update writes. */
    private static final Set<Kind> STORED = EnumSet.of(Kind.DATA, Kind.UPDATE, Kind.UPDATE_FIELDS, Kind.WRITTEN)

    private MongoCommandRedactor() {
    }

    /**
     * Redacts a command.
     *
     * @param database the database the command runs against
     * @param commandName the name of the command, which is its first key
     * @param command the command as the driver sends it
     * @param fields the fields the mapping of the datastore knows, or null before the datastore is built
     * @return the command with its values replaced
     */
    static String redact(String database, String commandName, BsonDocument command, MongoMappedFields fields) {
        String collection = null
        String collectionKey = null
        BsonValue first = command.get(commandName)
        if (first != null && first.isString() && COLLECTION_COMMANDS.contains(commandName)) {
            collection = first.asString().value
            collectionKey = commandName
        }
        else if (commandName == GET_MORE && command.get(COLLECTION)?.isString()) {
            collection = command.getString(COLLECTION).value
            collectionKey = COLLECTION
        }
        StringBuilder out = new StringBuilder(commandName)
        if (database != null || collection != null) {
            out.append(' ')
            if (database != null) {
                out.append(database)
            }
            if (collection != null) {
                out.append(database != null ? '.' : '').append(collection)
            }
        }
        Scope options = Scope.options(collection, fields)
        StringBuilder body = new StringBuilder()
        for (Map.Entry<String, BsonValue> entry in command.entrySet()) {
            String key = entry.key
            if (key == DATABASE || key == collectionKey || LEFT_OUT.contains(key)) {
                continue
            }
            body.append(body.length() == 0 ? '{' : ', ')
            appendEntry(body, key, entry.value, options)
        }
        if (body.length() > 0) {
            out.append(' ').append(body).append('}')
        }
        out.toString()
    }

    private static void appendEntry(StringBuilder out, String key, BsonValue value, Scope scope) {
        Scope child = scope.of(key, value)
        out.append(child.keyText).append(': ')
        appendValue(out, value, child)
    }

    private static void appendValue(StringBuilder out, BsonValue value, Scope scope) {
        if (value.isDocument()) {
            out.append('{')
            boolean first = true
            for (Map.Entry<String, BsonValue> entry in value.asDocument().entrySet()) {
                if (!first) {
                    out.append(', ')
                }
                first = false
                appendEntry(out, entry.key, entry.value, scope)
            }
            out.append('}')
        }
        else if (value.isArray()) {
            out.append('[')
            Scope element = scope.element()
            // a value is recorded as the placeholder whatever it is, so only a document or an array is rendered
            // to be told apart from the elements before it, and a list of values costs no more than a look at each
            boolean placeholder = false
            Set<String> written = null
            boolean first = true
            for (BsonValue item in value.asArray()) {
                String recorded
                if (item.isDocument() || item.isArray()) {
                    StringBuilder text = new StringBuilder()
                    appendValue(text, item, element)
                    recorded = text.toString()
                    if (written == null) {
                        written = new HashSet<>()
                    }
                    if (!written.add(recorded)) {
                        continue
                    }
                }
                else if (placeholder) {
                    continue
                }
                else {
                    placeholder = true
                    recorded = PLACEHOLDER
                }
                if (!first) {
                    out.append(', ')
                }
                first = false
                out.append(recorded)
            }
            out.append(']')
        }
        else {
            out.append(PLACEHOLDER)
        }
    }

    private static String quote(String key) {
        StringBuilder out = new StringBuilder(key.length() + 2).append('"')
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i)
            if (c == '"' || c == '\\') {
                out.append('\\').append(c)
            }
            else if (c < 0x20) {
                out.append(String.format('\\u%04x', (int) c))
            }
            else {
                out.append(c)
            }
        }
        out.append('"').toString()
    }

    /**
     * Where a document sits in the command, which decides what its keys are: the options of the
     * command, kept; the fields and operators of a condition, a pipeline, a sort or a projection, where
     * a {@code $} key is an operator; an aggregation expression, whose {@code $} keys are operators
     * too; an update, whose {@code $} keys are update operators; or stored data, the documents an
     * insert writes, the values an update writes and the values a condition compares with, where a
     * {@code $} key is a field name like any other and is kept only when the mapping knows it.
     */
    @CompileStatic
    private static final class Scope {

        final String collection
        final MongoMappedFields fields
        final Kind kind
        /**
         * The update operator whose fields a document of kind {@link Kind#UPDATE_FIELDS} holds, or the stage
         * whose document one of kind {@link Kind#WRITTEN} is.
         */
        final String operator
        /** The field path of the document; null in a document keyed by fields the mapping does not know. */
        final List<String> path
        /** How many indices into the field at the path have been taken: 0 at the field, 1 in one of its elements. */
        final int depth
        final String keyText

        private Scope(String collection, MongoMappedFields fields, Kind kind, String operator, List<String> path,
                int depth, String keyText) {
            this.collection = collection
            this.fields = fields
            this.kind = kind
            this.operator = operator
            this.path = path
            this.depth = depth
            this.keyText = keyText
        }

        static Scope options(String collection, MongoMappedFields fields) {
            new Scope(collection, fields, Kind.OPTIONS, null, Collections.<String>emptyList(), 0, null)
        }

        /**
         * @return the scope of an element of an array in this scope: one index deeper into the field
         * at the path, so that a numeric key inside the element is data unless the elements are arrays;
         * the elements of an update that is an array are the stages of a pipeline, and an element of an
         * expression at the top of a document, as the operands of an operator {@code $expr} holds are, is a
         * value the expression builds, whose keys are kept only when the mapping knows them, as those of a
         * document a stage of an update pipeline writes are
         */
        Scope element() {
            if (kind == Kind.UPDATE) {
                return child(Kind.UPDATE_PIPELINE, null, path, depth, keyText)
            }
            if (kind == Kind.PUSH) {
                return child(Kind.DATA, null, path, depth + 2, keyText)
            }
            if ((kind == Kind.QUERY || kind == Kind.CONDITION || kind == Kind.EXPRESSION || kind == Kind.DATA) && path != null && !path.isEmpty()) {
                return child(kind, null, path, depth + 1, keyText)
            }
            if (kind == Kind.EXPRESSION && path != null) {
                return child(Kind.WRITTEN, null, path, depth, keyText)
            }
            this
        }

        /**
         * @return the scope of the value of a key in a document of this scope, with the key as it is recorded
         */
        Scope of(String key, BsonValue value) {
            if (kind == Kind.OPTIONS) {
                Kind valueKind = DATA_KEYED.contains(key) ? Kind.DATA :
                        UPDATE_KEYED.contains(key) ? Kind.UPDATE :
                        CONDITION_KEYED.contains(key) ? Kind.CONDITION :
                        FIELD_KEYED.contains(key) ? Kind.QUERY :
                        key == LET ? Kind.EXPRESSION :
                        OPTION_KEYED.contains(key) ? Kind.OPTIONS : null
                if (valueKind == null) {
                    // an option not known to hold options, fields or a pipeline, whose document may be any data
                    return child(Kind.DATA, null, null, 0, quote(key))
                }
                return child(valueKind, null, Collections.<String>emptyList(), 0, quote(key))
            }
            if ((kind == Kind.QUERY || kind == Kind.CONDITION) && key.startsWith('$')) {
                if (key == COMMENT) {
                    return child(Kind.DATA, null, null, 0, quote(key))
                }
                if (kind == Kind.CONDITION && DBREF_FIELDS.contains(key)) {
                    return field(key, Kind.DATA)
                }
                if (kind == Kind.CONDITION && !QUERY_OPERATORS.contains(key)) {
                    // no operator of a condition, as a key beside the geometry of $near is not, so data the server rejects
                    return child(Kind.DATA, null, null, 0, PLACEHOLDER)
                }
                if (EXPRESSION_OPERATORS.contains(key)) {
                    return child(Kind.EXPRESSION, null, path, depth, quote(key))
                }
                if (key == '$match') {
                    return child(Kind.CONDITION, null, path, depth, quote(key))
                }
                // an operator on an array, such as $elemMatch, describes its elements
                int operandDepth = indexed(path, depth) ? depth + 1 : depth
                // the operand of $elemMatch is a condition on those elements, in a projection as in a condition
                Kind operandKind = LITERAL_OPERATORS.contains(key) || VALUE_OPERATORS.contains(key) ? Kind.DATA :
                        key == ELEM_MATCH ? Kind.CONDITION : kind
                return child(operandKind, null, path, operandDepth, quote(key))
            }
            if (kind == Kind.CONDITION) {
                // a DBRef, whichever of its fields comes first, is compared whole
                boolean operators = value.isDocument() && !value.asDocument().isEmpty() &&
                        value.asDocument().firstKey.startsWith('$') && !dbRef(value.asDocument())
                Scope operand = field(key, operators ? Kind.CONDITION : Kind.DATA)
                // A literal document compared with a list is an element, not a set of array indices.
                if (!operators && value.isDocument() && operand.indexed(operand.path, operand.depth)) {
                    return operand.child(Kind.DATA, null, operand.path, operand.depth + 1, operand.keyText)
                }
                return operand
            }
            if (kind == Kind.QUERY && CONDITION_KEYED.contains(key)) {
                return field(key, Kind.CONDITION)
            }
            if (kind == Kind.EXPRESSION && key.startsWith('$')) {
                // every $ key of an expression is an operator; only a literal holds data
                return child(LITERAL_OPERATORS.contains(key) ? Kind.DATA : Kind.EXPRESSION, null, path, depth, quote(key))
            }
            if (kind == Kind.UPDATE_PIPELINE) {
                if (WRITING_STAGES.contains(key) || key == REPLACE_ROOT) {
                    return child(Kind.WRITTEN, key, Collections.<String>emptyList(), 0, quote(key))
                }
                // any other stage, as $unset, is read as a stage of a pipeline
                return child(Kind.QUERY, null, path, depth, keyText).of(key, value)
            }
            if (kind == Kind.WRITTEN) {
                if ((operator == REPLACE_ROOT && key == NEW_ROOT) || key == MERGE_OBJECTS) {
                    // the new root, or the documents merged into it, which are written whole
                    return child(Kind.WRITTEN, null, Collections.<String>emptyList(), 0, quote(key))
                }
                if (key.startsWith('$')) {
                    // an expression that computes the document; only a literal holds data
                    return child(LITERAL_OPERATORS.contains(key) ? Kind.DATA : Kind.EXPRESSION, null, path, depth, quote(key))
                }
                return field(key, Kind.EXPRESSION)
            }
            if (kind == Kind.UPDATE) {
                // update operators, or the fields of a replacement document, which is stored data
                return key.startsWith('$') ? child(Kind.UPDATE_FIELDS, key, Collections.<String>emptyList(), 0, quote(key)) :
                        field(key, Kind.DATA)
            }
            if (kind == Kind.UPDATE_FIELDS) {
                Kind valueKind = CONDITION_OPERATORS.contains(operator) ? Kind.CONDITION :
                        PUSH_OPERATORS.contains(operator) ? Kind.PUSH : Kind.DATA
                return field(key, valueKind)
            }
            if (kind == Kind.PUSH) {
                if (key == EACH) {
                    return child(Kind.DATA, null, path, depth, quote(key))
                }
                if (PUSH_MODIFIERS.contains(key)) {
                    return child(Kind.QUERY, null, path, depth + 1, quote(key))
                }
                // the document being added is an element of the field
                return child(Kind.DATA, null, path, depth + 1, keyText).field(key, Kind.DATA)
            }
            field(key, kind)
        }

        private Scope child(Kind childKind, String childOperator, List<String> childPath, int childDepth, String text) {
            new Scope(collection, fields, childKind, childOperator, childPath, childDepth, text)
        }

        private static boolean dbRef(BsonDocument document) {
            DBREF_FIELDS.any { String key -> document.containsKey(key) }
        }

        private boolean indexed(List<String> at, int atDepth) {
            at != null && !at.isEmpty() && fields != null && fields.isIndexed(collection, at, atDepth)
        }

        private boolean mapped(List<String> at) {
            fields != null && fields.isField(collection, at)
        }

        /**
         * Resolves a key that names a field, dotted or not, against the mapping: a segment the mapping
         * does not know at its path, and every segment past it, is recorded as the placeholder. A key
         * at the top of a document is kept as a field, except a {@code $} key outside a condition, and
         * any key at the top of stored data, which are kept only when the mapping knows them.
         */
        private Scope field(String key, Kind valueKind) {
            List<String> current = path
            int currentDepth = depth
            String[] segments = key.split('\\.', -1)
            StringBuilder recorded = new StringBuilder()
            boolean anyKept = false
            for (int i = 0; i < segments.length; i++) {
                String segment = segments[i]
                boolean kept
                if (current == null) {
                    kept = false
                }
                else if (MongoMappedFields.isIndex(segment)) {
                    kept = indexed(current, currentDepth)
                    if (kept) {
                        currentDepth++
                    }
                    else {
                        current = null
                    }
                }
                else if (i == 0 && current.isEmpty()) {
                    List<String> top = [segment]
                    kept = kind == Kind.QUERY || (!segment.startsWith('$') && !STORED.contains(kind)) || mapped(top)
                    current = kept ? top : null
                    currentDepth = 0
                }
                else {
                    List<String> deeper = new ArrayList<>(current)
                    deeper.add(segment)
                    kept = mapped(deeper)
                    current = kept ? deeper : null
                    currentDepth = 0
                }
                if (i > 0) {
                    recorded.append('.')
                }
                recorded.append(kept ? segment : PLACEHOLDER)
                anyKept |= kept
            }
            child(valueKind, null, current, currentDepth, anyKept ? quote(recorded.toString()) : PLACEHOLDER)
        }
    }

    /** What the keys of a document are, by where the document sits in the command. */
    @CompileStatic
    private static enum Kind {
        /** The options of the command. */
        OPTIONS,
        /**
         * The fields and operators of a condition, a pipeline, a sort, a projection or an index key; the operand
         * of a comparison is a value, and is stored data.
         */
        QUERY,
        /** A matching document: field values are operator documents or implicit equality operands. */
        CONDITION,
        /** An aggregation expression, whose {@code $} keys are all operators; the operand of a literal is stored data. */
        EXPRESSION,
        /**
         * The fields of stored data, which an insert or an update writes or a condition compares with; a {@code $}
         * key is a field there.
         */
        DATA,
        /** An update: its update operators, or the fields of a replacement document. */
        UPDATE,
        /** The fields an update operator writes; the operator decides whether their values are data or a condition. */
        UPDATE_FIELDS,
        /** What a {@code $push} or {@code $addToSet} adds to a field: its modifiers, or the element itself. */
        PUSH,
        /** The stages of an update that is a pipeline. */
        UPDATE_PIPELINE,
        /**
         * A document a stage of an update pipeline writes: an aggregation expression whose keys at the top are the
         * fields written, which are stored data, or the options of {@code $replaceRoot}. A document an expression
         * builds in the operands of an operator at the top of {@code $expr} is read the same way.
         */
        WRITTEN
    }
}
