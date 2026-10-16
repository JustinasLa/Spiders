package net.tfminecraft.spiders.utilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringsTest {
    @Test
    @DisplayName("non-breaking and other Unicode spaces are trimmed like ordinary ones")
    void unicodeSpacesAreTrimmed() {
        assertEquals("EMPTY", Strings.trim("\u00A0 EMPTY\u2007\t"));
        assertEquals("a b", Strings.trim(" a b "));
        assertEquals("", Strings.trim("\u00A0\u202F"));
        assertTrue(Strings.isBlank("\u00A0 \u3000"));
        assertFalse(Strings.isBlank(" x "));
    }
}
