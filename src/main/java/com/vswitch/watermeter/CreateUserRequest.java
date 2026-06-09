package com.vswitch.watermeter;

public record CreateUserRequest(
        String email, String phone, String firstName, String lastName) {}
