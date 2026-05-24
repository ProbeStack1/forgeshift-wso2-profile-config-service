package com.forgeshift.profile.config.tenant;

/**
 * Thread-local tenant context populated by {@link TenantInterceptor} from
 * the X-Partner-Id header. Cleared at the end of every request.
 */
public final class TenantContext {

    private static final ThreadLocal<String> TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(String tenantId) {
        TENANT.set(tenantId);
    }

    public static String get() {
        return TENANT.get();
    }

    public static void clear() {
        TENANT.remove();
    }
}
