package dev.metaplus.core.util;

import dev.metaplus.core.exception.MetaplusException;
import lombok.NonNull;

public final class IdeaUtil {


    public static String[] segmentsFromFqmn(@NonNull String fqmn) {
        String[] segments = fqmn.split(":");
        if (segments.length != 4) throw new MetaplusException("Invalid fqmn: " + fqmn);
        return segments;
    }

    public static String domainFromFqmn(@NonNull String fqmn) {
        return segmentsFromFqmn(fqmn)[0];
    }


}
