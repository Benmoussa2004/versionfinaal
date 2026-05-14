package com.example.demo.Entity;

public enum Role {
    CLIENT, // External user - submits transport requests
    COMMERCIAL, // Commercial actor - manages transport requests
    ADMIN, // Administrator - manages platform and users
    EMPLOYER_LUMIERE // Lumière employee - manages orders
}
