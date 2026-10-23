# R8 rules that ship with the sendspin AAR to every consumer.

# The BouncyCastle provider (X25519 below API 33) references JDK classes that Android lacks.
-dontwarn org.bouncycastle.**
