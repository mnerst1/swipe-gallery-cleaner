package com.example.swipecleaner
import org.junit.Assert.*
import org.junit.Test
class ReviewLogicTest {
 @Test fun advancesToUndecidedAndWraps() { assertEquals(2, ReviewLogic.nextUndecided(listOf("a","b","c"), mapOf("a" to "keep", "b" to "delete"), 0)); assertNull(ReviewLogic.nextUndecided(listOf("a"), mapOf("a" to "keep"), 0)) }
 @Test fun undoRestoresOrRemovesPriorDecision() { val d=mapOf("a" to "delete", "b" to "keep"); assertEquals(mapOf("b" to "keep"), ReviewLogic.restore(d,"a",null)); assertEquals("keep", ReviewLogic.restore(d,"a","keep")["a"]) }
}
