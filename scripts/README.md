# Scripts

## refresh.clj

Runs the Wikidata SPARQL ingestion pipeline to regenerate CSV data files.

### Prerequisites

- [Babashka](https://babashka.org/) for the lightweight script runner, or
- `clj` for the full Clojure pipeline

### Usage

```bash
bb scripts/refresh.clj --mode incremental   # new and updated entities only
bb scripts/refresh.clj --mode full          # rebuild entire dataset
bb scripts/refresh.clj --dry-run            # print steps without writing
```

### Schedule

Run incremental refreshes weekly. Run full refreshes monthly.
