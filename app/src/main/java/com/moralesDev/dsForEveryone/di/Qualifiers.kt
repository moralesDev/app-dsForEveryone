package com.moralesDev.dsForEveryone.di

import javax.inject.Qualifier

/**
 * Marca el dispatcher de entrada/salida. Se inyecta en vez de usar
 * `Dispatchers.IO` directamente para poder sustituirlo por uno de pruebas.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
