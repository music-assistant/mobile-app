package io.music_assistant.client.utils

import io.music_assistant.client.utils.KeychainClientIdentity.ImportResult
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.create
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Runs the real Security framework on the simulator. The bare test runner has no
 * Keychain (errSecNotAvailable), so this covers decoding; the Keychain write is
 * checked in the app. Fixtures: one self-signed "MA Test Client" identity, password
 * "secret", exported by LibreSSL (3DES) and by OpenSSL 3 (AES-256, PBKDF2), the
 * default a user gets today.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class KeychainClientIdentityTest {
    private val keychain = KeychainClientIdentity()

    @Test
    fun decodesLegacyPkcs12() {
        assertEquals(ImportResult.Imported("MA Test Client"), keychain.inspect(LEGACY, "secret", "file.p12"))
    }

    @Test
    fun decodesOpenSsl3Pkcs12() {
        assertEquals(ImportResult.Imported("MA Test Client"), keychain.inspect(AES, "secret", "file.p12"))
    }

    @Test
    fun reportsWrongPassword() {
        assertEquals(ImportResult.WrongPassword, keychain.inspect(AES, "nope", "file.p12"))
    }

    @Test
    fun rejectsGarbage() {
        assertEquals(ImportResult.Failed, keychain.inspect(data("AAAA"), "secret", "file.p12"))
    }

    private companion object {
        fun data(base64: String): NSData = assertNotNull(NSData.create(base64EncodedString = base64, options = 0u))

        val LEGACY = data(
            "MIII8QIBAzCCCLcGCSqGSIb3DQEHAaCCCKgEggikMIIIoDCCA1cGCSqGSIb3DQEHBqCCA0gwggNEAgEAMIIDPQYJKoZIhvcNAQcB" +
            "MBwGCiqGSIb3DQEMAQYwDgQIY1MFKwB3bWICAggAgIIDEGahlO1s77aKTsWpYzyR/hQzke8prlk1+Gt7aDjLKhVh4y1TsZyo8t2I" +
            "mMm4iYkS03QNaug0jSricqIuCoz5dFvYoAB55HDcSoJMLskoGlQ4s6V7w3BdN9jO2bkmWx0NwxLarO6O9C9ShlQiik31iopfD7Vl" +
            "bSew5hRqXQc1lo4Pn1j8iYWgKLaXHDY5qg58MLjl1YBCHECulQ4CMwepPj9oOihi2vyMVitaQzg4Ffo1H+rAgfDuE1WtAHfEFh5B" +
            "u1S5VA8bycpSNB5JuJTL6Vee/IxPY7fUw0gRP4QPrwxPwsZP7aBG0VXODLPF1vzNnEvQ74ZlNGesTsMuN3qM+z9+cmxpfO3olptc" +
            "060AMICYwAyuc316s3kcGhK8NFglfdpOhYLaFHmeey9NNLZ6doVbhEzS6hv5FZ47dlVoicQnr6bnPzPv/QDard3qlAfBhYwK5F0r" +
            "SB13nubGQ65+st759vmGdA0d0hdCX2v8MeKNVNR/xrPq1fS0hrVShDVYV0YTgiakFSeZwDAzVwhvX39TXRYmHTcHgDRNHP4aUb02" +
            "C2F2utI8LxZK9/OZyRLY7HdV4z8FoxbHcvhyajpyhUDOEkAAnofUDH+1pl2bTmZhze8XxakhayZnmLOGs16D8/yKi1LOroqYcwll" +
            "BSoD6pZD2GsG2AvpO4NmRnhA0/CM7y7mGA8O2FGdNkROqUVIuxZq2o5iR6qKzwyJaMuCQqxteONrWU+92gVWKG6DSLkjDXzUwpVY" +
            "kkIlAyVTE1rtp74clzem/2ofcugGqqZP0HnYrZ3FnaAa4UJhtOgOzIHiDYIhSp2LibM/6dlu5GopiwKzJS0WKZ3AfqwAeCA2uak7" +
            "cB6rpvG8dZ4QKYDJM5WHUETTbgvv8TebN8twGFpkb10NZFxx0db7Jh1sYGYzTtOR70nn+ZNiKvwMup5kKknBZLj8O7THFUqw3zQC" +
            "/qpmzid8tqNBU/cMjfMT58fCL1H/RFy8NVrYmmr9PN9eRo/qgb7SLTjrcfEDoq2/RhMwqh446g0L3FqIHCMjIc/Csu8wggVBBgkq" +
            "hkiG9w0BBwGgggUyBIIFLjCCBSowggUmBgsqhkiG9w0BDAoBAqCCBO4wggTqMBwGCiqGSIb3DQEMAQMwDgQId8B5XQ8SdzQCAggA" +
            "BIIEyLZlD1wWDgwYJkCTW+rPm3juopG1QeVN3z+ojMenOvSKCjlGu6QZyrwPGN0Xr7+v0lHPs1CiwizsKOeyMgXailTN+HkNxrbV" +
            "Pc9YbH30iFvdnbbz40CfFfIFXVMVYO/t3DPjK2wPFj1xCXnIdPdPMpo3jSriNN8MG5YJu4i34M+kZ4bOz7b4gv4j6vOHvQQ/WFBD" +
            "2NgqYKMzEcQTDbd9L9V7JPCeeOq10re1EOpcXaHYALuDlziTTIEEQEcKVYyjSp/bYvfjgmCRkQ/YEGv2eW+WhDnfOpMLZ6vdCNyM" +
            "HcRbK25n4foHMmb0ZNmX8qun29VQxogl/CkCvaA/TlF0HC7Wiib8PHTM2QgIJbYAF2amHuW7FyYI2HS122WK4Tr8qcVg8RBAxeQb" +
            "N6hJmqWIi87/YpO4nTDWHmWqb87GEleaBoVh1oHSSkHbj3nhmUSrS6kFXp4/zarsQKpnPO0PccV6TmT+0vqrHqIvKBlNiPq89Z0V" +
            "5NdQ54iSPKGXRl6DJdv29TJeTd/kuNAr0NU2j79mlBuTtJ/jM1dAxTJxGrdshPa6j6hMHpktllFGKJuoVFZB5t25NfLsp9m75Jvn" +
            "exKyc8Q3Uo28/ejKfAUSvR7pFAstfYUnITyZDEDdSzr4ZzFfi+UKcVck6dkmnn1mi06HKURJ+9LFmH5NxbyYwX8+BO9hcwLLjH01" +
            "gJD6ydRoNJqJVHS8RAzuvKBp9JR8xqx2MAlsDmC9TxshoUq6uuYnFFb/YlB/OaLDwcZh0F7kgRb0DUHlwEZHI5mwHbk/pKJU0TUR" +
            "qXndVAUYzBQoUgzfi8hxCmaUMSfhKVxY9TjEIDsyf4PtFao4sk10XMVorxcCbMC0+b9GUPv0dVlR2G0eWUp2S7WqBE70LTC7fwkj" +
            "t1QZNeLL3b41q0ZYKlzmUE8rfElBAsSQEibLG4gERSxNVfGLKax9HUH4Yevczavjy1wAz7LZWKK862AxMPG0lQFj+d4hf5e06wi1" +
            "MGuNBYJnSbVH16JWErwtLCkwgWeV7MiAv/6RHgKics9kA6AW4DlV7oIMQtb0LJrn/ZaLX3U152ch1dwS9GZ7NGBJXnG5zkGy729/" +
            "Kqp2mJnO2ArkV9zHi3nO1rJLdh18MnUgArjrPrYHZXR1iZAQwC6i8KDupFo60fiBM5jB6sBCNT2FlMjSo/J3zFHHJbgNvJNgZri7" +
            "Qhof8fgjO2wor71fpb8qHvZVOSUmArPHdRCdDLy9lOwNMAXT4RjAsp9UkNz02C3bJt83IYnukQmqU+rcSnz29cddRE+vXrC01kMr" +
            "BJkKCUHd0AV/D6t0yHEZmQtwOvY7IcwzguuteyxBlBln/s07bE5N1GW3/3oijp6Ru5isk5LmzgI1myHrAhqQ+bnZg3ldN0qHip/8" +
            "1BuQkgAmeP5eHBCqD6gtffNUmylr2Fap7+eU/SxeErJgjMmPuXfLE7AXNTT5GbiizlW+CLlb+oKD2bYWmiZuGtJSCo9cdr+0wAqo" +
            "PVv98eqvLGkIgEWAUz5IKY9fUnLqLFl9g88nM8bXt5VifipSvv/WW297yifmb5mWf477wWVt41PmNZRrkf0FDWtCaTj8dSJcd9U6" +
            "nV+oPTvBmVitVNPEiP6SH72KVF0y5u/VbHNdCjElMCMGCSqGSIb3DQEJFTEWBBTYRqD2RWyL90nylRS6VLYCg0QQrDAxMCEwCQYF" +
            "Kw4DAhoFAAQUNkYeGA3X8LzTY7E/Qx20+9awgXIECMFe/Qp6UIYdAgIIAA==",
        )

        val AES = data(
            "MIIJlwIBAzCCCUUGCSqGSIb3DQEHAaCCCTYEggkyMIIJLjCCA5oGCSqGSIb3DQEHBqCCA4swggOHAgEAMIIDgAYJKoZIhvcNAQcB" +
            "MF8GCSqGSIb3DQEFDTBSMDEGCSqGSIb3DQEFDDAkBBBC07mfNdsSNNqWenBw4cSyAgIIADAMBggqhkiG9w0CCQUAMB0GCWCGSAFl" +
            "AwQBKgQQSwhSlUN3Edgu1KQK0ymtsYCCAxBPk4pPupCIhSuvn0n5u3glAYiw+AlkpIXP1Rsw0Tl6vOOZv8i5MEgCF7jsEZMc11j3" +
            "e7CsqT86F4SqGAR6vtB7ru3T1iEBUgeTVa084JEGm+wcaR1PTkKJPrR9lCfeUyAuaRgkLc4ffzv2BfRDnR+55S6MHGk49M2tk7VX" +
            "jbEV9rGN1WKMKO8z/+v3LtJmSDCKRZ2pOwvO8wt5xbPHGhp0sraOgia6pJN+XJqQ6fFAhAdSc3vQg4jWd2YhbkzwXlyBMRV4Vbyq" +
            "vnG7ca1jabzsv3bjjuuUi4yshHeeg69CeWHPZ+4SSWaHBJEslHKutdQifGoh1agqP0pZDf/O7Bu9SrWeGTxOforwdaHnSKo5H5d7" +
            "QuAbYU5cnREb1Xh35JtCAJx2IS6CXcDF1g/MOffrBi94M1xc4Hzu8eRLGhUNEE8Js2QQF8RQH76HqCAspqjgC0Fd1TeDW08ACuET" +
            "0xZ6YiPKIWgRwLzbAz85xTMZfESKirIuKn1YUBjXcRQMVWvWsdP6SnHBWqQTlhThz7OeWVsYQATiqpG1uJJK+ue8SH0lWJKYUFQW" +
            "PwyN158k+gPTIdt367idXptVCz12T2wAo4QXAMEdjZ/bmTEZn/4676dZ9lPtQ9Q047oHNOAGZFHgMcZgYwgqWm6eBMbdq+R2Fq0/" +
            "XsgJ07RnWG4av0uNo5sVjz6pnw9OsIkQanYcC1guAh8nJWtFaNTChqdNhOvALz7Kl8FfZSeUyZciHM/HoTlm/vz2uU8+KfLP8WY3" +
            "Z3vLMEeAR/2IfMD9oxrrvgLSQzi3kH1uxxGtqcGToLu4cS8SAY0IodGSIMf1RgJ+AVACyrijP4485GvlS/SsKomgcugJQYHub1Sl" +
            "VWWTURtA9TFLj9yt5c5CIoo2dZDML6DoClWb3AsA7uJum4+074cdoQQCPHkm5K1TZn3j5KEuEvLLUCfm36x5YJf+3rfHWuK6FrvQ" +
            "2G00U4lZLS3d9vvMB1sMUVK4f1xMkyf+r1xfPhSmq7/ZI/qSoAfE2H02hswXSIbsaI8aFcmLw6iNrwzlMIIFjAYJKoZIhvcNAQcB" +
            "oIIFfQSCBXkwggV1MIIFcQYLKoZIhvcNAQwKAQKgggU5MIIFNTBfBgkqhkiG9w0BBQ0wUjAxBgkqhkiG9w0BBQwwJAQQ4bLXc4fL" +
            "xyZcPcKGOtvRBgICCAAwDAYIKoZIhvcNAgkFADAdBglghkgBZQMEASoEEP6n/1omDH01zf2uJIE+XNkEggTQHt5zg0u6bXKVs9Iq" +
            "Vr1lKnjdwyFfB3uRF36YFQ6aSx3Lzgft3zq5+eLaTz3kMVTBA1pEKBT3/EPIJ/MhzjmPxnfT76IxDIJwbriDgY/prbCQ8Y3A60We" +
            "gcr7Pfak71i8/0wT757vvxVkMVv42pQ0K0XarieRyWF211s4+nUulgX2lpcZ2cttk/MIywde/SVT+lc1smfdOn+Rov79M8+YQEAz" +
            "oespZ6Wy44YiId03LUGBIGZd5mhxIIOcDzUQie/6sUOOJsaf3ZkVbKZ9yYhKvZeoHNgDgKCFQJwIoGrzbdnNENrKGudZ1m4sgmyu" +
            "q9t9ehGSV6LK0ddIl+q/l9Z0dKZmTZU1eQLvoT7U/suaXbuX+s1jx6LWb6oNA2D0r8Q1GNgHOkHFaS6BZ4FJPVQUnRz9JR/Ge4oY" +
            "QBhyCE6apu2yUXrt0yBcMyh7ILlzDEbYmohvZ9Qs+nmhCzQllyiXbAjE6XepLNcEYDMAdsYWRuunu9RzxdVnK+uAZgTW7HTf4h52" +
            "G+j2yZkuy80/TpnydE58ekG/nld3vDY+eN70pkguh/n3aqZLEux5qItib+b3Nc3eeyIx2EMZhUVydw58XybX8O/OYTXrt/nE5yhx" +
            "OvY6O0bKOQ6zJeyGgn+RQvO8yO1ZAUFyVUrFM/asTtrEVcAu29tp7iGdmHc/H5aNkoadQQh9J4XhAZicKyiWKuiIc13rN7xRl3HJ" +
            "CZSvQXd0ZPS4K6CjQsSBjm5JQBhrSLFMm0QTzmLdIr2SGx/HI0RqwmgrFTqXU2z4WZxRHvfYMW+uFy4HvirvIYkwQA+hoxMB6612" +
            "ItG8NHZ1Q9wnf3MvTQOsfbrafuwqiiPLpQINxJf1gfJeU9TifwGBmYztBjI5n5Kmg748EwhtkrnacrwIXS6w9MjHg8dYiCcHkr5O" +
            "+8qym3YQIkc7msNOnwO8/d+4lUex3nBEOkL8gXrc2DzODnkzQ6XcdizocZpzaoJPuRz9cmxnrWIACaITNtH0tKlXR+VyYooL2mUs" +
            "Zr6KIxMaS4D5VZoT1L3KBF/O36iSe+BIKBFC2wUsm96URpLj0sLSNXl/XWAHMCJPlc8KoEyktQHVkh2d2vru+N8AK89y26bkqbh1" +
            "mJT9IJ83uq5F80KBX6YKwIGgZcHQi6uSE46HsoCIbo3x6pNsgGLLXPB8oIdgjAIH4yhV/ABDRtqgELpdrGx0b0+kmOXJ5JgKwT1/" +
            "FmI+iCsjFNw9l3kFfI6O+xnWQYuQtixSrSVrgnIwc6/HKufnn8MIOm/XnaRZny3nCDcpw4SBZxP2QDNOnX0UkIADJFUUtzDKMdcB" +
            "yKnjhfdiLyA6sVYGE/FhOJ9jZ5YkWtv0GaJLkzAnPWTbdX6cHPIyeHeKvimY9vgAtY2BQuEy3uiVD3pQ5AoUfJwW9CIe39/G92FH" +
            "jiJ8KXK4OZCX7SB5CDZvnLhFUe4Ab7AIPsJxUAjduoF4N5bOAixsUMhXSTexw2oklfCAXF0oRD9jqU8FZIWivbLvpNfF4MmdG/G/" +
            "n9al5Y7ZC95xt1h03boPyczq73XZaAiKMSUymymcrLhL/1JgRmKMvDI41YdC8NVxwhb/C+4Wr3/3h3Ei7oM1hcZ8Iy04Cg5wvxhG" +
            "DORKX5biuliN97rGfus2wMzKgIIxJTAjBgkqhkiG9w0BCRUxFgQU2Eag9kVsi/dJ8pUUulS2AoNEEKwwSTAxMA0GCWCGSAFlAwQC" +
            "AQUABCDXsOK3A1sr/lwBHHidSunKSJTvTRcz++BBkgGGE7nXPgQQJk6cfr9LPNdbIHvsYsyzYwICCAA=",
        )
    }
}
