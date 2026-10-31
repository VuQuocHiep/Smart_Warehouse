package com.example.iot.service;

import com.example.iot.config.JwtKey;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtKeyTest {
    @Test void cloudRejectsMissingOrWeakSecret() {
        assertThrows(IllegalStateException.class,()->new JwtKey("",true));
        assertThrows(IllegalStateException.class,()->new JwtKey("short",true));
    }
    @Test void localKeysAreEphemeralAndConfiguredKeyIsStable() {
        assertFalse(java.util.Arrays.equals(new JwtKey("",false).bytes(),new JwtKey("",false).bytes()));
        assertArrayEquals(new JwtKey("x".repeat(32),true).bytes(),new JwtKey("x".repeat(32),false).bytes());
    }
}
