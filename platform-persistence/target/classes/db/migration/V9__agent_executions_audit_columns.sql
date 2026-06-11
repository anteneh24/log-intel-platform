-- Align agent_executions with AgentExecutionEntity (extends V6 table shape).
ALTER TABLE agent_executions
    ADD COLUMN IF NOT EXISTS trace_id TEXT,
    ADD COLUMN IF NOT EXISTS status TEXT,
    ADD COLUMN IF NOT EXISTS latency_ms BIGINT;
