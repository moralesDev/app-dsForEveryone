# El enlace con el código nativo es por nombre: si R8 renombra la clase o los
# métodos, el runtime no encuentra los símbolos de libdsemu.so y la aplicación
# falla al cargar el core.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.moralesDev.dsForEveryone.emulator.core.NativeBridge { *; }
