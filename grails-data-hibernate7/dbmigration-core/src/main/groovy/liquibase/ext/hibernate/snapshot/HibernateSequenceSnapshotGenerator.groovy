package liquibase.ext.hibernate.snapshot

import groovy.transform.CompileStatic
import liquibase.Scope
import liquibase.exception.DatabaseException
import liquibase.ext.hibernate.database.HibernateDatabase
import liquibase.snapshot.DatabaseSnapshot
import liquibase.snapshot.InvalidExampleException
import liquibase.snapshot.SnapshotGenerator
import liquibase.snapshot.jvm.SequenceSnapshotGenerator
import liquibase.structure.DatabaseObject
import liquibase.structure.core.Schema
import liquibase.structure.core.Sequence
import org.hibernate.boot.model.relational.Namespace
import org.hibernate.boot.spi.MetadataImplementor
import org.hibernate.dialect.Dialect
import org.hibernate.generator.Generator
import org.hibernate.id.NativeGenerator
import org.hibernate.id.enhanced.DatabaseStructure
import org.hibernate.id.enhanced.SequenceStyleGenerator
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.SimpleValue

/**
 * Snapshots sequences from Hibernate metadata, including sequences managed by {@code @NativeGenerator} or
 * {@code SequenceStyleGenerator} identifiers that are not registered in the relational namespace. This class also
 * prevents the default SequenceSnapshotGenerator from running.
 */
@CompileStatic
class HibernateSequenceSnapshotGenerator extends HibernateSnapshotGenerator {

    HibernateSequenceSnapshotGenerator() {
        super(Sequence, Schema)
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        return example
    }

    @Override
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        if (!snapshot.getSnapshotControl().shouldInclude(Sequence)) {
            return
        }

        if (foundObject instanceof Schema) {
            Schema schema = (Schema) foundObject
            HibernateDatabase database = (HibernateDatabase) snapshot.getDatabase()
            Set<IdentifierGeneratorSupport.SequenceKey> addedSequences = new HashSet<>()

            for (Namespace namespace : database.getMetadata().getDatabase().getNamespaces()) {
                for (org.hibernate.boot.model.relational.Sequence sequence : namespace.getSequences()) {
                    if (!addedSequences.add(IdentifierGeneratorSupport.sequenceKey(sequence.getName()))) {
                        continue
                    }
                    addSequence(schema, sequence.getName().getSequenceName().getText(),
                            sequence.getInitialValue(), sequence.getIncrementSize())
                }
            }

            addGeneratorSequences(database, schema, addedSequences)
        }
    }

    private void addGeneratorSequences(HibernateDatabase database, Schema schema,
            Set<IdentifierGeneratorSupport.SequenceKey> addedSequences) {
        MetadataImplementor metadata = (MetadataImplementor) database.getMetadata()
        Dialect dialect = database.getDialect()

        for (PersistentClass entityBinding : metadata.getEntityBindings()) {
            if (!(entityBinding instanceof RootClass) ||
                    !(((RootClass) entityBinding).getIdentifier() instanceof SimpleValue) ||
                    !IdentifierGeneratorSupport.hasGenerationIntent((SimpleValue) ((RootClass) entityBinding).getIdentifier())) {
                continue
            }
            RootClass rootClass = (RootClass) entityBinding
            SimpleValue simpleValue = (SimpleValue) rootClass.getIdentifier()

            try {
                Generator generator = simpleValue.createGenerator(
                        dialect,
                        rootClass,
                        rootClass.getIdentifierProperty(),
                        IdentifierGeneratorSupport.createGeneratorSettings(simpleValue))

                SequenceStyleGenerator seqGen = null
                // NativeGenerator may wrap a SequenceStyleGenerator delegate depending on the dialect.
                if (generator instanceof NativeGenerator) {
                    Generator delegate = IdentifierGeneratorSupport.nativeDelegate((NativeGenerator) generator)
                    if (delegate instanceof SequenceStyleGenerator) {
                        seqGen = (SequenceStyleGenerator) delegate
                    }
                } else if (generator instanceof SequenceStyleGenerator) {
                    seqGen = (SequenceStyleGenerator) generator
                }

                if (seqGen != null) {
                    DatabaseStructure structure = seqGen.getDatabaseStructure()
                    if (structure != null && structure.isPhysicalSequence() && structure.getPhysicalName() != null) {
                        def physicalName = structure.getPhysicalName()
                        if (addedSequences.add(IdentifierGeneratorSupport.sequenceKey(physicalName))) {
                            addSequence(schema, physicalName.getObjectName().getText(),
                                    structure.getInitialValue(), structure.getIncrementSize())
                        }
                    }
                }
            } catch (RuntimeException e) {
                Scope.getCurrentScope().getLog(getClass())
                        .fine('Could not resolve generator for ' + rootClass.getEntityName(), e)
            }
        }
    }

    private void addSequence(Schema snapshotSchema, String bareName, int initialValue, int incrementSize) {
        Sequence liquibaseSequence = new Sequence()
        liquibaseSequence.setName(bareName)
        liquibaseSequence.setSchema(snapshotSchema)
        liquibaseSequence.setStartValue(BigInteger.valueOf(initialValue))
        liquibaseSequence.setIncrementBy(BigInteger.valueOf(incrementSize))
        snapshotSchema.addDatabaseObject(liquibaseSequence)
    }

    @Override
    Class<? extends SnapshotGenerator>[] replaces() {
        return [SequenceSnapshotGenerator] as Class<? extends SnapshotGenerator>[]
    }

}
