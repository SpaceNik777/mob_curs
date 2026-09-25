package ru.course.cityservices.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ru.course.cityservices.model.RequestResponse

@Database(
    entities = [RequestResponse::class],
    version = 1,
)
@TypeConverters(InstantConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun requestDao(): RequestDao
}
