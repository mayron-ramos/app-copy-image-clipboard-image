package com.example.data

import kotlinx.coroutines.flow.Flow

class CopiedImageRepository(private val dao: CopiedImageDao) {
    val allImages: Flow<List<CopiedImage>> = dao.getAllImages()

    suspend fun getAllImagesDirect(): List<CopiedImage> {
        return dao.getAllImagesDirect()
    }

    suspend fun insert(image: CopiedImage): Long {
        return dao.insertImage(image)
    }

    suspend fun delete(image: CopiedImage) {
        dao.deleteImage(image)
    }

    suspend fun deleteById(id: Int) {
        dao.deleteImageById(id)
    }

    suspend fun deleteAll() {
        dao.deleteAllImages()
    }
}
