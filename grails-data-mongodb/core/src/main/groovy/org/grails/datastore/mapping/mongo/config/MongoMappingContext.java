/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.mongo.config;

import com.mongodb.ConnectionString;
import groovy.lang.Closure;

import java.beans.PropertyDescriptor;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecConfigurationException;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.bson.types.*;
import org.grails.datastore.bson.codecs.*;
import org.grails.datastore.bson.codecs.encoders.SimpleEncoder;
import org.grails.datastore.gorm.mongo.geo.*;
import org.grails.datastore.gorm.mongo.simple.EnumType;
import org.grails.datastore.mapping.config.AbstractGormMappingFactory;
import org.grails.datastore.mapping.config.ConfigurationUtils;
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings;
import org.grails.datastore.mapping.document.config.Attribute;
import org.grails.datastore.mapping.document.config.Collection;
import org.grails.datastore.mapping.document.config.DocumentMappingContext;
import org.grails.datastore.mapping.model.*;

import org.grails.datastore.mapping.model.types.Custom;
import org.grails.datastore.mapping.model.types.Identity;
import org.grails.datastore.mapping.mongo.MongoConstants;
import org.grails.datastore.mapping.mongo.MongoDatastore;
import org.grails.datastore.mapping.mongo.connections.AbstractMongoConnectionSourceSettings;
import org.grails.datastore.mapping.reflect.ClassUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.core.env.PropertyResolver;

/**
 * Models a {@link org.grails.datastore.mapping.model.MappingContext} for Mongo.
 *
 * @author Graeme Rocher
 */
@SuppressWarnings("rawtypes")
public class MongoMappingContext extends DocumentMappingContext {
    private static final String DECIMAL_TYPE_CLASS_NAME = "org.bson.types.Decimal128";
    /**
     * Java types supported as mongo property types.
     */
    private static final Set<String> MONGO_NATIVE_TYPES = new HashSet<>(Arrays.asList(
            Double.class.getName(),
            String.class.getName(),
            Document.class.getName(),
            "com.mongodb.DBObject",
            org.bson.types.Binary.class.getName(),
            org.bson.types.ObjectId.class.getName(),
            "com.mongodb.DBRef",
            Boolean.class.getName(),
            Date.class.getName(),
            Pattern.class.getName(),
            Symbol.class.getName(),
            Integer.class.getName(),
            Code.class.getName(),
            "org.bson.types.BSONTimestamp",
            DECIMAL_TYPE_CLASS_NAME,
            "org.bson.types.CodeWScope",
            "org.bson.types.Code",
            "org.bson.types.Binary",
            Long.class.getName(),
            UUID.class.getName(),
            byte[].class.getName(),
            Byte.class.getName()

    ));

    private CodecRegistry codecRegistry;
    private Map<Class, Boolean> hasCodecCache = new HashMap<>();

    public MongoMappingContext(String defaultDatabaseName) {
        this(defaultDatabaseName, null);
    }

    public MongoMappingContext(String defaultDatabaseName, Closure defaultMapping) {
        this(defaultDatabaseName, defaultMapping, new Class[0]);
    }

    /**
     * Constructs a new {@link MongoMappingContext} for the given arguments
     *
     * @param defaultDatabaseName The default database name
     * @param defaultMapping The default database mapping configuration
     * @param classes The persistent classes
     */
    public MongoMappingContext(String defaultDatabaseName, Closure defaultMapping, Class...classes) {
        super(defaultDatabaseName, defaultMapping);
        initialize(classes);

    }

    /**
     * Constructs a new {@link MongoMappingContext} for the given arguments
     *
     * @param configuration The configuration
     * @param classes The persistent classes
     * @deprecated  Use {@link #MongoMappingContext(AbstractMongoConnectionSourceSettings, Class[])} instead
     *
     */
    @Deprecated
    public MongoMappingContext(PropertyResolver configuration, Class...classes) {
        this(getDefaultDatabaseName(configuration), configuration.getProperty(MongoSettings.SETTING_DEFAULT_MAPPING, Closure.class, null), classes);
    }

    /**
     * Construct a new context for the given settings and classes
     *
     * @param settings The settings
     * @param classes The classes
     */
    public MongoMappingContext(AbstractMongoConnectionSourceSettings settings, Class... classes) {
        super(settings.getDatabase(), settings);
        initialize(classes);
    }

    /**
     * @return The codec registry for this mapping context
     */
    public CodecRegistry getCodecRegistry() {
        return codecRegistry;
    }

    @Override
    protected void initialize(ConnectionSourceSettings settings) {
        super.initialize(settings);

        AbstractMongoConnectionSourceSettings mongoConnectionSourceSettings = (AbstractMongoConnectionSourceSettings) settings;
        List<Class<? extends Codec>> codecClasses = mongoConnectionSourceSettings.getCodecs();

        if(mongoConnectionSourceSettings.isDecimalType() && ClassUtils.isPresent(DECIMAL_TYPE_CLASS_NAME)) {
            MONGO_NATIVE_TYPES.add(BigDecimal.class.getName());
            MONGO_NATIVE_TYPES.add(BigInteger.class.getName());
            SimpleEncoder.enableBigDecimalEncoding();

            codecClasses.add(BigDecimalCodec.class);
        }


        Iterable<Codec> codecList = ConfigurationUtils.findServices(codecClasses, Codec.class);
        List<Codec<?>> codecs = new ArrayList<>();
        codecs.add(new InstantCodec());
        codecs.add(new LocalDateCodec());
        codecs.add(new LocalDateTimeCodec());
        codecs.add(new LocalTimeCodec());
        codecs.add(new OffsetDateTimeCodec());
        codecs.add(new OffsetTimeCodec());
        codecs.add(new PeriodCodec());
        codecs.add(new ZonedDateTimeCodec());

        for (Codec codec : codecList) {
            codecs.add(codec);
        }

        if(mongoConnectionSourceSettings.getCodecRegistry() != null) {
            this.codecRegistry = CodecRegistries.fromRegistries(
                    mongoConnectionSourceSettings.getCodecRegistry(),
                    CodecRegistries.fromCodecs(codecs)
            );
        }
        else {
            this.codecRegistry = CodecRegistries.fromCodecs(codecs);
        }
    }

    private void initialize(Class[] classes) {
        registerMongoTypes();
        final ConverterRegistry converterRegistry = getConverterRegistry();

        converterRegistry.addConverter(new Converter<String, ObjectId>() {
            public ObjectId convert(String source) {
                if(ObjectId.isValid(source)) {
                    return new ObjectId(source);
                }
                else {
                    return null;
                }
            }
        });

        converterRegistry.addConverter(new Converter<ObjectId, String>() {
            public String convert(ObjectId source) {
                return source.toString();
            }
        });

        converterRegistry.addConverter(new Converter<byte[], Binary>() {
            public Binary convert(byte[] source) {
                return new Binary(source);
            }
        });

        converterRegistry.addConverter(new Converter<Binary, byte[]>() {
            public byte[] convert(Binary source) {
                return source.getData();
            }
        });

        converterRegistry.addConverter(new Converter<Decimal128, BigDecimal>() {
            @Override
            public BigDecimal convert(Decimal128 source) {
                return source.bigDecimalValue();
            }
        });

        converterRegistry.addConverter(new Converter<BigDecimal,Decimal128>() {
            @Override
            public Decimal128 convert(BigDecimal source) {
                return new Decimal128(source);
            }
        });

        converterRegistry.addConverter(new Converter<Decimal128, BigInteger>() {
            @Override
            public BigInteger convert(Decimal128 source) {
                return source.bigDecimalValue().toBigInteger();
            }
        });

        converterRegistry.addConverter(new Converter<BigInteger,Decimal128>() {
            @Override
            public Decimal128 convert(BigInteger source) {
                return new Decimal128(new BigDecimal(source.toString()));
            }
        });


        for (Converter converter : CodecExtensions.getBsonConverters()) {
            converterRegistry.addConverter(converter);
        }

        addPersistentEntities(classes);
        hasCodecCache.clear();
    }

    /**
     * Check whether a type is a native mongo type that can be stored by the mongo driver without conversion.
     * @param clazz The class to check.
     * @return true if no conversion is required and the type can be stored natively.
     */
    public static boolean isMongoNativeType(Class clazz) {
        return MongoMappingContext.MONGO_NATIVE_TYPES.contains(clazz.getName()) ||
                Bson.class.isAssignableFrom(clazz.getClass()) ;
    }

    public static String getDefaultDatabaseName(PropertyResolver configuration) {
        String connectionString = configuration.getProperty(MongoDatastore.SETTING_CONNECTION_STRING, String.class, null);

        if (connectionString != null) {
            String database = new ConnectionString(connectionString).getDatabase();
            if(database != null) {
                return database;
            }
        }
        return configuration.getProperty(MongoSettings.SETTING_DATABASE_NAME, "test");
    }

    private final class MongoDocumentMappingFactory extends
            AbstractGormMappingFactory<MongoCollection, MongoAttribute> {
        @Override
        protected Class<MongoAttribute> getPropertyMappedFormType() {
            return MongoAttribute.class;
        }

        @Override
        protected Class<MongoCollection> getEntityMappedFormType() {
            return MongoCollection.class;
        }


        @Override
        public Identity<MongoAttribute> createIdentity(PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
            Identity<MongoAttribute> identity = super.createIdentity(owner, context, pd);
            identity.getMapping().getMappedForm().setTargetName(MongoConstants.MONGO_ID_FIELD);
            return identity;
        }

        @Override
        public boolean isCustomType(Class<?> propertyType) {
            return super.isCustomType(propertyType) || hasCodecForType(propertyType);
        }

        @Override
        public Custom<MongoAttribute> createCustom(PersistentEntity owner, MappingContext context, final PropertyDescriptor pd) {
            if(hasCodecForType(pd.getPropertyType())) {
                CodecCustomTypeMarshaller customTypeMarshaller = new CodecCustomTypeMarshaller(codecRegistry.get(pd.getPropertyType()), MongoMappingContext.this);
                return new Custom<MongoAttribute>(owner, context, pd, customTypeMarshaller) {
                    PropertyMapping<MongoAttribute> propertyMapping = createPropertyMapping(this, owner);
                    public PropertyMapping<MongoAttribute> getMapping() {
                        return propertyMapping;
                    }
                };
            }
            else {
                return super.createCustom(owner, context, pd);
            }
        }

        @Override
        public boolean isSimpleType(Class propType) {
            if (propType == null) return false;
            if (propType.isArray()) {
                return isSimpleType(propType.getComponentType()) || super.isSimpleType(propType);
            }
            return isMongoNativeType(propType)  || super.isSimpleType(propType);
        }
    }

    private boolean hasCodecForType(Class propType) {
        if(hasCodecCache.containsKey(propType)) {
            return hasCodecCache.get(propType);
        }
        else {
            Boolean hasCodec;
            try {
                hasCodec = codecRegistry.get(propType) != null;
            } catch (CodecConfigurationException e) {
                hasCodec = false;
            }
            hasCodecCache.put(propType, hasCodec);
            return hasCodec;
        }
    }


    protected void registerMongoTypes() {
        MappingFactory<Collection, Attribute> mappingFactory = getMappingFactory();
        mappingFactory.registerCustomType(new GeometryCollectionType());
        mappingFactory.registerCustomType(new PointType());
        mappingFactory.registerCustomType(new PolygonType());
        mappingFactory.registerCustomType(new LineStringType());
        mappingFactory.registerCustomType(new MultiLineStringType());
        mappingFactory.registerCustomType(new MultiPointType());
        mappingFactory.registerCustomType(new MultiPolygonType());
        mappingFactory.registerCustomType(new ShapeType());
        mappingFactory.registerCustomType(new BoxType());
        mappingFactory.registerCustomType(new CircleType());
        mappingFactory.registerCustomType(new EnumType());
    }

    @Override
    protected MappingFactory createDocumentMappingFactory(Closure defaultMapping) {
        MongoDocumentMappingFactory mongoDocumentMappingFactory = new MongoDocumentMappingFactory();
        mongoDocumentMappingFactory.setDefaultMapping(defaultMapping);
        return mongoDocumentMappingFactory;
    }

    @Override
    public PersistentEntity createEmbeddedEntity(Class type) {
        return new DocumentEmbeddedPersistentEntity(type, this);
    }

    class DocumentEmbeddedPersistentEntity extends EmbeddedPersistentEntity {

        private DocumentCollectionMapping classMapping;

        public DocumentEmbeddedPersistentEntity(Class type, MappingContext ctx) {
            super(type, ctx);
            classMapping = new DocumentCollectionMapping(this, ctx);
        }

        @Override
        public boolean isIdentityName(String propertyName) {
            return false;
        }

        @Override
        public ClassMapping getMapping() {
            return classMapping;
        }

        public class DocumentCollectionMapping extends AbstractClassMapping<Collection> {
            private Collection mappedForm;

            public DocumentCollectionMapping(PersistentEntity entity, MappingContext context) {
                super(entity, context);
                this.mappedForm = (Collection) context.getMappingFactory().createMappedForm(DocumentEmbeddedPersistentEntity.this);
            }
            @Override
            public Collection getMappedForm() {
                return mappedForm ;
            }
        }
    }
}
