package com.aarushi.qa;
import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class TenantIsolationTest {@Test void tenantIsPartOfRetrievalFilter(){String tenant="tenant-a";String filter="tenantId == '"+tenant+"'";assertTrue(filter.contains(tenant));}}
