package com.zerowipe.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class RefusalReasonTest {

    @Test
    void everyRefusalReasonHasAHumanReadableMessage() {
        for (RefusalReason reason : RefusalReason.values()) {
            String message = reason.message();
            assertNotNull(message, reason + " has no message");
            assertFalse(message.isBlank(), reason + " has a blank message");
        }
    }
}
