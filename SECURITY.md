# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in Thorold or its data, please report it
responsibly:

1. **Do NOT open a public issue** for security vulnerabilities
2. Email: (mailto: dennisgathu8@gmail.com)
3. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

We will acknowledge receipt within 48 hours and provide a timeline for a fix.

## Scope

The following are in scope for security reports:

- **Data integrity:** Corrupted or incorrect ID mappings
- **API vulnerabilities:** Injection, authentication bypass, rate limit circumvention
- **Batch request limits:** `/batch/lookup` and `/batch/resolve` enforce a 100-item
  server-side cap, returning HTTP 400 before any query logic runs if exceeded
- **EDN content negotiation:** `wrap-content-negotiation` is output-only — it serializes
  responses via `pr-str` and never calls `read-string`, `eval`, `load-string`, or
  `load-file` on request input
- **Credential exposure:** API keys, tokens, or PII in committed files
- **Dependency vulnerabilities:** Known CVEs in project dependencies

## Data Privacy

- Thorold processes publicly available football data from Wikidata (CC0 licensed)
- No personally identifiable information beyond public athlete data is stored
- API keys are never committed to the repository
- All credentials are loaded from environment variables

## Supported Versions

| Version | Supported |
|---------|-----------|
| Latest  | ✅        |
