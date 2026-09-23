package org.lareferencia.contrib.dark.worker;

/** Snapshot of the in-memory progress exposed for a selected manual command. */
public record DarkManualProgress(String phase, int processed, int succeeded, int skipped, int failed) { }
