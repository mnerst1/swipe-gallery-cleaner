package com.example.swipecleaner
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
class FilterTest {
 private fun date(d:String)=LocalDate.parse(d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
 private val photos=listOf(Photo("a","a",40,date("2026-10-01"),"Screenshots"),Photo("b","b",300,date("2026-10-02"),"Camera"),Photo("c","c",100,date("2026-10-03"),"Screenshots"))
 @Test fun filtersAlbumAndOrdersByLargest(){assertEquals(listOf("c","a"),filterPhotos(photos,"Screenshots","largest","","").map{it.uri})}
 @Test fun includesBothDateEndpoints(){assertEquals(listOf("a","b"),filterPhotos(photos,"","oldest","2026-10-01","2026-10-02").map{it.uri})}
 @Test fun filtersCanReturnNoPhotos(){assertTrue(filterPhotos(photos,"other","newest","","").isEmpty())}
}
