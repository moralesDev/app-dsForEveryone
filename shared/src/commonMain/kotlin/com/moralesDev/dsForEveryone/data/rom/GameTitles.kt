package com.moralesDev.dsForEveryone.data.rom

/**
 * El nombre que tiene en la biblioteca cada juego, por su id (el SHA-256 de
 * la ROM). Es lo único que Ajustes necesita de la biblioteca —para poner
 * títulos en el manifiesto de una copia de partidas—, y así no depende del
 * repositorio de ROMs entero, que en Android lee los archivos por SAF.
 */
fun interface GameTitles {
    suspend fun byId(): Map<String, String>
}
