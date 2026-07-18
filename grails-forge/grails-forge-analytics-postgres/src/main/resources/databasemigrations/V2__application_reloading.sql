-- The Application entity replaced its build tool and test framework fields with a
-- development reloading field (TestFramework -> DevelopmentReloading refactor), but the
-- baseline schema was never updated, so persisting an Application failed: the reloading
-- column did not exist and the entity does not populate the NOT NULL build_tool /
-- test_framework columns. Add the reloading column the entity persists (and that
-- FeatureRepository.topReloading queries). Keep build_tool with a GRADLE default so
-- FeatureRepository.topBuildTools continues to report a meaningful value when the
-- entity omits the unmapped column on insert. Relax test_framework (no longer mapped
-- or queried).
ALTER TABLE application ADD COLUMN IF NOT EXISTS reloading varchar(255) NOT NULL DEFAULT 'DEVTOOLS';
ALTER TABLE application ALTER COLUMN build_tool SET DEFAULT 'GRADLE';
ALTER TABLE application ALTER COLUMN test_framework DROP NOT NULL;
