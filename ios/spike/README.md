# DsSpike: ¿va bastante rápido en iOS?

La prueba de la fase iOS-0 de [`docs/ios.md`](../../docs/ios.md). Una app
mínima en Objective-C++ que corre melonDS **sin JIT** y mide cuántos
fotogramas por segundo da el intérprete en un iPhone o iPad. Es de usar y
tirar: la app de verdad llega en las fases siguientes.

## Una sola vez, en Xcode

1. **Xcode → Settings → Components**: instala la plataforma **iOS**. Sin ella
   Xcode no puede compilar para un dispositivo, aunque el SDK ya esté.
2. **Xcode → Settings → Accounts → +**: añade tu cuenta de Apple. Con la
   gratuita basta; aparece como «Personal Team».

## Generar el proyecto y abrirlo

Desde la raíz del repositorio:

```sh
cmake -S ios/spike -B build-ios/spike -G Xcode \
  -DCMAKE_SYSTEM_NAME=iOS -DCMAKE_OSX_ARCHITECTURES=arm64 \
  -DCMAKE_OSX_DEPLOYMENT_TARGET=16.0
open build-ios/spike/DsSpike.xcodeproj
```

En Xcode:

1. Arriba, elige el esquema **DsSpike** (no `ALL_BUILD`) y tu iPad como destino.
2. Proyecto → target **DsSpike** → **Signing & Capabilities** → **Team**: tu
   cuenta. Si se queja del identificador, cambia el *Bundle Identifier* por
   otro único (por ejemplo, añade tu nombre al final).
3. **⌘R** (Product → Run).

La primera vez el iPad no deja abrirla: **Ajustes → General → VPN y gestión
de dispositivos** → tu cuenta → **Confiar**. Con la cuenta gratuita la app
caduca a los 7 días; se vuelve a instalar con ⌘R.

## Medir

1. Pasa una ROM al iPad, a la app **Archivos** (AirDrop, iCloud Drive o el
   Finder con el cable).
2. En DsSpike: **Elegir ROM**.
3. Con **Sin límite** (por defecto), la línea de arriba dice:
   - **fps**: cuántos fotogramas por segundo da el core sin frenos.
   - **ms por fotograma**: el presupuesto de una DS es **16,7 ms**.
   - **% de una DS**: 100 % es velocidad real; 150 % alcanza para x1,5 y
     200 % para x2.
4. Mide en un juego 2D y en uno 3D, en una escena con movimiento (no en un
   menú quieto), y apunta las cifras en `docs/ios.md`.
5. **60 Hz** pone el core al ritmo real de la DS, para ver si se juega fluido.

Las mismas cifras salen cada segundo en la consola de Xcode, con el prefijo `[spike]`.
