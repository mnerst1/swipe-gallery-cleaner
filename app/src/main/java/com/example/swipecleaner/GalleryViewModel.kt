package com.example.swipecleaner

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

data class Photo(val uri:String, val name:String, val size:Long = 0, val date:Long = 0, val album:String = "")
data class DecisionHistory(val uri:String, val old:String?, val choice:String, val swipe:Boolean, val day:String)
data class State(
 val screen:String="welcome", val folder:String?=null, val allPhotos:List<Photo> = emptyList(), val photos:List<Photo> = emptyList(),
 val decisions:Map<String,String> = emptyMap(), val index:Int=0, val previous:List<DecisionHistory> = emptyList(),
 val theme:String="system", val haptics:Boolean=true, val reducedMotion:Boolean=false,
 val deleted:Int=0, val deletedBytes:Long=0, val failed:Int=0, val pending:Int=0,
 val session:String="Default", val sessions:List<String> = listOf("Default"),
 val album:String="", val sort:String="newest", val from:String="", val to:String="",
 val similar:List<List<Photo>> = emptyList(), val scanProgress:Int=0, val scanTotal:Int=0,
 val stats:List<DayStat> = emptyList(), val accessibleCount:Int?=null, val partialAccess:Boolean=false
)
class GalleryViewModel(app:Application):AndroidViewModel(app) {
 private val prefs=app.getSharedPreferences("gallery",0); private val statistics=Statistics(app)
 private val _state=MutableStateFlow(State(theme=prefs.getString("theme","system")?:"system",haptics=prefs.getBoolean("haptics",true),reducedMotion=prefs.getBoolean("reducedMotion",false)))
 val state=_state.asStateFlow()
 private fun update(f:(State)->State) { _state.value=f(_state.value) }
 private fun digest(s:String)=java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){ "%02x".format(it.toInt() and 255) }
 private fun oldKey(source:String)="decisions_"+digest(source)
 private fun sessionsKey(source:String)="sessions_"+digest(source)
 private fun sessionKey(source:String,name:String)="session_"+digest(source+"\n"+name)
 private fun activeKey(source:String)="active_"+digest(source)
 init { prefs.getString("folder",null)?.let { source ->
  if(!prefs.contains(oldKey(source)) && prefs.contains("decisions")) prefs.edit().putString(oldKey(source),prefs.getString("decisions","{}" )).remove("decisions").apply()
  if(source=="all") loadAll() else load(source)
 } }
 private fun names(source:String):List<String> { val a=runCatching { JSONArray(prefs.getString(sessionsKey(source),"[]")) }.getOrDefault(JSONArray()); return (listOf("Default")+(0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }).distinct() }
 private fun openSource(source:String) {
  prefs.edit().putString("folder",source).apply()
  val name=prefs.getString(activeKey(source),"Default")?:"Default"
  update { it.copy(screen="loading",folder=source,session=name,sessions=names(source),previous=emptyList(),allPhotos=emptyList(),photos=emptyList(),similar=emptyList()) }
 }
 private fun restore(source:String, photos:List<Photo>) {
  val name=_state.value.session; val obj=runCatching { JSONObject(prefs.getString(sessionKey(source,name),"{}") ?: "{}") }.getOrDefault(JSONObject())
  val stored=obj.optJSONObject("decisions") ?: if(name=="Default") runCatching { JSONObject(prefs.getString(oldKey(source),"{}")?:"{}") }.getOrDefault(JSONObject()) else JSONObject()
  val ids=photos.map { it.uri }.toSet(); val choices=stored.keys().asSequence().mapNotNull { id -> stored.optString(id).takeIf { it=="keep" || it=="delete" }?.let { id to it } }.toMap()
  val h=obj.optJSONArray("history") ?: JSONArray(); val history=(0 until h.length()).mapNotNull { i -> val x=h.optJSONObject(i) ?: return@mapNotNull null; val id=x.optString("uri"); if(id.isBlank()) null else DecisionHistory(id,x.optString("old").ifEmpty{null},x.optString("choice"),x.optBoolean("swipe"),x.optString("day")) }
  update { it.copy(allPhotos=photos,decisions=choices,previous=history,album=obj.optString("album"),sort=obj.optString("sort","newest"),from=obj.optString("from"),to=obj.optString("to"),deleted=0,failed=0,pending=0) }
  applyFilter(obj.optString("current")); update { it.copy(screen=if(photos.isEmpty()) "empty" else "review") }; save()
 }
 private fun save() {
  val s=_state.value; val source=s.folder ?: return; val decisions=JSONObject(); s.decisions.forEach{(k,v)->decisions.put(k,v)}
  val history=JSONArray(); s.previous.forEach { h -> history.put(JSONObject().put("uri",h.uri).put("old",h.old ?: "").put("choice",h.choice).put("swipe",h.swipe).put("day",h.day)) }
  val o=JSONObject().put("decisions",decisions).put("history",history).put("current",s.photos.getOrNull(s.index)?.uri ?: "").put("album",s.album).put("sort",s.sort).put("from",s.from).put("to",s.to)
  prefs.edit().putString(sessionKey(source,s.session),o.toString()).putString(activeKey(source),s.session).apply()
 }
 fun load(folder:String) {
  openSource(folder); viewModelScope.launch {
   val result=withContext(Dispatchers.IO) { runCatching {
    val dir=DocumentFile.fromTreeUri(getApplication(),Uri.parse(folder)) ?: error("folder")
    if(!dir.canRead()) error("permission")
    dir.listFiles().filter{ it.isFile && it.type?.startsWith("image/")==true }.map { Photo(it.uri.toString(),it.name?:"",it.length().coerceAtLeast(0),it.lastModified(),"") }
   } }; if(_state.value.folder!=folder) return@launch
   result.fold(onSuccess={ restore(folder,it) },onFailure={update { s->s.copy(screen="error") }})
  }
 }
 fun loadAll() {
  openSource("all"); viewModelScope.launch {
   val result=withContext(Dispatchers.IO) { runCatching {
    val resolver=getApplication<Application>().contentResolver; val collection=MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection=arrayOf(MediaStore.Images.Media._ID,MediaStore.Images.Media.DISPLAY_NAME,MediaStore.Images.Media.SIZE,MediaStore.Images.Media.DATE_ADDED,MediaStore.Images.Media.BUCKET_DISPLAY_NAME,MediaStore.Images.Media.DATE_TAKEN)
    val list=mutableListOf<Photo>(); resolver.query(collection,projection,null,null,null)?.use { c ->
     val id=c.getColumnIndexOrThrow(projection[0]); val name=c.getColumnIndexOrThrow(projection[1]); val size=c.getColumnIndexOrThrow(projection[2]); val date=c.getColumnIndexOrThrow(projection[3]); val album=c.getColumnIndexOrThrow(projection[4]); val taken=c.getColumnIndexOrThrow(projection[5])
     while(c.moveToNext()) list.add(Photo(ContentUris.withAppendedId(collection,c.getLong(id)).toString(),c.getString(name)?:"",c.getLong(size).coerceAtLeast(0),(if(c.getLong(taken)>0) c.getLong(taken) else c.getLong(date)*1000),c.getString(album)?:""))
    } ?: error("query"); list
   } }; if(_state.value.folder!="all") return@launch
   result.fold(onSuccess={ restore("all",it) },onFailure={ update { s->s.copy(screen="error") } })
  }
 }
 fun applyFilter(current:String?=null) {
  val s=_state.value; val sorted=filterPhotos(s.allPhotos,s.album,s.sort,s.from,s.to)
  val id=current ?: s.photos.getOrNull(s.index)?.uri; val idx=sorted.indexOfFirst{it.uri==id}.let { if(it>=0) it else sorted.indexOfFirst{p->p.uri !in s.decisions}.coerceAtLeast(0) }
  update { it.copy(photos=sorted,index=idx.coerceAtMost((sorted.size-1).coerceAtLeast(0))) }
 }
 fun filters(album:String,sort:String,from:String,to:String) { update { it.copy(album=album,sort=sort,from=from,to=to) }; applyFilter(); save() }
 fun createSession(name:String) { val source=_state.value.folder ?: return; val n=name.trim().take(60); if(n.isBlank()) return; val all=(names(source)+n).distinct(); prefs.edit().putString(sessionsKey(source),JSONArray(all).toString()).putString(activeKey(source),n).apply(); update{it.copy(session=n,sessions=all)}; restore(source,_state.value.allPhotos) }
 fun selectSession(name:String) { val source=_state.value.folder ?: return; save(); update{it.copy(session=name)}; restore(source,_state.value.allPhotos) }
 fun decide(value:String,swipe:Boolean=false) {
  val s=_state.value; val p=s.photos.getOrNull(s.index) ?: return; val old=s.decisions[p.uri]; val d=s.decisions+(p.uri to value); val day=LocalDate.now().toString()
  val h=DecisionHistory(p.uri,old,value,swipe,day); val next=ReviewLogic.nextUndecided(s.photos.map{it.uri},d,s.index)
  if(value=="keep" && old!="keep") statistics.record(day,kept=1,swipe=if(swipe)1 else 0)
  update { it.copy(decisions=d,previous=it.previous+h,index=next?:s.index,screen=if(next==null) "marked" else "review") }; save()
 }
 fun undo() { val s=_state.value; val available=s.allPhotos.map{it.uri}.toSet(); val historyIndex=s.previous.indexOfLast{it.uri in available}; if(historyIndex<0)return; val h=s.previous[historyIndex]; val d=ReviewLogic.restore(s.decisions,h.uri,h.old)
  if(h.choice=="keep" && h.old!="keep") statistics.record(h.day,kept=-1,swipe=if(h.swipe)-1 else 0)
  val i=s.photos.indexOfFirst{it.uri==h.uri}; update { it.copy(decisions=d,previous=it.previous.filterIndexed { index, _ -> index != historyIndex },index=if(i<0)it.index else i,screen="review") }; save()
 }
 fun skip() { val s=_state.value; if(s.photos.isNotEmpty()) { update { it.copy(index=(it.index+1)%it.photos.size) }; save() } }
 fun toggleMark(uri:String) { update { it.copy(decisions=it.decisions+(uri to if(it.decisions[uri]=="delete") "keep" else "delete")) }; save() }
 fun removeMark(uri:String) { update { it.copy(decisions=it.decisions+(uri to "keep")) }; save() }
 fun markAll() { update { it.copy(decisions=it.decisions+it.photos.associate{p->p.uri to "delete"}) }; save() }
 fun clearMarks() { update { state -> val accessible=state.allPhotos.map{it.uri}.toSet(); state.copy(decisions=state.decisions.mapValues{(k,v)->if(k in accessible && v=="delete") "keep" else v}) }; save() }
 fun navigate(screen:String) { update{it.copy(screen=screen)}; if(screen=="stats") refreshStats() }
 fun move(delta:Int) { update{it.copy(index=(it.index+delta).coerceIn(0,(it.photos.size-1).coerceAtLeast(0)))}; save() }
 fun setTheme(v:String) {prefs.edit().putString("theme",v).apply();update{it.copy(theme=v)}}
 fun setHaptics(v:Boolean) {prefs.edit().putBoolean("haptics",v).apply();update{it.copy(haptics=v)}}
 fun setReducedMotion(v:Boolean) {prefs.edit().putBoolean("reducedMotion",v).apply();update{it.copy(reducedMotion=v)}}
 fun scanSimilar() { val source=_state.value.folder; val photos=_state.value.photos; update{it.copy(screen="similar",similar=emptyList(),scanProgress=0,scanTotal=photos.size)}
  viewModelScope.launch { val hashes=withContext(Dispatchers.IO) { val list=mutableListOf<Pair<String,Long>>(); photos.forEachIndexed { i,p -> SimilarImages.fingerprint(getApplication(),p.uri)?.let{list.add(p.uri to it)}; if(i%10==0) update{it.copy(scanProgress=i+1)} }; list }
   val groups=withContext(Dispatchers.Default){SimilarImages.groups(hashes)}; if(_state.value.folder!=source) return@launch
   val byId=photos.associateBy{it.uri}; update{it.copy(similar=groups.map{ids->ids.mapNotNull(byId::get)},scanProgress=photos.size)}
  }
 }
 fun marked():List<Photo> = _state.value.allPhotos.filter { _state.value.decisions[it.uri]=="delete" }
 fun prepareMediaBatch():List<Uri>? { val batch=marked().take(500); val a=JSONArray(); batch.forEach { a.put(JSONObject().put("uri",it.uri).put("size",it.size)) }; val saved=prefs.edit().putString("pendingMedia",a.toString()).putString("pendingSource",_state.value.folder).putString("pendingSession",_state.value.session).commit(); return if(saved) batch.map{Uri.parse(it.uri)} else null }
 fun completeMediaDeletion(approved:Boolean) {
  val batch=runCatching{JSONArray(prefs.getString("pendingMedia","[]"))}.getOrDefault(JSONArray()); if(!approved) { prefs.edit().remove("pendingMedia").apply(); navigate("marked"); return }
  val items=(0 until batch.length()).mapNotNull{ i -> batch.optJSONObject(i)?.let{ Photo(it.optString("uri"),"",it.optLong("size")) } }
  val source=prefs.getString("pendingSource",null); val session=prefs.getString("pendingSession",null)
  viewModelScope.launch {
   _state.first { it.folder != source || it.session != session || it.screen != "loading" }
   update{it.copy(screen="working")}
   val successes=withContext(Dispatchers.IO) { items.filter { p->runCatching { getApplication<Application>().contentResolver.query(Uri.parse(p.uri),arrayOf(MediaStore.Images.Media._ID),null,null,null)?.use{!it.moveToFirst()} ?: false }.getOrDefault(false) } }
   finishDeletion(successes,items.size,source,session); prefs.edit().remove("pendingMedia").remove("pendingSource").remove("pendingSession").apply()
  }
 }
 private fun finishDeletion(successes:List<Photo>, attempted:Int, source:String?,session:String?) {
  val ids=successes.map{it.uri}.toSet(); val bytes=successes.sumOf{it.size}; if(ids.isNotEmpty()) statistics.record(deleted=ids.size,bytes=bytes)
  val same=source==_state.value.folder && session==_state.value.session
  if(same) { update { it.copy(allPhotos=it.allPhotos.filterNot{p->p.uri in ids},photos=it.photos.filterNot{p->p.uri in ids},decisions=it.decisions.filterKeys{k->k !in ids},deleted=ids.size,deletedBytes=bytes,failed=attempted-ids.size,pending=(it.allPhotos.count{p->it.decisions[p.uri]=="delete"}-attempted).coerceAtLeast(0),previous=emptyList(),index=0,screen="complete") }; save() }
  else { update { it.copy(deleted=ids.size,deletedBytes=bytes,failed=attempted-ids.size,screen="complete") } }
 }
 fun delete() { val s=_state.value; val targets=marked(); update{it.copy(screen="working")}; viewModelScope.launch {
  val successes=withContext(Dispatchers.IO) { targets.filter{runCatching{DocumentFile.fromSingleUri(getApplication(),Uri.parse(it.uri))?.delete()==true}.getOrDefault(false)} }
  finishDeletion(successes,targets.size,s.folder,s.session)
 } }
 fun reset() { update {it.copy(decisions=emptyMap(),previous=emptyList(),index=0,deleted=0,failed=0,pending=0,screen=if(it.photos.isEmpty())"empty" else "review")}; save() }
 fun refreshStats() { viewModelScope.launch { val result=withContext(Dispatchers.IO) { runCatching {
  getApplication<Application>().contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,arrayOf(MediaStore.Images.Media._ID),null,null,null)?.use{it.count}
 }.getOrNull() }; val context=getApplication<Application>(); val partial=android.os.Build.VERSION.SDK_INT>=34 && androidx.core.content.ContextCompat.checkSelfPermission(context,"android.permission.READ_MEDIA_VISUAL_USER_SELECTED")==android.content.pm.PackageManager.PERMISSION_GRANTED && androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.READ_MEDIA_IMAGES)!=android.content.pm.PackageManager.PERMISSION_GRANTED
 if(result!=null) statistics.snapshot(result,partial); update{it.copy(stats=statistics.history(),accessibleCount=result,partialAccess=partial)} } }
}







