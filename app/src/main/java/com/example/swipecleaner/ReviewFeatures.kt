package com.example.swipecleaner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

data class DayStat(val day: String, val kept: Int, val swipeKept: Int, val deleted: Int, val bytes: Long, val photos:Int? = null, val partial:Boolean = false)
class Statistics(private val context: Context) {
 private val prefs = context.getSharedPreferences("statistics", 0)
 @Synchronized fun record(day: String = LocalDate.now().toString(), kept: Int = 0, swipe: Int = 0, deleted: Int = 0, bytes: Long = 0) {
  val all = runCatching { JSONObject(prefs.getString("days", "{}") ?: "{}") }.getOrDefault(JSONObject())
  val item = all.optJSONObject(day) ?: JSONObject()
  item.put("kept", (item.optInt("kept") + kept).coerceAtLeast(0))
  item.put("swipe", (item.optInt("swipe") + swipe).coerceAtLeast(0))
  item.put("deleted", (item.optInt("deleted") + deleted).coerceAtLeast(0))
  item.put("bytes", (item.optLong("bytes") + bytes).coerceAtLeast(0))
  all.put(day, item); prefs.edit().putString("days", all.toString()).apply()
 }
 @Synchronized fun snapshot(count:Int,partial:Boolean) {
  val day=LocalDate.now().toString(); val all=runCatching { JSONObject(prefs.getString("days","{}") ?: "{}") }.getOrDefault(JSONObject()); val item=all.optJSONObject(day) ?: JSONObject()
  item.put("photos",count); item.put("partial",partial); all.put(day,item); prefs.edit().putString("days",all.toString()).apply()
 }
 fun history(): List<DayStat> {
  val all = runCatching { JSONObject(prefs.getString("days", "{}") ?: "{}") }.getOrDefault(JSONObject())
  return all.keys().asSequence().sortedDescending().map { day -> val item=all.optJSONObject(day) ?: JSONObject(); DayStat(day,item.optInt("kept"),item.optInt("swipe"),item.optInt("deleted"),item.optLong("bytes"),if(item.has("photos")) item.optInt("photos") else null,item.optBoolean("partial")) }.toList()
 }
}
object SimilarImages {
 fun fingerprint(context: Context, uri: String): Long? = runCatching {
  val resolver = context.contentResolver
  val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
  val stream = resolver.openInputStream(Uri.parse(uri)) ?: return null
  stream.use { BitmapFactory.decodeStream(it,null,bounds) }
  if(bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
  var sample=1; while(bounds.outWidth / (sample*2) >= 32 && bounds.outHeight / (sample*2) >= 32) sample*=2
  val bitmap=resolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=sample }) } ?: return null
  val tiny=Bitmap.createScaledBitmap(bitmap,9,8,true); var hash=0L
  for(y in 0..7) for(x in 0..7) {
   fun luma(c:Int): Int = (android.graphics.Color.red(c)*299 + android.graphics.Color.green(c)*587 + android.graphics.Color.blue(c)*114)/1000
   hash=(hash shl 1) or if(luma(tiny.getPixel(x,y)) > luma(tiny.getPixel(x+1,y))) 1L else 0L
  }
  if(tiny !== bitmap) tiny.recycle(); bitmap.recycle(); hash
 }.getOrNull()
 fun groups(hashes: List<Pair<String,Long>>): List<List<String>> {
  val parent=IntArray(hashes.size){it}; fun root(i:Int): Int { var x=i; while(parent[x]!=x){ parent[x]=parent[parent[x]]; x=parent[x] }; return x }
  val buckets=HashMap<Int, MutableList<Int>>(); val identical=HashMap<Long,Int>()
  hashes.forEachIndexed { i, pair ->
   val first=identical.putIfAbsent(pair.second,i); if(first!=null) { parent[root(i)]=root(first); return@forEachIndexed }
   val candidates=HashSet<Int>()
   for(band in 0..7) { val key=(band shl 8) or ((pair.second ushr (band*8)) and 255).toInt(); buckets[key]?.let { candidates.addAll(it) } }
   for(j in candidates) if(java.lang.Long.bitCount(pair.second xor hashes[j].second)<=6) { val a=root(i); val b=root(j); if(a!=b) parent[a]=b }
   for(band in 0..7) { val key=(band shl 8) or ((pair.second ushr (band*8)) and 255).toInt(); buckets.getOrPut(key){ mutableListOf() }.add(i) }
  }
  return hashes.indices.groupBy { root(it) }.values.filter { it.size>1 }.map { ids -> ids.map { hashes[it].first } }
 }
}
fun formatBytes(context: Context, size: Long): String = android.text.format.Formatter.formatFileSize(context, size.coerceAtLeast(0))
fun formatPhotoDate(timestamp: Long): String = if(timestamp <= 0) "—" else java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(timestamp))
fun filterPhotos(photos:List<Photo>, album:String, sort:String, from:String, to:String): List<Photo> {
 val start=runCatching { LocalDate.parse(from).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
 val end=runCatching { LocalDate.parse(to).plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
 val matched=photos.filter { (album.isEmpty() || it.album==album) && (start==null || it.date>=start) && (end==null || (it.date>0 && it.date<end)) }
 return when(sort) { "oldest" -> matched.sortedBy{it.date}; "largest" -> matched.sortedByDescending{it.size}; else -> matched.sortedByDescending{it.date} }
}



