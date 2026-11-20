package com.qcenaman.francesinha.data

import android.content.Context
import com.qcenaman.francesinha.model.Plan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object PlanStore {
    private var file: File? = null
    private val _plans = MutableStateFlow<List<Plan>>(emptyList())
    val plans: StateFlow<List<Plan>> = _plans.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "plans.json")
        file = f
        if (f.exists()) runCatching { _plans.value = Codec.decodePlans(f.readText()).sortedByDescending { it.start } }
    }

    fun get(id: Long): Plan? = _plans.value.firstOrNull { it.id == id }

    @Synchronized
    private fun update(block: (List<Plan>) -> List<Plan>) {
        val next = block(_plans.value).sortedByDescending { it.start }
        _plans.value = next
        val f = file ?: return
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(Codec.encodePlans(next))
            if (!tmp.renameTo(f)) {
                f.writeText(Codec.encodePlans(next))
                tmp.delete()
            }
        }
    }

    fun replaceAll(list: List<Plan>) = update { list }
    fun upsert(p: Plan) = update { list -> list.filterNot { it.id == p.id } + p }
    fun delete(id: Long) = update { list -> list.filterNot { it.id == id } }
}
