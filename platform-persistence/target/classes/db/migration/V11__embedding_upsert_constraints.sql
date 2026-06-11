-- Required for BatchConsumer ON CONFLICT upserts.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'code_embeddings_repo_git_sha_file_path_fqn_key'
    ) THEN
        ALTER TABLE code_embeddings
            ADD CONSTRAINT code_embeddings_repo_git_sha_file_path_fqn_key
                UNIQUE (repo, git_sha, file_path, fqn);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'incident_embeddings_incident_id_key'
    ) THEN
        ALTER TABLE incident_embeddings
            ADD CONSTRAINT incident_embeddings_incident_id_key
                UNIQUE (incident_id);
    END IF;
END $$;
