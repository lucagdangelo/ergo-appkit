package org.ergoplatform.appkit;

public class Signature {
    private Signature() {
        // prevent instantiation
    }

    /**
     * Verifies a signature on given (arbitrary) message for a given public key.
     *
     * @param sigmaProp Sigma proposition the message should be signed with
     * @param message   message to verify
     * @param signature signature for the message
     * @return whether signature is valid or not
     */
    public static boolean verifySignature(SigmaProp sigmaProp, byte[] message, byte[] signature) {
        return SigmaPropInterpreter.verifySignature(sigmaProp.getSigmaBoolean(), message, signature);
    }
}
