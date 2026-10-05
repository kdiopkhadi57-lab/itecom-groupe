package com.elearning.service;

import org.junit.jupiter.api.Test;

import static com.elearning.service.ContactValidator.Usage.*;
import static org.junit.jupiter.api.Assertions.*;

class ContactValidatorTest {

    @Test
    void acceptsRealEmailsAndNormalizesCase() {
        assertEquals("prenom.nom@gmail.com", ContactValidator.email("  Prenom.Nom@Gmail.com "));
        assertEquals("a.b+itecom@ucad.edu.sn", ContactValidator.email("a.b+itecom@ucad.edu.sn"));
    }

    @Test
    void refusesFancifulEmails() {
        for (String bad : new String[]{"", "awa", "awa@", "@gmail.com", "awa@gmail", "awa@@gmail.com", "awa diop@gmail.com",
                                       "awa@gmail.c", "awa@gmail.123", "awa..diop@gmail.com", ".awa@gmail.com", "awa@-gmail.com",
                                       "awa@gmail..com", "awa@gmail.com.", "aaa@bbb", "n'importe quoi"}) {
            assertThrows(IllegalArgumentException.class, () -> ContactValidator.email(bad), bad);
        }
        var typo = assertThrows(IllegalArgumentException.class, () -> ContactValidator.email("awa@gmial.com"));
        assertTrue(typo.getMessage().contains("awa@gmail.com"));
    }

    @Test
    void normalizesSenegalesePhoneNumbers() {
        assertEquals("+221 77 123 45 67", ContactValidator.phone("77 123 45 67", ANY));
        assertEquals("+221 77 123 45 67", ContactValidator.phone("+221771234567", ANY));
        assertEquals("+221 78 123 45 67", ContactValidator.phone("00221 78.123.45.67", ANY));
        assertEquals("+221 33 821 00 00", ContactValidator.phone("33 821 00 00", ANY));
        assertEquals("+33612345678", ContactValidator.phone("+33 6 12 34 56 78", ANY));
    }

    @Test
    void refusesFancifulPhoneNumbers() {
        for (String bad : new String[]{"", "abc", "77 12 34", "7712345678", "12 345 67 89", "79 123 45 67",
                                       "77 777 77 77", "77 123 45 6a", "+12", "771234567x"}) {
            assertThrows(IllegalArgumentException.class, () -> ContactValidator.phone(bad, ANY), bad);
        }
    }

    @Test
    void mobilePaymentNumberMustMatchOperator() {
        assertEquals("+221 76 123 45 67", ContactValidator.phone("76 123 45 67", MOBILE));          // Wave : tout mobile
        assertThrows(IllegalArgumentException.class, () -> ContactValidator.phone("33 821 00 00", MOBILE));
        assertThrows(IllegalArgumentException.class, () -> ContactValidator.phone("+33612345678", MOBILE));
        assertThrows(IllegalArgumentException.class, () -> ContactValidator.phone("76 123 45 67", ORANGE_MONEY));
        assertThrows(IllegalArgumentException.class, () -> ContactValidator.phone("77 123 45 67", FREE_MONEY));
        assertEquals(ORANGE_MONEY, ContactValidator.usageFor("ORANGE_MONEY"));
        assertEquals(MOBILE, ContactValidator.usageFor("WAVE"));
    }
}
