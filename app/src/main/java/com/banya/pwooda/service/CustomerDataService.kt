package com.banya.pwooda.service

import android.content.Context
import com.banya.pwooda.data.*
import com.google.gson.Gson
import java.io.InputStreamReader

class DataRepository(private val context: Context) {
    private val gson = Gson()
    private var cachedData: PwoodaData? = null

    fun loadData(): PwoodaData {
        if (cachedData != null) return cachedData!!
        val inputStream = context.assets.open("data.json")
        val reader = InputStreamReader(inputStream, Charsets.UTF_8)
        val data = gson.fromJson(reader, PwoodaData::class.java)
        cachedData = data
        return data
    }

    fun getUsers(): List<User> = loadData().users
    fun getWeeklySchedules(): List<WeeklySchedule> = loadData().weeklySchedules
    fun getMedications(): List<Medication> = loadData().medications
    fun getEducatorMaterials(): EducatorMaterials = loadData().educatorMaterials
    fun getUserById(id: String): User? = getUsers().find { it.id == id }
    fun getScheduleByUserId(id: String): WeeklySchedule? = getWeeklySchedules().find { it.id == id }
}

class CustomerDataService(private val context: Context) {
    private val repository = DataRepository(context)

    fun loadUsers(): List<User> = repository.getUsers()
    fun getUserById(id: String): User? = repository.getUserById(id)
    fun getScheduleByUserId(id: String): WeeklySchedule? = repository.getScheduleByUserId(id)
    fun getEducatorMaterials(): EducatorMaterials = repository.getEducatorMaterials()
}