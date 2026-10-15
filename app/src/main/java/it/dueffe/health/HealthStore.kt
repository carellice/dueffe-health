package it.dueffe.health

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

class HealthStore(private val context: Context) {
    private val root = File(context.filesDir, "health").apply { mkdirs() }
    private val settingsFile = File(root, "settings.json")
    var settings: JSONObject = readObject(settingsFile)
        private set
    var profiles: JSONArray = readArray(File(root, "profiles.json"))
        private set
    var activeId: String = settings.optString("activeProfileId", "main")
        private set

    init {
        if (profiles.length() == 0) {
            profiles.put(JSONObject().put("id", "main").put("nome", "Io").put("relazione", "Io"))
            saveProfiles()
        }
        if ((0 until profiles.length()).none { profiles.getJSONObject(it).optString("id") == activeId }) switchProfile(profiles.getJSONObject(0).getString("id"))
    }

    private fun readObject(file: File): JSONObject = runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }
    private fun readArray(file: File): JSONArray = runCatching { JSONArray(file.readText()) }.getOrElse { JSONArray() }
    private fun write(file: File, value: String) { val tmp = File(file.path + ".tmp"); tmp.writeText(value); if (!tmp.renameTo(file)) { file.writeText(value); tmp.delete() } }
    private fun file(key: String, id: String = activeId) = File(root, "${id}_${key}.json")
    fun list(key: String, id: String = activeId): List<JSONObject> = readArray(file(key, id)).let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }
    fun profile(): JSONObject = readObject(file("profile"))
    fun saveProfile(data: JSONObject) = write(file("profile"), data.toString())
    fun put(key: String, item: JSONObject) {
        val data = list(key).toMutableList()
        val id = item.optString("id").ifBlank { UUID.randomUUID().toString() }
        item.put("id", id)
        val position = data.indexOfFirst { it.optString("id") == id }
        if (position >= 0) data[position] = item else data.add(item)
        write(file(key), JSONArray(data).toString())
    }
    fun delete(key: String, id: String) = write(file(key), JSONArray(list(key).filterNot { it.optString("id") == id }).toString())
    fun profileItem(): JSONObject = (0 until profiles.length()).map { profiles.getJSONObject(it) }.first { it.optString("id") == activeId }
    fun saveProfiles() = write(File(root, "profiles.json"), profiles.toString())
    fun addProfile(name: String, relation: String) { profiles.put(JSONObject().put("id", UUID.randomUUID().toString()).put("nome", name).put("relazione", relation)); saveProfiles() }
    fun updateProfileItem(name: String, relation: String) { profileItem().put("nome", name).put("relazione", relation); saveProfiles() }
    fun deleteProfile(id: String) {
        if (id == "main") return
        profiles = JSONArray((0 until profiles.length()).map { profiles.getJSONObject(it) }.filterNot { it.optString("id") == id })
        root.listFiles()?.filter { it.name.startsWith("${id}_") }?.forEach { it.delete() }
        saveProfiles(); if (activeId == id) switchProfile("main")
    }
    fun switchProfile(id: String) { activeId = id; settings.put("activeProfileId", id); saveSettings() }
    fun setSetting(key: String, value: Any?) { settings.put(key, value); saveSettings() }
    fun saveSettings() = write(settingsFile, settings.toString())
    fun dose(date: String, medicineId: String, time: String): JSONObject = readObject(file("taken_$date")).optJSONObject(medicineId)?.optJSONObject(time) ?: JSONObject()
    fun asNeededDoses(date: String, medicineId: String): List<Pair<String, JSONObject>> {
        val doses = readObject(file("taken_$date")).optJSONObject(medicineId) ?: return emptyList()
        return doses.keys().asSequence().filter { it.startsWith("prn:") && doses.optJSONObject(it)?.bool("taken") == true }
            .map { it to doses.getJSONObject(it) }.sortedBy { it.first }.toList()
    }
    fun recordAsNeededDose(med: JSONObject) {
        val key = "prn:${System.currentTimeMillis()}:${UUID.randomUUID()}"
        toggleDose(med, key, true)
    }
    fun setDose(date: String, medicineId: String, time: String, taken: Boolean) {
        val data = readObject(file("taken_$date"))
        val medicine = data.optJSONObject(medicineId) ?: JSONObject()
        if (taken) medicine.put(time, JSONObject().put("taken", true).put("actualTime", java.time.LocalTime.now().toString().take(5))) else medicine.remove(time)
        data.put(medicineId, medicine); write(file("taken_$date"), data.toString())
    }
    fun toggleDose(med: JSONObject, time: String, taken: Boolean) {
        val before = dose(today(), med.str("id"), time).bool("taken")
        if (before == taken) return
        setDose(today(), med.str("id"), time, taken)
        med.str("quantita").toDoubleOrNull()?.let { current ->
            med.put("quantita", if (taken) (current - 1).coerceAtLeast(0.0) else current + 1)
            put("medicines", med)
        }
    }
    fun exportProfile(): String = JSONObject().apply {
        put("version", 2); put("exportedAt", java.time.Instant.now().toString()); put("profileId", activeId)
        put("profile", profile())
        for (key in listOf("medicines", "appointments", "bookingReminders", "documents")) put(key, JSONArray(list(key).map { JSONObject(it.toString()).apply { remove("fileData") } }))
        val history = JSONObject()
        root.listFiles()?.filter { it.name.startsWith("${activeId}_taken_") }?.forEach { history.put(it.name.removePrefix("${activeId}_taken_").removeSuffix(".json"), readObject(it)) }
        put("doseHistory", history)
    }.toString(2)
    fun importProfile(raw: String) {
        val data = JSONObject(raw)
        require(data.optInt("version", 0) in 1..2) { "Backup non valido" }
        data.optJSONObject("profile")?.let { saveProfile(it) }
        for (key in listOf("medicines", "appointments", "bookingReminders", "documents")) data.optJSONArray(key)?.let { write(file(key), it.toString()) }
        data.optJSONObject("doseHistory")?.let { history -> history.keys().forEach { date -> if (runCatching { LocalDate.parse(date) }.isSuccess) write(file("taken_$date"), history.getJSONObject(date).toString()) } }
    }
    fun clearProfile() { root.listFiles()?.filter { it.name.startsWith("${activeId}_") }?.forEach { it.delete() } }
    fun attachmentPath(name: String) = File(root, File(name).name)
    fun attachmentFile(id: String, extension: String) = File(root, "attachment_${id.replace(Regex("[^a-zA-Z0-9_-]"), "")}.${extension.take(8).replace(Regex("[^a-zA-Z0-9]"), "")}")
}

fun JSONObject.str(key: String) = optString(key, "")
fun JSONObject.bool(key: String, default: Boolean = false) = if (has(key)) optBoolean(key) else default
fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.let { arr -> (0 until arr.length()).map { arr.optString(it) } } ?: emptyList()
fun today() = LocalDate.now().toString()
