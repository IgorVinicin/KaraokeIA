import android.content.*
import com.araujo.karaoke_ia.data.QueueRepository
class Prefs: SharedPreferences {
 val data = mutableMapOf<String,String>()
 val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
 override fun getString(key:String, defaultValue:String?) = data[key] ?: defaultValue
 override fun registerOnSharedPreferenceChangeListener(listener:SharedPreferences.OnSharedPreferenceChangeListener) { listeners.add(listener) }
 override fun unregisterOnSharedPreferenceChangeListener(listener:SharedPreferences.OnSharedPreferenceChangeListener) { listeners.remove(listener) }
 override fun edit() = object:SharedPreferences.Editor {
  val changes = mutableMapOf<String,String>()
  override fun putString(key:String,value:String):SharedPreferences.Editor { changes[key]=value; return this }
  override fun apply() { data.putAll(changes); changes.keys.forEach { key -> listeners.toList().forEach { it.onChanged(this@Prefs,key) } } }
 }
}
class Device(val prefs:Prefs):Context() { override fun getSharedPreferences(name:String,mode:Int):SharedPreferences = prefs }
fun main() {
 val device=Device(Prefs()); val queue=QueueRepository(device)
 var notifications=0; val observation=queue.observeChanges { notifications++ }
 queue.addToQueue("A"); queue.addToQueue("B"); queue.addToQueue("B")
 check(queue.popNextIfMatches("B")==null); check(queue.getQueue()==listOf("A","B","B"))
 queue.removeFromQueue("A")
 check(queue.popNextIfMatches("A")==null); check(queue.getQueue()==listOf("B","B"))
 check(queue.popNextIfMatches("B")=="B"); check(queue.getQueue()==listOf("B"))
 check(QueueRepository(device).getQueue()==listOf("B"))
 check(queue.popNextIfMatches("B")=="B"); check(queue.popNextIfMatches("B")==null)
 check(notifications==6); observation.close(); queue.addToQueue("C"); check(notifications==6)
 println("PASS: stale head, changed queue, duplicate preservation, repository recreation, empty queue, observation/disposal")
}
