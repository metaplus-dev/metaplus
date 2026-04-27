package dev.metaplus.backend.server.domain;

public final class StorageUtil {

    public static String getDomainIndex(String domain) {
        return "i_metaplus_domain_" + domain;
    }

}
