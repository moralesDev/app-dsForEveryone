Código nativo propio en C++ entre la app y el core de `third_party/melonDS`.

- `ds_core/`: melonDS detrás de una API en C (`ds_core.h`), sin nada de
  ninguna plataforma. Lo comparten Android e iOS.
- `emulator_bridge/`: lo propio de Android. `ds_bridge.cpp` traduce JNI a
  `ds_core.h` y `audio_aaudio.cpp` saca el sonido por AAudio.

La app de iOS (`ios/`) usa `ds_core/` directamente. Ver `docs/ios.md`, fase iOS-1.
