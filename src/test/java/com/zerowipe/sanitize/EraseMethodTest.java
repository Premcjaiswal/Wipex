package com.zerowipe.sanitize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.zerowipe.sanitize.EraseMethod.NistCategory;
import org.junit.jupiter.api.Test;

class EraseMethodTest {

    @Test
    void zeroFillAchievesClear() {
        assertEquals(NistCategory.CLEAR, EraseMethod.ZERO_FILL.nistCategory());
    }

    @Test
    void dod3PassLegacyAchievesClear() {
        assertEquals(NistCategory.CLEAR, EraseMethod.DOD_3PASS_LEGACY.nistCategory());
    }

    @Test
    void cryptoEraseAchievesPurge() {
        assertEquals(NistCategory.PURGE, EraseMethod.CRYPTO_ERASE.nistCategory());
    }

    @Test
    void everyMethodMapsToANistCategory() {
        for (EraseMethod method : EraseMethod.values()) {
            assertNotNull(method.nistCategory(), method + " has no NIST category");
        }
    }

    @Test
    void exactlyTheThreeMethodsOfferedOnScreenTwoExist() {
        assertEquals(3, EraseMethod.values().length);
    }
}
