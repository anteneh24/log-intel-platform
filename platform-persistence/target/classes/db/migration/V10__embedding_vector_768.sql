-- Switch embedding vector columns to 768 dimensions for local Ollama
-- model `embeddinggemma:300m` (returns 768-length vectors).

DROP INDEX IF EXISTS log_embeddings_hnsw;
DROP INDEX IF EXISTS code_embeddings_hnsw;
DROP INDEX IF EXISTS incident_embeddings_hnsw;

ALTER TABLE log_embeddings
    ALTER COLUMN embedding TYPE vector(768);

ALTER TABLE code_embeddings
    ALTER COLUMN embedding TYPE vector(768);

ALTER TABLE incident_embeddings
    ALTER COLUMN embedding TYPE vector(768);

CREATE INDEX log_embeddings_hnsw ON log_embeddings USING hnsw (embedding vector_cosine_ops);
CREATE INDEX code_embeddings_hnsw ON code_embeddings USING hnsw (embedding vector_cosine_ops);
CREATE INDEX incident_embeddings_hnsw ON incident_embeddings USING hnsw (embedding vector_cosine_ops);

