# asm

Contains a class loader that allows class binaries to be edited during class loading. See javadoc in `TransformerClassLoader.java` for API details.

This only exposes the raw class file data. To more easily change bytecode during runtime, a library like [ObjectWeb ASM](https://asm.ow2.io/) may be used additionally.

This library requires [omz-java-lib](https://git.omegazero.org/omz-infrastructure/omz-java-lib).
