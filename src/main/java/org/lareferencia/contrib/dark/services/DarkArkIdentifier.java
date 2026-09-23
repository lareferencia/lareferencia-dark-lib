package org.lareferencia.contrib.dark.services;

/** Canonical representation used by the dARK minter: {@code ark:NAAN/name}. */
public final class DarkArkIdentifier {

    private static final String LEGACY_PREFIX = "ark:/";
    private static final String CANONICAL_PREFIX = "ark:";

    private DarkArkIdentifier() {
    }

    /**
     * Accept the historical {@code ark:/NAAN/name} spelling at system boundaries,
     * but never persist or send it to the current minter API.
     */
    public static String normalize(String ark) {
        if (ark == null) {
            return null;
        }
        String value = ark.trim();
        return value.regionMatches(true, 0, LEGACY_PREFIX, 0, LEGACY_PREFIX.length())
                ? CANONICAL_PREFIX + value.substring(LEGACY_PREFIX.length())
                : value;
    }
}
