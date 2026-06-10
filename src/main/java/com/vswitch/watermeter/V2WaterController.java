package com.vswitch.watermeter;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class V2WaterController {

    private final WaterReadingService waterReadingService;
    private final UnitService unitService;
    private final UserService userService;

    V2WaterController(
            WaterReadingService waterReadingService,
            UnitService unitService,
            UserService userService) {
        this.waterReadingService = waterReadingService;
        this.unitService = unitService;
        this.userService = userService;
    }

    @GetMapping("/v2/tenants/{tenantId}/devices/{deviceId}/water/valve")
    ValveStateResponse getValve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @PathVariable String deviceId) {
        requireDevice(tenantId, deviceId, jwt);
        return waterReadingService.getValveState(deviceId, tenantId);
    }

    @PutMapping("/v2/tenants/{tenantId}/devices/{deviceId}/water/valve")
    ValveStateResponse updateValve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @PathVariable String deviceId,
            @RequestBody ValveUpdateRequest request) {
        requireDevice(tenantId, deviceId, jwt);
        return waterReadingService.updateValve(deviceId, request);
    }

    private void requireDevice(String tenantId, String deviceId, Jwt jwt) {
        userService.requireTenantMember(jwt.getSubject(), tenantId);
        unitService
                .findByTenantAndDeviceId(tenantId, deviceId)
                .orElseThrow(
                        () ->
                                new org.springframework.web.server.ResponseStatusException(
                                        org.springframework.http.HttpStatus.NOT_FOUND,
                                        "Unit not found"));
    }
}
