package com.vswitch.watermeter;

public record QuotaStepDto(double atLitersUsed, String action, Double value) {}
