package io.music_assistant.client.player.sendspin.noise

/**
 * Reference handshake + transport vectors for the Noise protocol suites used by
 * (or adjacent to) Sendspin encryption, embedded as JSON so every test target
 * (JVM host tests, iOS simulator tests) can read them without platform
 * resource-loading machinery.
 *
 * Provenance: extracted from the cacophony vector set distributed with the
 * `snow` Noise implementation (https://github.com/mcginty/snow,
 * tests/vectors/cacophony.txt), filtered to the 25519_ChaChaPoly_SHA256
 * suites relevant here. See player/sendspin/noise/README.md for how to
 * regenerate.
 */
internal object NoiseTestVectors {
    const val json: String = """
{
  "vectors": [
    {
      "protocol_name": "Noise_KK_25519_ChaChaPoly_SHA256",
      "init_prologue": "4a6f686e2047616c74",
      "init_static": "e61ef9919cde45dd5f82166404bd08e38bceb5dfdfded0a34c8df7ed542214d1",
      "init_ephemeral": "893e28b9dc6ca8d611ab664754b8ceb7bac5117349a4439a6b0569da977c464a",
      "init_remote_static": "31e0303fd6418d2f8c0e78b91f22e8caed0fbe48656dcf4767e4834f701b8f62",
      "resp_prologue": "4a6f686e2047616c74",
      "resp_static": "4a3acbfdb163dec651dfa3194dece676d437029c62a408b4c5ea9114246e4893",
      "resp_ephemeral": "bbdb4cdbd309f1a1f2e1456967fe288cadd6f712d65dc7b7793d5e63da6b375b",
      "resp_remote_static": "6bc3822a2aa7f4e6981d6538692b3cdf3e6df9eea6ed269eb41d93c22757b75a",
      "handshake_hash": "24c6b51ecb76277140ca018b5985bc9f03de321dae2d34dcae433dafef0131d9",
      "messages": [
        {
          "payload": "4c756477696720766f6e204d69736573",
          "ciphertext": "ca35def5ae56cec33dc2036731ab14896bc4c75dbb07a61f879f8e3afa4c79440177015efc1fe7a37c629af7120a96274e6ab7afcc9261901d0e09ae32a5bb96"
        },
        {
          "payload": "4d757272617920526f746862617264",
          "ciphertext": "95ebc60d2b1fa672c1f46a8aa265ef51bfe38e7ccb39ec5be34069f144808843b274d3429adc47ca093ba63ef90f8da89fda108db471dccfa4894aa7b00003"
        },
        {
          "payload": "462e20412e20486179656b",
          "ciphertext": "966b05bc69ec01b8454d3160a214e6f24a3d884eb31ec2408af63f"
        },
        {
          "payload": "4361726c204d656e676572",
          "ciphertext": "0ad887fba4f611bbb4afe44ba3556b8164332ca7d5934634d63d80"
        },
        {
          "payload": "4a65616e2d426170746973746520536179",
          "ciphertext": "012b28ae646ae7830e2c5472cb023eab071c1db3d8413ec69b513b83832f974c2d"
        },
        {
          "payload": "457567656e2042f6686d20766f6e2042617765726b",
          "ciphertext": "bb3e6a48160d9c5971d37f975727294e0d868342db31832e54d07191ab0ca3c3703b5ed3d9"
        }
      ]
    },
    {
      "protocol_name": "Noise_NNpsk2_25519_ChaChaPoly_SHA256",
      "init_prologue": "4a6f686e2047616c74",
      "init_psks": [
        "54686973206973206d7920417573747269616e20706572737065637469766521"
      ],
      "init_ephemeral": "893e28b9dc6ca8d611ab664754b8ceb7bac5117349a4439a6b0569da977c464a",
      "resp_prologue": "4a6f686e2047616c74",
      "resp_psks": [
        "54686973206973206d7920417573747269616e20706572737065637469766521"
      ],
      "resp_ephemeral": "bbdb4cdbd309f1a1f2e1456967fe288cadd6f712d65dc7b7793d5e63da6b375b",
      "handshake_hash": "bb9704f2303bd8b98b40fdb2ee50c2a9a46d7d20ea4d0949ae3094e376b29b1c",
      "messages": [
        {
          "payload": "4c756477696720766f6e204d69736573",
          "ciphertext": "ca35def5ae56cec33dc2036731ab14896bc4c75dbb07a61f879f8e3afa4c7944d44698de33ea6b7eea8023b48a284404489f9976c5f03417e8e2d6db7ab6bb9f"
        },
        {
          "payload": "4d757272617920526f746862617264",
          "ciphertext": "95ebc60d2b1fa672c1f46a8aa265ef51bfe38e7ccb39ec5be34069f14480884361200acbacd001a0d19a826982488f52573687652551ca5e903db095fedc7a"
        },
        {
          "payload": "462e20412e20486179656b",
          "ciphertext": "5ac8678baf0ef0cf884ab3271236b7ee57a02519505f4a4be09b95"
        },
        {
          "payload": "4361726c204d656e676572",
          "ciphertext": "fe899e844ac0d348a3ab679b83c95fd1099f734a0dc085955adce2"
        },
        {
          "payload": "4a65616e2d426170746973746520536179",
          "ciphertext": "f8800be62325c8bd6794f7e533bb90316c6ba569a4223e644175f4e5e458e840fd"
        },
        {
          "payload": "457567656e2042f6686d20766f6e2042617765726b",
          "ciphertext": "2f60885aedcd5b5c142a3190208b540407ab4477528ea8d15bd795416575e58121098a4a9f"
        }
      ]
    },
    {
      "protocol_name": "Noise_KKpsk2_25519_ChaChaPoly_SHA256",
      "init_prologue": "4a6f686e2047616c74",
      "init_psks": [
        "54686973206973206d7920417573747269616e20706572737065637469766521"
      ],
      "init_static": "e61ef9919cde45dd5f82166404bd08e38bceb5dfdfded0a34c8df7ed542214d1",
      "init_ephemeral": "893e28b9dc6ca8d611ab664754b8ceb7bac5117349a4439a6b0569da977c464a",
      "init_remote_static": "31e0303fd6418d2f8c0e78b91f22e8caed0fbe48656dcf4767e4834f701b8f62",
      "resp_prologue": "4a6f686e2047616c74",
      "resp_psks": [
        "54686973206973206d7920417573747269616e20706572737065637469766521"
      ],
      "resp_static": "4a3acbfdb163dec651dfa3194dece676d437029c62a408b4c5ea9114246e4893",
      "resp_ephemeral": "bbdb4cdbd309f1a1f2e1456967fe288cadd6f712d65dc7b7793d5e63da6b375b",
      "resp_remote_static": "6bc3822a2aa7f4e6981d6538692b3cdf3e6df9eea6ed269eb41d93c22757b75a",
      "handshake_hash": "7f3c5fdcdd3767e2835473a2683971490339f5bbeee82c3690bc606e14db70ed",
      "messages": [
        {
          "payload": "4c756477696720766f6e204d69736573",
          "ciphertext": "ca35def5ae56cec33dc2036731ab14896bc4c75dbb07a61f879f8e3afa4c7944babf6443250c604872e33233c3b9a29df5c6d334ae2d53f1bd7f0b265a716b37"
        },
        {
          "payload": "4d757272617920526f746862617264",
          "ciphertext": "95ebc60d2b1fa672c1f46a8aa265ef51bfe38e7ccb39ec5be34069f14480884366a1f5f0d79fe93ae476bd1897a7a8ae92764898aa5d49e07b5849f35865ba"
        },
        {
          "payload": "462e20412e20486179656b",
          "ciphertext": "2eb2686b8814a7c0178fe18bfeeafe3e07312d69486d45e6572546"
        },
        {
          "payload": "4361726c204d656e676572",
          "ciphertext": "eea5791a890cd573a5c2e2345a8f98b0d1f0727acd24584fcddde5"
        },
        {
          "payload": "4a65616e2d426170746973746520536179",
          "ciphertext": "ab2e1a411abaaa3df9cb497dffe4cfb70af6c71f0815b3c33b35e22329dee72f3e"
        },
        {
          "payload": "457567656e2042f6686d20766f6e2042617765726b",
          "ciphertext": "ed22a0392c6afbfd6a6adea92b1faf13c4df24072f7060a20b1500609621c6957ac86d82f9"
        }
      ]
    }
  ]
}
"""
}
