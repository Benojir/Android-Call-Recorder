package zoro.benojir.callrecorder.helpers;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PinCrypto {
    private PinCrypto() { }
    public static byte[] derive(char[] pin, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(pin, salt, 120000, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); java.util.Arrays.fill(pin, '\0'); }
    }
    public static boolean matches(byte[] actual, byte[] expected) { return MessageDigest.isEqual(actual, expected); }
    public static boolean valid(String pin) { return pin != null && pin.matches("[0-9]{6,12}"); }
}
