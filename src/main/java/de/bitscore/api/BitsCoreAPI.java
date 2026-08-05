package de.bitscore.api;

import de.bitscore.exception.BitsProviderNotSetException;

public class BitsCoreAPI {

    private static BitsProvider provider;

    public static BitsProvider getProvider() {
        if (provider == null) {
            throw new BitsProviderNotSetException("BitsProvider is not set. Is BitsCore enabled?");
        }
        return provider;
    }

    public static void setProvider(BitsProvider provider) {
        BitsCoreAPI.provider = provider;
    }
}
