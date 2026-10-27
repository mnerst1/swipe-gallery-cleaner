package com.example.swipecleaner
import org.junit.Assert.*
import org.junit.Test
class SimilarityTest {
 @Test fun matchesCloseHashesAndExcludesDistantOnes() {
  val groups=SimilarImages.groups(listOf("a" to 0L,"b" to 3L,"c" to -1L))
  assertEquals(1,groups.size); assertEquals(setOf("a","b"),groups.single().toSet())
 }
 @Test fun retainsTransitiveGroupsAndHandlesEmptyInput() {
  val groups=SimilarImages.groups(listOf("a" to 0L,"b" to 63L,"c" to 4095L))
  assertEquals(setOf("a","b","c"),groups.single().toSet()); assertTrue(SimilarImages.groups(emptyList()).isEmpty())
 }
 @Test fun groupsManyIdenticalImages() { val items=(0 until 5000).map{it.toString() to 0L}; assertEquals(5000, SimilarImages.groups(items).single().size) }
 @Test fun checksHammingDistanceAtThreshold() {
  assertEquals(1,SimilarImages.groups(listOf("a" to 0L,"b" to 63L)).size)
  assertTrue(SimilarImages.groups(listOf("a" to 0L,"b" to 127L)).isEmpty())
 }
}

