package ru.course.cityservices.storage

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.course.cityservices.model.RequestResponse

@Dao
interface RequestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(request: RequestResponse)

    // История читается без фильтра по пользователю - на экран попадают заявки всех пользователей устройства
    @Query(value = "SELECT * FROM requests ORDER BY createdAt DESC")
    suspend fun all(): List<RequestResponse>

    // Фильтр существует, но в приложении не используется
    @Query(value = "SELECT * FROM requests WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun forUser(userId: Long): List<RequestResponse>

    @Query(value = "DELETE FROM requests")
    suspend fun clear()
}
