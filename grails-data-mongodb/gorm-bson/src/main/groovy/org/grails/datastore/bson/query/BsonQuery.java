package org.grails.datastore.bson.query;

import grails.gorm.DetachedCriteria;
import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.Document;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.codehaus.groovy.runtime.NullObject;
import org.grails.datastore.bson.codecs.CodecCustomTypeMarshaller;
import org.grails.datastore.mapping.config.Property;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.engine.internal.MappingUtils;
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Custom;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.datastore.mapping.model.types.ToOne;
import org.grails.datastore.mapping.proxy.ProxyHandler;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.reflect.EntityReflector;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

import java.util.*;
import java.util.regex.Pattern;

/**
 * A base class for Query implementations that create BSON queries based on MongoDB query format. See https://docs.mongodb.com/manual/tutorial/query-documents/
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public abstract class BsonQuery extends Query {
    public static final String PROJECT_OPERATOR = "$project";
    public static final String SORT_OPERATOR = "$sort";
    public static final String IN_OPERATOR = "$in";
    public static final String OR_OPERATOR = "$or";
    public static final String AND_OPERATOR = "$and";
    public static final String GTE_OPERATOR = "$gte";
    public static final String LTE_OPERATOR = "$lte";
    public static final String GT_OPERATOR = "$gt";
    public static final String LT_OPERATOR = "$lt";
    public static final String NE_OPERATOR = "$ne";
    public static final String EQ_OPERATOR = "$eq";
    public static final String NIN_OPERATOR = "$nin";
    public static final String REGEX_OPERATOR = "$regex";
    public static final String MATCH_OPERATOR = "$match";
    public static final String AVERAGE_OPERATOR = "$avg";
    public static final String GROUP_OPERATOR = "$group";
    public static final String SUM_OPERATOR = "$sum";
    public static final String MIN_OPERATOR = "$min";
    public static final String MAX_OPERATOR = "$max";
    public static final String SIZE_OPERATOR = "$size";
    public static final String NOT_OPERATOR = "$not";
    public static final String NOR_OPERATOR = "$nor";
    public static final String EXISTS_OPERATOR = "$exists";
    public static final String WHERE_OPERATOR = "$where";
    public static final EncoderContext ENCODER_CONTEXT = EncoderContext.builder().build();
    public static final String ID_REFERENCE_SUFFIX = ".$id";

    private static final String THIS_PREFIX = "this.";
    protected static Map<Class, QueryHandler> queryHandlers = new HashMap<>();
    protected static Map<String, OperatorHandler> operatorHandlers = new HashMap<>();
    protected static Map<Class, ProjectionHandler> groupByProjectionHandlers = new HashMap<>();

    protected static Map<Class, ProjectionHandler> projectProjectionHandlers = new HashMap<>();

    static {
        queryHandlers.put(IdEquals.class, new QueryHandler<IdEquals>() {
            public void handle(EmbeddedQueryEncoder queryEncoder, IdEquals criterion, Document query, PersistentEntity entity) {
                Object value = criterion.getValue();
                MappingContext mappingContext = entity.getMappingContext();
                PersistentProperty identity = entity.getIdentity();
                Object converted = mappingContext.getConversionService().convert(value, identity.getType());
                Property mappedForm = identity.getMapping().getMappedForm();
                String targetProperty = mappedForm.getTargetName();
                if(targetProperty == null) {
                    targetProperty = identity.getName();
                }
                query.put(targetProperty, converted);
            }
        });

        queryHandlers.put(Equals.class, new QueryHandler<Equals>() {
            public void handle(EmbeddedQueryEncoder queryEncoder, Equals criterion, Document query, PersistentEntity entity) {
                String propertyName = getPropertyName(entity, criterion);
                PersistentProperty persistentProperty = entity.getPropertyByName(criterion.getProperty());
                Object value;
                if ((persistentProperty instanceof Embedded) && criterion.getValue() != null) {
                    value = queryEncoder.encode((Embedded) persistentProperty, criterion.getValue());
                } else {
                    value = criterion.getValue();
                }
                if (value instanceof Pattern) {
                    Pattern pattern = (Pattern) value;
                    query.put(propertyName, new Document(REGEX_OPERATOR, pattern.toString()));
                } else {
                    query.put(propertyName, value);
                }
            }
        });

        queryHandlers.put(IsNull.class, new QueryHandler<IsNull>() {
            @SuppressWarnings("unchecked")
            public void handle(EmbeddedQueryEncoder queryEncoder, IsNull criterion, Document query, PersistentEntity entity) {
                queryHandlers.get(Equals.class).handle(queryEncoder, new Equals(criterion.getProperty(), null), query, entity);
            }
        });
        queryHandlers.put(IsNotNull.class, new QueryHandler<IsNotNull>() {
            @SuppressWarnings("unchecked")
            public void handle(EmbeddedQueryEncoder queryEncoder, IsNotNull criterion, Document query, PersistentEntity entity) {
                queryHandlers.get(NotEquals.class).handle(queryEncoder, new NotEquals(criterion.getProperty(), null), query, entity);
            }
        });
        queryHandlers.put(EqualsProperty.class, new QueryHandler<EqualsProperty>() {
            public void handle(EmbeddedQueryEncoder queryEncoder, EqualsProperty criterion, Document query, PersistentEntity entity) {
                String propertyName = getPropertyName(entity, criterion);
                String otherPropertyName = getPropertyName(entity, criterion.getOtherProperty());
                addWherePropertyComparison(query, propertyName, otherPropertyName, "==");
            }
        });
        queryHandlers.put(NotEqualsProperty.class, new QueryHandler<NotEqualsProperty>() {
            public void handle(EmbeddedQueryEncoder queryEncoder, NotEqualsProperty criterion, Document query, PersistentEntity entity) {
                String propertyName = getPropertyName(entity, criterion);
                String otherPropertyName = getPropertyName(entity, criterion.getOtherProperty());
                addWherePropertyComparison(query, propertyName, otherPropertyName, "!=");
            }
        });
        queryHandlers.put(GreaterThanProperty.class, new QueryHandler<GreaterThanProperty>() {
            public void handle(EmbeddedQueryEncoder queryEncoder, GreaterThanProperty criterion, Document query, PersistentEntity entity) {
                String propertyName = getPropertyName(entity, criterion);
                String otherPropertyName = getPropertyName(entity, criterion.getOtherProperty());
                addWherePropertyComparison(query, propertyName, otherPropertyName, ">");
            }
        });
        queryHandlers.put(LessThanProperty.class, new QueryHandler<LessThanProperty>() {
            public void handle(EmbeddedQueryEncoder queryEncoder, LessThanProperty criterion, Document query, PersistentEntity entity) {
                String propertyName = getPropertyName(entity, criterion);
                String otherPropertyName = getPropertyName(entity, criterion.getOtherProperty());
                addWherePropertyComparison(query, propertyName, otherPropertyName, "<");
            }
        });
        queryHandlers.put(GreaterThanEqualsProperty.class, new QueryHandler<GreaterThanEqualsProperty>() {
            public void handle(EmbeddedQueryEncoder queryEncoder, GreaterThanEqualsProperty criterion, Document query, PersistentEntity entity) {
                String propertyName = getPropertyName(entity, criterion);
                String otherPropertyName = getPropertyName(entity, criterion.getOtherProperty());
                addWherePropertyComparison(query, propertyName, otherPropertyName, ">=");
            }
        });
        queryHandlers.put(LessThanEqualsProperty.class, new QueryHandler<LessThanEqualsProperty>() {
            public void handle(EmbeddedQueryEncoder queryEncoder, LessThanEqualsProperty criterion, Document query, PersistentEntity entity) {

                String propertyName = getPropertyName(entity, criterion);
                String otherPropertyName = getPropertyName(entity, criterion.getOtherProperty());
                addWherePropertyComparison(query, propertyName, otherPropertyName, "<=");
            }
        });

        queryHandlers.put(NotEquals.class, new QueryHandler<NotEquals>() {
            public void handle(EmbeddedQueryEncoder queryEncoder, NotEquals criterion, Document query, PersistentEntity entity) {
                String propertyName = getPropertyName(entity, criterion);
                Document notEqualQuery = getOrCreatePropertyQuery(query, propertyName);
                notEqualQuery.put(NE_OPERATOR, criterion.getValue());

                query.put(propertyName, notEqualQuery);
            }
        });

        queryHandlers.put(Like.class, new QueryHandler<Like>() {
            public void handle(EmbeddedQueryEncoder queryEncoder, Like like, Document query, PersistentEntity entity) {
                handleLike(entity, like, query, true);
            }
        });

        queryHandlers.put(ILike.class, new QueryHandler<ILike>() {
            public void handle(EmbeddedQueryEncoder queryEncoder, ILike like, Document query, PersistentEntity entity) {
                handleLike(entity, like, query, false);
            }
        });

        queryHandlers.put(RLike.class, new QueryHandler<RLike>() {
            public void handle(EmbeddedQueryEncoder queryEncoder, RLike like, Document query, PersistentEntity entity) {
                Object value = like.getValue();
                if (value == null) value = "null";
                final String expr = value.toString();
                Pattern regex = Pattern.compile(expr);
                String propertyName = getPropertyName(entity, like);
                query.put(propertyName, regex);
            }
        });

        queryHandlers.put(In.class, new QueryHandler<In>() {
            public void handle(EmbeddedQueryEncoder queryEncoder, In in, Document query, PersistentEntity entity) {
                Document inQuery = new Document();
                List values = getInListQueryValues(entity, in);
                inQuery.put(IN_OPERATOR, values);
                String propertyName = getPropertyName(entity, in);
                query.put(propertyName, inQuery);
            }
        });

        queryHandlers.put(Between.class, new QueryHandler<Between>() {
            public void handle(EmbeddedQueryEncoder queryEncoder, Between between, Document query, PersistentEntity entity) {
                Document betweenQuery = new Document();
                betweenQuery.put(GTE_OPERATOR, between.getFrom());
                betweenQuery.put(LTE_OPERATOR, between.getTo());
                String propertyName = getPropertyName(entity, between);
                query.put(propertyName, betweenQuery);
            }
        });

        queryHandlers.put(GreaterThan.class, new QueryHandler<GreaterThan>() {
            public void handle(EmbeddedQueryEncoder queryEncoder, GreaterThan criterion, Document query, PersistentEntity entity) {
                String propertyName = getPropertyName(entity, criterion);
                Document greaterThanQuery = getOrCreatePropertyQuery(query, propertyName);
                greaterThanQuery.put(GT_OPERATOR, criterion.getValue());

                query.put(propertyName, greaterThanQuery);
            }
        });

        queryHandlers.put(GreaterThanEquals.class, new QueryHandler<GreaterThanEquals>() {
            public void handle(EmbeddedQueryEncoder queryEncoder, GreaterThanEquals criterion, Document query, PersistentEntity entity) {
                String propertyName = getPropertyName(entity, criterion);
                Document greaterThanQuery = getOrCreatePropertyQuery(query, propertyName);
                greaterThanQuery.put(GTE_OPERATOR, criterion.getValue());

                query.put(propertyName, greaterThanQuery);
            }
        });

        queryHandlers.put(LessThan.class, new QueryHandler<LessThan>() {
            public void handle(EmbeddedQueryEncoder queryEncoder, LessThan criterion, Document query, PersistentEntity entity) {
                String propertyName = getPropertyName(entity, criterion);
                Document lessThanQuery = getOrCreatePropertyQuery(query, propertyName);
                lessThanQuery.put(LT_OPERATOR, criterion.getValue());

                query.put(propertyName, lessThanQuery);
            }
        });

        queryHandlers.put(LessThanEquals.class, new QueryHandler<LessThanEquals>() {
            public void handle(EmbeddedQueryEncoder queryEncoder, LessThanEquals criterion, Document query, PersistentEntity entity) {
                String propertyName = getPropertyName(entity, criterion);
                Document lessThanQuery = getOrCreatePropertyQuery(query, propertyName);
                lessThanQuery.put(LTE_OPERATOR, criterion.getValue());

                query.put(propertyName, lessThanQuery);
            }
        });

        queryHandlers.put(Conjunction.class, new QueryHandler<Conjunction>() {
            public void handle(EmbeddedQueryEncoder queryEncoder, Conjunction criterion, Document query, PersistentEntity entity) {
                populateBsonQuery(queryEncoder, query, criterion, entity);
            }
        });

        queryHandlers.put(Negation.class, new QueryHandler<Negation>() {
            @SuppressWarnings("unchecked")
            public void handle(EmbeddedQueryEncoder queryEncoder, Negation criteria, Document query, PersistentEntity entity) {
                List nor = new ArrayList();
                query.put(NOR_OPERATOR, nor);
                for (Criterion criterion : criteria.getCriteria()) {
                    Document negatedQuery = new Document();
                    nor.add(negatedQuery);
                    if(criterion instanceof PropertyCriterion) {
                        PropertyCriterion pc = (PropertyCriterion) criterion;
                        PersistentProperty property = entity.getPropertyByName(pc.getProperty());
                        if (property instanceof Custom) {
                            CustomTypeMarshaller customTypeMarshaller = ((Custom) property).getCustomTypeMarshaller();
                            customTypeMarshaller.query(property, pc, query);
                            continue;
                        }
                    }

                    final QueryHandler queryHandler = queryHandlers.get(criterion.getClass());
                    if (queryHandler != null) {
                        queryHandler.handle(queryEncoder, criterion, negatedQuery, entity);
                    } else {
                        throw new UnsupportedOperationException("Query of type " + criterion.getClass().getSimpleName() + " cannot be negated");
                    }
                }
            }
        });

        queryHandlers.put(Disjunction.class, new QueryHandler<Disjunction>() {
            @SuppressWarnings("unchecked")
            public void handle(EmbeddedQueryEncoder queryEncoder, Disjunction criterion, Document query, PersistentEntity entity) {
                populateBsonQuery(queryEncoder, query, criterion, entity);
            }
        });

        queryHandlers.put(SizeEquals.class, new QueryHandler<SizeEquals>() {
            @SuppressWarnings("unchecked")
            public void handle(EmbeddedQueryEncoder queryEncoder, SizeEquals criterion, Document query, PersistentEntity entity) {
                String propertyName = getPropertyName(entity, criterion);
                Document sizeEqualsQuery = getOrCreatePropertyQuery(query, propertyName);
                sizeEqualsQuery.put(SIZE_OPERATOR, getNumber(criterion));

                query.put(propertyName, sizeEqualsQuery);
            }
        });

        queryHandlers.put(SizeNotEquals.class, new QueryHandler<SizeNotEquals>() {
            public void handle(EmbeddedQueryEncoder queryEncoder, SizeNotEquals criterion, Document query, PersistentEntity entity) {
                String propertyName = getPropertyName(entity, criterion);
                Document sizeNotEqualsQuery = getOrCreatePropertyQuery(query, propertyName);
                sizeNotEqualsQuery.put(NOT_OPERATOR, new Document(SIZE_OPERATOR, getNumber(criterion)));

                query.put(propertyName, sizeNotEqualsQuery);
            }
        });

        queryHandlers.put(SizeGreaterThan.class, new QueryHandler<SizeGreaterThan>() {
            @SuppressWarnings("unchecked")
            public void handle(EmbeddedQueryEncoder queryEncoder, SizeGreaterThan criterion, Document query, PersistentEntity entity) {
                String propertyName = getPropertyName(entity, criterion);
                Integer greaterThanValue = getNumber(criterion);

                query.put(propertyName + '.' + greaterThanValue, new Document(EXISTS_OPERATOR, true));
            }
        });

        queryHandlers.put(SizeLessThan.class, new QueryHandler<SizeLessThan>() {
            @SuppressWarnings("unchecked")
            public void handle(EmbeddedQueryEncoder queryEncoder, SizeLessThan criterion, Document query, PersistentEntity entity) {
                String propertyName = getPropertyName(entity, criterion);
                Integer lessThanValue = getNumber(criterion);

                query.put(propertyName + '.' + (lessThanValue - 1), new Document(EXISTS_OPERATOR, 0));
            }
        });

        queryHandlers.put(SizeLessThanEquals.class, new QueryHandler<SizeLessThanEquals>() {
            @SuppressWarnings("unchecked")
            public void handle(EmbeddedQueryEncoder queryEncoder, SizeLessThanEquals criterion, Document query, PersistentEntity entity) {
                String propertyName = getPropertyName(entity, criterion);
                Integer lessThanValue = getNumber(criterion);

                query.put(propertyName + '.' + lessThanValue, new Document(EXISTS_OPERATOR, 0));
            }
        });

        queryHandlers.put(SizeGreaterThanEquals.class, new QueryHandler<SizeGreaterThanEquals>() {
            @SuppressWarnings("unchecked")
            public void handle(EmbeddedQueryEncoder queryEncoder, SizeGreaterThanEquals criterion, Document query, PersistentEntity entity) {
                String propertyName = getPropertyName(entity, criterion);
                Integer greaterThanValue = getNumber(criterion);

                query.put(propertyName + '.' + (greaterThanValue - 1), new Document(EXISTS_OPERATOR, true));
            }
        });


        operatorHandlers.put(GT_OPERATOR, new OperatorHandler() {
            @Override
            public void handle(Junction criteria, String attributeName, BsonReader queryReader) {
                Object value = readBsonValue(queryReader, queryReader.getCurrentBsonType());
                if(value != null && !(value instanceof NullObject)) {
                    criteria.add(new Query.GreaterThan(attributeName, value));
                }
            }
        });

        operatorHandlers.put(GTE_OPERATOR, new OperatorHandler() {
            @Override
            public void handle(Junction criteria, String attributeName, BsonReader queryReader) {
                Object value = readBsonValue(queryReader, queryReader.getCurrentBsonType());
                if(value != null && !(value instanceof NullObject)) {
                    criteria.add(new Query.GreaterThanEquals(attributeName, value));
                }
            }
        });

        operatorHandlers.put(LT_OPERATOR, new OperatorHandler() {
            @Override
            public void handle(Junction criteria, String attributeName, BsonReader queryReader) {
                Object value = readBsonValue(queryReader, queryReader.getCurrentBsonType());
                if(value != null && !(value instanceof NullObject)) {
                    criteria.add(new Query.LessThan(attributeName, value));
                }
            }
        });
        operatorHandlers.put(LTE_OPERATOR, new OperatorHandler() {
            @Override
            public void handle(Junction criteria, String attributeName, BsonReader queryReader) {
                Object value = readBsonValue(queryReader, queryReader.getCurrentBsonType());
                if(value != null && !(value instanceof NullObject)) {
                    criteria.add(new Query.LessThanEquals(attributeName, value));
                }
            }
        });
        operatorHandlers.put(NE_OPERATOR, new OperatorHandler() {
            @Override
            public void handle(Junction criteria, String attributeName, BsonReader queryReader) {
                Object value = readBsonValue(queryReader, queryReader.getCurrentBsonType());
                if(value != null && !(value instanceof NullObject)) {
                    criteria.add(new Query.NotEquals(attributeName, value));
                }
            }
        });
        operatorHandlers.put(EQ_OPERATOR, new OperatorHandler() {
            @Override
            public void handle(Junction criteria, String attributeName, BsonReader queryReader) {
                Object value = readBsonValue(queryReader, queryReader.getCurrentBsonType());
                if(value != null) {
                    criteria.add(new Query.Equals(attributeName, value));
                }
            }
        });

        operatorHandlers.put(REGEX_OPERATOR, new OperatorHandler() {
            @Override
            public void handle(Junction criteria, String attributeName, BsonReader queryReader) {
                Object value = readBsonValue(queryReader, queryReader.getCurrentBsonType());
                if(value != null && !(value instanceof NullObject)) {
                    criteria.add(new Query.RLike(attributeName, value.toString()));
                }
            }
        });

        operatorHandlers.put(OR_OPERATOR, new OperatorHandler() {
            @Override
            public void handle(Junction criteria, String attributeName, BsonReader queryReader) {
                Disjunction disjunction = new Disjunction();
                criteria.add(disjunction);

                queryReader.readStartArray();
                BsonType bsonType = queryReader.readBsonType();
                parseJunctionDocuments(disjunction, attributeName, queryReader, bsonType);
                queryReader.readEndArray();
            }
        });

        operatorHandlers.put(IN_OPERATOR, new OperatorHandler() {
            @Override
            public void handle(Junction criteria, String attributeName, BsonReader queryReader) {
                List values = readArrayOfValues(queryReader);
                criteria.add(new Query.In(attributeName, values));
            }
        });

        operatorHandlers.put(NIN_OPERATOR, new OperatorHandler() {
            @Override
            public void handle(Junction criteria, String attributeName, BsonReader queryReader) {
                Negation negation = new Negation();
                List values = readArrayOfValues(queryReader);
                negation.add(new Query.In(attributeName, values));
                criteria.add(negation);
            }
        });

        operatorHandlers.put(NOT_OPERATOR, new OperatorHandler() {
            @Override
            public void handle(Junction criteria, String attributeName, BsonReader queryReader) {
                Negation negation = new Negation();
                criteria.add(negation);

                queryReader.readStartArray();
                BsonType bsonType = queryReader.readBsonType();
                parseJunctionDocuments(negation, attributeName, queryReader, bsonType);
                queryReader.readEndArray();
            }
        });

        operatorHandlers.put(AND_OPERATOR, new OperatorHandler() {
            @Override
            public void handle(Junction criteria, String attributeName, BsonReader queryReader) {
                Conjunction conj = new Conjunction();
                criteria.add(conj);

                queryReader.readStartArray();
                BsonType bsonType = queryReader.readBsonType();
                parseJunctionDocuments(conj, attributeName, queryReader, bsonType);
                queryReader.readEndArray();
            }
        });
    }

    private static List readArrayOfValues(BsonReader queryReader) {
        List values = new ArrayList();
        BsonType bsonType = queryReader.getCurrentBsonType();
        if(bsonType == BsonType.ARRAY) {
            queryReader.readStartArray();
            bsonType = queryReader.readBsonType();
            while(bsonType != BsonType.END_OF_DOCUMENT) {
                Object value = readBsonValue(queryReader, queryReader.getCurrentBsonType());
                if(value instanceof NullObject) {
                    value = null;
                }
                values.add(value);
                bsonType = queryReader.readBsonType();
            }

            queryReader.readEndArray();
        }
        return values;
    }

    private static void parseJunctionDocuments(Junction junction, String attributeName, BsonReader queryReader, BsonType bsonType) {
        while(bsonType != BsonType.END_OF_DOCUMENT) {
            if(bsonType == BsonType.DOCUMENT) {
                parseQueryAttributeValue(queryReader, bsonType, attributeName, junction);
            }
            else {
                queryReader.skipValue();
            }
            bsonType = queryReader.readBsonType();
        }
    }

    protected BsonQuery(Session session, PersistentEntity entity) {
        super(session, entity);
    }

    protected BsonQuery(PersistentEntity entity) {
        super(null, entity);
    }

    /**
     * Creates a new query for the given registry, entity and criteria
     *
     * @param registry The registry
     * @param entity   The entity
     * @param criteria The criteria
     * @return The query
     */
    public static Document createBsonQuery(CodecRegistry registry, PersistentEntity entity, List<Criterion> criteria) {
        Conjunction junction = new Conjunction(criteria);
        return createBsonQuery(registry, entity, junction);
    }

    /**
     * Creates a new query for the given registry, entity and criteria
     *
     * @param registry The registry
     * @param entity   The entity
     * @param junction The junction
     * @return The query
     */
    public static Document createBsonQuery(CodecRegistry registry, PersistentEntity entity, Junction junction) {
        EmbeddedQueryEncoder embeddedQueryEncoder = new CodecRegistryEmbeddedQueryEncoder(registry);
        Document query = new Document();
        if(junction instanceof Conjunction) {
            populateBsonQuery(embeddedQueryEncoder, query, junction.getCriteria(), entity);
        }
        else {
            populateBsonQuery(embeddedQueryEncoder, query, junction, entity);
        }
        return query;
    }

    /**
     * Parse a query from a BsonReader into a DetachedCriteria
     *
     * @param type        The entity type
     * @param queryReader The query reader
     * @param <T>         The entity concrete type
     * @return A {@link DetachedCriteria}
     */
    public static <T> DetachedCriteria<T> parse(Class<T> type, BsonReader queryReader) {
        queryReader.readStartDocument();
        BsonType bsonType = queryReader.getCurrentBsonType();
        DetachedCriteria<T> criteria = new DetachedCriteria<>(type);
        Query.Junction junction = new Query.Conjunction();

        if (bsonType != BsonType.END_OF_DOCUMENT) {
            String attributeName = queryReader.readName();
            boolean isJunction = false;
            switch (attributeName) {
                case OR_OPERATOR:
                    junction = new Disjunction();
                    isJunction = true;
                    break;
                case NOT_OPERATOR:
                    junction = new Negation();
                    isJunction = true;
                    break;
                case AND_OPERATOR:
                    junction = new Conjunction();
                    isJunction = true;
                    break;
                default:
                    parseQueryAttributeValue(queryReader, queryReader.getCurrentBsonType(), attributeName, junction);
                    bsonType = queryReader.readBsonType();
                    break;
            }

            criteria.add(junction);

            if(isJunction) {
                queryReader.readStartArray();
                bsonType = queryReader.readBsonType();
                while(bsonType != BsonType.END_OF_DOCUMENT) {
                    if(bsonType == BsonType.DOCUMENT) {
                        parseQueryAttributeValue(queryReader, queryReader.getCurrentBsonType(), attributeName, junction);
                    }
                    else {
                        queryReader.skipValue();
                    }
                    bsonType = queryReader.readBsonType();
                }
                queryReader.readEndArray();
            }
            else {
                while(bsonType != BsonType.END_OF_DOCUMENT) {
                    attributeName = queryReader.readName();
                    bsonType = queryReader.getCurrentBsonType();
                    parseQueryAttributeValue(queryReader, bsonType, attributeName, junction);
                    bsonType = queryReader.readBsonType();
                }
            }

        }

        return criteria;
    }

    private static BsonType parseQueryAttributeValue(BsonReader queryReader, BsonType bsonType, String attributeName, Junction junction) {
        if (bsonType == BsonType.DOCUMENT) {
            queryReader.readStartDocument();
            bsonType = queryReader.getCurrentBsonType();
            while(bsonType != BsonType.END_OF_DOCUMENT) {
                String operator = queryReader.readName();
                OperatorHandler operatorHandler = operatorHandlers.get(operator);
                if(operatorHandler != null) {
                    operatorHandler.handle(junction, attributeName, queryReader);
                }
                else {
                    parseQueryAttributeValue(queryReader, queryReader.getCurrentBsonType(), operator, junction);
                }
                bsonType = queryReader.readBsonType();
            }
            queryReader.readEndDocument();
        } else {
            Object value = readBsonValue(queryReader, bsonType);
            if(value != null) {
                if(value instanceof NullObject) {
                    junction.add(new IsNull(attributeName));
                }
                else {
                    junction.add(new Equals(attributeName, value));
                }
            }
        }
        return bsonType;
    }

    protected static Object readBsonValue(BsonReader queryReader, BsonType bsonType) {
        Object value = null;
        switch (bsonType) {
            case STRING:
                value = queryReader.readString();
                break;
            case INT32:
                value = queryReader.readInt32();
                break;
            case INT64:
                value = queryReader.readInt64();
                break;
            case BOOLEAN:
                value = queryReader.readBoolean();
                break;
            case DOUBLE:
                value = queryReader.readDouble();
                break;
            case BINARY:
                value = queryReader.readBinaryData().getData();
                break;
            case REGULAR_EXPRESSION:
                value = queryReader.readRegularExpression().getPattern();
                break;
            case DATE_TIME:
                value = new Date(queryReader.readDateTime());
                break;
            case OBJECT_ID:
                value = queryReader.readObjectId();
                break;
            case NULL:
                value = NullObject.getNullObject();
                break;
            default:
                queryReader.skipValue();

        }
        return value;
    }

    protected static String getPropertyName(PersistentEntity entity, PropertyNameCriterion criterion) {
        String propertyName = criterion.getProperty();
        return getPropertyName(entity, propertyName);
    }

    protected static String getPropertyName(PersistentEntity entity, String propertyName) {
        if (entity.isIdentityName(propertyName)) {
            String targetIdentityName = entity.getIdentity().getMapping().getMappedForm().getTargetName();
            if (targetIdentityName != null) {
                propertyName = targetIdentityName;
            }
        } else {
            PersistentProperty property = entity.getPropertyByName(propertyName);
            if (property != null) {
                propertyName = MappingUtils.getTargetKey(property);
                if (property instanceof ToOne && !(property instanceof Embedded)) {
                    ToOne association = (ToOne) property;
                    Property attr = association.getMapping().getMappedForm();
                    boolean isReference = attr == null || attr.isReference();
                    if (isReference) {
                        propertyName = propertyName + ID_REFERENCE_SUFFIX;
                    }
                }
            }
        }

        return propertyName;
    }

    private static void addWherePropertyComparison(Document query, String propertyName, String otherPropertyName, String operator) {
        query.put(WHERE_OPERATOR, new StringBuilder(THIS_PREFIX).append(propertyName).append(operator).append(THIS_PREFIX).append(otherPropertyName).toString());
    }

    private static void handleLike(PersistentEntity entity, Like like, Document query, boolean caseSensitive) {
        Object value = like.getValue();
        String expr = patternToRegex(value);

        Pattern regex = caseSensitive ? Pattern.compile(expr) : Pattern.compile(expr, Pattern.CASE_INSENSITIVE);
        String propertyName = getPropertyName(entity, like);
        query.put(propertyName, regex);
    }

    /**
     * Get the list of native values to use in the query. This converts entities to ids and other types to
     * their persisted types.
     *
     * @param entity The entity
     * @param in     The criterion
     * @return The list of native values suitable for passing to Mongo.
     */
    protected static List<Object> getInListQueryValues(PersistentEntity entity, In in) {
        List<Object> values = new ArrayList<Object>(in.getValues().size());
        final MappingContext mappingContext = entity.getMappingContext();
        for (Object value : in.getValues()) {
            if (mappingContext.isPersistentEntity(value)) {
                PersistentEntity pe = mappingContext.getPersistentEntity(
                        value.getClass().getName());
                ProxyHandler proxyHandler = mappingContext.getProxyHandler();
                if(proxyHandler.isProxy(value)) {
                    values.add(proxyHandler.getIdentifier(value));
                }
                else {
                    EntityReflector reflector = mappingContext.getEntityReflector(pe);
                    values.add(reflector.getIdentifier(value));
                }
            } else {
                values.add(value);
            }
        }
        return values;
    }

    protected static Document getOrCreatePropertyQuery(Document query, String propertyName) {
        Object existing = query.get(propertyName);
        Document queryObject = existing instanceof Document ? (Document) existing : null;
        if (queryObject == null) {
            queryObject = new Document();
        }
        return queryObject;
    }

    private static Integer getNumber(PropertyCriterion criterion) {
        Object value = criterion.getValue();
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        throw new IllegalArgumentException("Argument to size constraint must be a number");
    }

    /**
     * Handles an individual criterion
     *
     * @param <T>
     */
    protected interface QueryHandler<T> {
        void handle(EmbeddedQueryEncoder queryEncoder, T criterion, Document query, PersistentEntity entity);
    }

    /**
     * Handles query operators when reading BSON
     */
    protected interface OperatorHandler {
        void handle(Junction criteria, String attributeName, BsonReader queryReader);
    }

    /**
     *
     * Handles a projection
     *
     * @param <T>
     */
    protected interface ProjectionHandler<T extends Projection> {
        /**
         * Handles a projection modifying the aggregation pipeline appropriately
         *
         * @param entity        The entity
         * @param groupByObject The group by object
         * @param projection    The projection
         * @return The key to be used to obtain the projected value from the pipeline results
         */
        String handle(PersistentEntity entity, Document projectObject, Document groupByObject, T projection);
    }

    protected static void populateBsonQuery(final EmbeddedQueryEncoder queryEncoder, Document query, List<Criterion> criteria, final PersistentEntity entity) {
        // if a query combines more than 1 item, wrap the items in individual $and or $or arguments
        // so that property names can't clash (e.g. for an $and containing two $ors)
        for (Criterion criterion : criteria) {
            final QueryHandler queryHandler = queryHandlers.get(criterion.getClass());
            if (queryHandler != null) {
                Document dbo = query;
                if (criterion instanceof PropertyCriterion) {
                    PropertyCriterion pc = (PropertyCriterion) criterion;
                    PersistentProperty property = entity.getPropertyByName(pc.getProperty());
                    if (property instanceof Custom) {
                        CustomTypeMarshaller customTypeMarshaller = ((Custom) property).getCustomTypeMarshaller();
                        if(!(customTypeMarshaller instanceof CodecCustomTypeMarshaller)) {
                            customTypeMarshaller.query(property, pc, query);
                            continue;
                        }
                    }
                }
                queryHandler.handle(queryEncoder, criterion, dbo, entity);
            } else {
                throw new InvalidDataAccessResourceUsageException("Queries of type " + criterion.getClass().getSimpleName() + " are not supported by this implementation");
            }
        }
    }

    protected static void populateBsonQuery(final EmbeddedQueryEncoder queryEncoder, Document query, Junction criteria, final PersistentEntity entity) {
        List subList = null;
        if (criteria instanceof Disjunction) {
            subList = new ArrayList();
            query.put(OR_OPERATOR, subList);
        } else if (criteria instanceof Conjunction) {
            subList = new ArrayList();
            query.put(AND_OPERATOR, subList);
        } else if (criteria instanceof Negation) {
            subList = new ArrayList();
            query.put(NOT_OPERATOR, new Document(OR_OPERATOR, subList));
        }

        for (Criterion criterion : criteria.getCriteria()) {
            final QueryHandler queryHandler = queryHandlers.get(criterion.getClass());
            if (queryHandler != null) {
                Document dbo = query;
                if (subList != null) {
                    dbo = new Document();
                    subList.add(dbo);
                }

                if (criterion instanceof PropertyCriterion) {
                    PropertyCriterion pc = (PropertyCriterion) criterion;
                    PersistentProperty property = entity.getPropertyByName(pc.getProperty());
                    if (property instanceof Custom) {
                        CustomTypeMarshaller customTypeMarshaller = ((Custom) property).getCustomTypeMarshaller();
                        customTypeMarshaller.query(property, pc, query);
                        continue;
                    }
                }
                queryHandler.handle(queryEncoder, criterion, dbo, entity);
            } else {
                throw new InvalidDataAccessResourceUsageException("Queries of type " + criterion.getClass().getSimpleName() + " are not supported by this implementation");
            }
        }
    }
}
