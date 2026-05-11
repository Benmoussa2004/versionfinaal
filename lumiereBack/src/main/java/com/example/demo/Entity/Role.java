package com.example.demo.Entity;

public enum Role {
    CLIENT, // External user - submits transport requests
    COMMERCIAL, // Commercial actor - manages transport requests
    ADMIN, // Administrator - manages platform and users
    USER_LUMIERE, // Lumière employee - manages orders
    USER_OTFLOW, // Mobile app user role
    USER, // Legacy - treat as CLIENT
    SUPERADMIN // Legacy - treat as ADMIN
}
