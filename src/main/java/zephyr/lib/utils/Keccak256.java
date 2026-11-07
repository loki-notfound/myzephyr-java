package zephyr.lib.utils;

/**
 * Keccak-256 as CryptoNote uses it for address checksums: the Keccak-f[1600] permutation with a
 * rate of 136 bytes, a 256-bit output and the original Keccak padding (a 0x01 domain byte).
 *
 * This is not SHA3-256, which pads with 0x06 and gives different digests. The class depends on
 * nothing outside the JDK, so it behaves the same on every Android version: Android 7 and 8 put
 * their own copy of Bouncy Castle on the boot class path, and that copy has no Keccak.
 */
public final class Keccak256 {

  /** Bytes absorbed per permutation: (1600 - 2 * 256) / 8. */
  private static final int RATE = 136;

  /** Digest length in bytes. */
  private static final int DIGEST_LENGTH = 32;

  /** Round constants of the iota step, one per round. */
  private static final long[] ROUND_CONSTANTS = {
    0x0000000000000001L, 0x0000000000008082L, 0x800000000000808AL, 0x8000000080008000L,
    0x000000000000808BL, 0x0000000080000001L, 0x8000000080008081L, 0x8000000000008009L,
    0x000000000000008AL, 0x0000000000000088L, 0x0000000080008009L, 0x000000008000000AL,
    0x000000008000808BL, 0x800000000000008BL, 0x8000000000008089L, 0x8000000000008003L,
    0x8000000000008002L, 0x8000000000000080L, 0x000000000000800AL, 0x800000008000000AL,
    0x8000000080008081L, 0x8000000000008080L, 0x0000000080000001L, 0x8000000080008008L
  };

  /** Rotation offsets of the rho step, indexed [x][y]. */
  private static final int[][] ROTATIONS = {
    { 0, 36,  3, 41, 18},
    { 1, 44, 10, 45,  2},
    {62,  6, 43, 15, 61},
    {28, 55, 25, 21, 56},
    {27, 20, 39,  8, 14}
  };

  private Keccak256() {}

  /**
   * Computes the Keccak-256 digest of the given bytes.
   *
   * @param input the bytes to hash
   * @return the 32-byte digest
   */
  public static byte[] hash(byte[] input) {
    long[] state = new long[25]; // lane (x, y) at index x + 5 * y

    // absorb every full block
    int offset = 0;
    while (input.length - offset >= RATE) {
      xorBlock(state, input, offset, RATE);
      permute(state);
      offset += RATE;
    }

    // the last block with the Keccak padding: 0x01 after the message, 0x80 in the last byte
    byte[] last = new byte[RATE];
    int remaining = input.length - offset;
    System.arraycopy(input, offset, last, 0, remaining);
    last[remaining] ^= 0x01;
    last[RATE - 1] ^= (byte) 0x80;
    xorBlock(state, last, 0, RATE);
    permute(state);

    // squeeze: the digest fits in the first four lanes, little-endian
    byte[] digest = new byte[DIGEST_LENGTH];
    for (int i = 0; i < DIGEST_LENGTH; i++) {
      digest[i] = (byte) (state[i / 8] >>> (8 * (i % 8)));
    }
    return digest;
  }

  /** XORs a block of little-endian 64-bit lanes into the state. */
  private static void xorBlock(long[] state, byte[] data, int offset, int length) {
    for (int i = 0; i < length; i++) {
      state[i / 8] ^= (data[offset + i] & 0xFFL) << (8 * (i % 8));
    }
  }

  /** The Keccak-f[1600] permutation: 24 rounds of theta, rho, pi, chi and iota. */
  private static void permute(long[] a) {
    long[] c = new long[5];
    long[] b = new long[25];
    for (int round = 0; round < 24; round++) {
      // theta: XOR each lane with the parities of two neighbouring columns
      for (int x = 0; x < 5; x++) {
        c[x] = a[x] ^ a[x + 5] ^ a[x + 10] ^ a[x + 15] ^ a[x + 20];
      }
      for (int x = 0; x < 5; x++) {
        long d = c[(x + 4) % 5] ^ Long.rotateLeft(c[(x + 1) % 5], 1);
        for (int y = 0; y < 5; y++) {
          a[x + 5 * y] ^= d;
        }
      }

      // rho and pi: rotate each lane and move (x, y) to (y, 2x + 3y)
      for (int x = 0; x < 5; x++) {
        for (int y = 0; y < 5; y++) {
          b[y + 5 * ((2 * x + 3 * y) % 5)] = Long.rotateLeft(a[x + 5 * y], ROTATIONS[x][y]);
        }
      }

      // chi: the only non-linear step, along each row
      for (int x = 0; x < 5; x++) {
        for (int y = 0; y < 5; y++) {
          a[x + 5 * y] = b[x + 5 * y] ^ (~b[(x + 1) % 5 + 5 * y] & b[(x + 2) % 5 + 5 * y]);
        }
      }

      // iota
      a[0] ^= ROUND_CONSTANTS[round];
    }
  }
}
