package com.moralesDev.dsForEveryone.di

import javax.inject.Qualifier

/**
 * Marca el dispatcher de entrada/salida. Se inyecta en vez de usar
 * `Dispatchers.IO` directamente para poder sustituirlo por uno de pruebas.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Ámbito que vive lo que vive la aplicación.
 *
 * Existe para el puñado de operaciones que deben terminar aunque quien las
 * pidió ya no esté: sobre todo volcar la partida de batería al salir de un
 * juego, momento en el que el `viewModelScope` de la pantalla ya está
 * cancelado.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
