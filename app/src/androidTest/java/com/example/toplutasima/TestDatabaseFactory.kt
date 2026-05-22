package com.example.toplutasima

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.toplutasima.data.local.AppDatabase

object TestDatabaseFactory {
    fun targetContext(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    fun createInMemoryDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(targetContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
}
