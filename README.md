# zephyr-java

Java bindings for the [Zephyr Protocol](https://github.com/ZephyrProtocol) wallet core, for Android
(AAR) and for desktop and server JVMs (JAR). The native core is `zephyr-cpp` over Zephyr's `wallet2`,
built from the security-hardened forks of [zephyr-backports](https://github.com/zephyr-backports),
linked statically into one native library per platform. The Java API mirrors
[monero-java](https://github.com/woodser/monero-java), from which this project is derived, with
Zephyr's assets, conversions and reserve information added.

**Not affiliated with ZephyrProtocol.** An independent project. Report Zephyr vulnerabilities to the
ZephyrProtocol team, not here.

## Status

Under construction; no release yet. The first release follows the backports tag `v2.3.0-sec.1`.

## Credits and licence

Derived from monero-java by woodser (MIT), whose copyright notices are kept in the source files.
zephyr-java is MIT licensed as well; see `LICENCE.txt`.
