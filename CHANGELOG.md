# Changelog

All notable changes to Thorold will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Initial Clojure re-implementation of Reep
- Data model with Malli schemas (`thorold.model`)
- Reep ID generation (`thorold.id`)
- CSV parsers for people, teams, and names (`thorold.parse`)
- Index builders: by-reep-id, by-provider, by-qid, by-name (`thorold.index`)
- Database assembly from CSV files (`thorold.db`)
- Query functions: search, resolve, translate, lookup (`thorold.query`)
- Wikidata SPARQL ingestion pipeline (`thorold.ingest`)
- CLI with all original Reep commands (`thorold.cli`)
- REST API with Ring/Reitit (`thorold.api`)
- REPL demo walkthrough (`dev/repl_demo.clj`)
- Full test suite for all modules
