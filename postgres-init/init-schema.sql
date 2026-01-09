-- Initialize schema and extensions for J-RAG
\c jrag

CREATE SCHEMA IF NOT EXISTS jrag_core;
CREATE EXTENSION IF NOT EXISTS vector SCHEMA public;
