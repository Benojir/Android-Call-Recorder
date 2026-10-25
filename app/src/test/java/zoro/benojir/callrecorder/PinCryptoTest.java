package zoro.benojir.callrecorder;
import org.junit.Test;
import zoro.benojir.callrecorder.helpers.PinCrypto;
import static org.junit.Assert.*;

public class PinCryptoTest {
    @Test public void saltAndPinBothAffectVerifier() throws Exception {
        byte[] salt = new byte[16];
        byte[] hash = PinCrypto.derive("123456".toCharArray(), salt);
        assertTrue(PinCrypto.matches(hash, PinCrypto.derive("123456".toCharArray(), salt)));
        assertFalse(PinCrypto.matches(hash, PinCrypto.derive("123457".toCharArray(), salt)));
        salt[0] = 1;
        assertFalse(PinCrypto.matches(hash, PinCrypto.derive("123456".toCharArray(), salt)));
    }
    @Test public void shortAndNonNumericPinsAreRejected() {
        assertFalse(PinCrypto.valid("12345")); assertFalse(PinCrypto.valid("abcd12"));
        assertTrue(PinCrypto.valid("123456")); assertFalse(PinCrypto.valid("1234567890123"));
    }
}
