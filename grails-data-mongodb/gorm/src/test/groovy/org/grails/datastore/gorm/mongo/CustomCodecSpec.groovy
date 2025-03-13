package org.grails.datastore.gorm.mongo

import grails.gorm.time.InstantConverter
import grails.mongodb.MongoEntity
import grails.persistence.Entity
import org.bson.BsonDateTime
import org.bson.BsonDocumentWrapper
import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.types.ObjectId
import org.grails.datastore.bson.codecs.decoders.SimpleDecoder
import org.grails.datastore.bson.codecs.encoders.SimpleEncoder
import org.grails.datastore.bson.codecs.temporal.TemporalBsonConverter
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.mongo.MongoDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

import static java.time.temporal.ChronoUnit.DAYS

/**
 * Created by graemerocher on 27/09/2016.
 */
class CustomCodecSpec extends Specification {

    @AutoCleanup @Shared MongoDatastore datastore = new MongoDatastore(
            ['grails.mongodb.codecs':[BirthdayCodec,
                                      InstantAsBsonDateTimeCodec
            ]],
            Person, InstantHolder)

    void "Test custom codecs"() {
        when:"A new person is saved"
        Person.DB.drop()
        def birthday = new Birthday(new Date())
        new Person(name: "Fred", birthday: birthday).save(flush:true)

        Person p = Person.first()

        then:"The result is correct"
        p.name == "Fred"
        p.birthday
        Person.findByBirthday(birthday)
        !Person.findByBirthday(new Birthday(new Date() - 7))
    }

    void "Test codec overriding for simple type Instant"() {
        setup:
        def defaultInstantDecoder = SimpleDecoder.SIMPLE_TYPE_DECODERS[Instant]
        def defaultInstantEncoder = SimpleEncoder.SIMPLE_TYPE_ENCODERS[Instant]
        SimpleDecoder.SIMPLE_TYPE_DECODERS[Instant] = new InstantAsBsonDateTimeDecoder()
        SimpleEncoder.SIMPLE_TYPE_ENCODERS[Instant] = new InstantAsBsonDateTimeEncoder()

        when:"A new instant holder is saved"
        InstantAsBsonDateTimeCodec.resetCounts()
        InstantHolder.DB.drop()
        def instant = Instant.now()
        def holder = new InstantHolder(anInstant: instant)

        def codecRegistry = InstantHolder.collection.codecRegistry
        def wrapper = new BsonDocumentWrapper(holder, codecRegistry.get(InstantHolder))
        def serializedInstant = wrapper.get('anInstant')

        holder.save(flush:true)
        InstantHolder ih = InstantHolder.first()

        then:"The serialization is correct"
        serializedInstant.class == BsonDateTime
        codecRegistry.get(Instant).class == InstantAsBsonDateTimeCodec
        ih.anInstant
        InstantAsBsonDateTimeCodec.encodeCount.get() == 0
        InstantHolder.findByAnInstant(instant)
        InstantAsBsonDateTimeCodec.encodeCount.get() == 1
        !InstantHolder.findByAnInstant(instant.minus(7, DAYS))
        InstantAsBsonDateTimeCodec.encodeCount.get() == 2
        InstantAsBsonDateTimeCodec.decodeCount.get() == 0

        cleanup:
        SimpleDecoder.SIMPLE_TYPE_DECODERS[Instant] = defaultInstantDecoder
        SimpleEncoder.SIMPLE_TYPE_ENCODERS[Instant] = defaultInstantEncoder
    }
}

class BirthdayCodec implements Codec<Birthday> {
    Birthday decode(BsonReader reader, DecoderContext decoderContext) {
        return new Birthday(new Date(reader.readDateTime()))
    }
    void encode(BsonWriter writer, Birthday value, EncoderContext encoderContext) {
        writer.writeDateTime(value.date.time)
    }
    Class<Birthday> getEncoderClass() { Birthday }
}

class InstantAsBsonDateTimeCodec implements Codec<Instant> {
    public static AtomicLong decodeCount = new AtomicLong()
    public static AtomicLong encodeCount = new AtomicLong()
    static void resetCounts() {
        decodeCount.set(0)
        encodeCount.set(0)
    }

    Instant decode(BsonReader reader, DecoderContext decoderContext) {
        decodeCount.incrementAndGet()
        return Instant.ofEpochMilli(reader.readDateTime())
    }

    void encode(BsonWriter writer, Instant value, EncoderContext encoderContext) {
        encodeCount.incrementAndGet()
        writer.writeDateTime(value.toEpochMilli())
    }

    Class<Instant> getEncoderClass() { Instant }
}

trait InstantAsBsonDateTimeConverter implements TemporalBsonConverter<Instant>, InstantConverter {

    @Override
    void write(BsonWriter writer, Instant value) {
        writer.writeDateTime(value.toEpochMilli())
    }

    @Override
    Instant read(BsonReader reader) {
        Instant.ofEpochMilli(reader.readDateTime())
    }

    @Override
    BsonType bsonType() {
        BsonType.DATE_TIME
    }

}

class InstantAsBsonDateTimeEncoder implements SimpleEncoder.TypeEncoder, InstantAsBsonDateTimeConverter {
    @Override
    void encode(BsonWriter writer, PersistentProperty property, Object value) {
        write(writer, (Instant)value)
    }
}

class InstantAsBsonDateTimeDecoder implements SimpleDecoder.TypeDecoder, InstantAsBsonDateTimeConverter {
    @Override
    void decode(BsonReader reader, PersistentProperty property, EntityAccess entityAccess) {
        entityAccess.setPropertyNoConversion(property.name, read(reader))
    }
}

@Entity
class InstantHolder implements MongoEntity<InstantHolder> {
    ObjectId id
    Instant anInstant
}
