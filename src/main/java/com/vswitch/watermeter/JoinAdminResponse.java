package com.vswitch.watermeter;

public record JoinAdminResponse(
        String tenantId, boolean onboardingComplete, boolean isTenantOwner) {}
