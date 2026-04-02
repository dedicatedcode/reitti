ALTER TABLE immich_integrations ADD COLUMN use_best_guess_location BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE immich_integrations ALTER COLUMN use_best_guess_location DROP DEFAULT;