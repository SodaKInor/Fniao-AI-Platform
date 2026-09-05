# Bootstrap

`002-local-sanitize.sql` is the version-controlled cleanup step for a disposable copy of the approved private baseline. It clears historical logs and runtime bindings and nulls external credentials or author-machine endpoints while preserving the schema, users, permissions, menus and dictionaries.

The private baseline is intentionally absent from Git. Do not apply this cleanup script to an active database or existing application volume; restore a separate copy first and verify the resulting table and row summaries.
