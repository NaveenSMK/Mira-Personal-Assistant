package com.naveensmk.mira

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidIntentParserTest {
    @Test fun browserSearchNeverRoutesToMaps() {
        val phrases = listOf(
            "open browser and search for Cricbuzz",
            "open browser and do a search for Cricbuzz",
            "search for Cricbuzz",
            "search the web for Cricbuzz"
        )
        phrases.forEach { phrase ->
            val parsed = AndroidIntentParser.parse(phrase)
            assertEquals(phrase, AndroidIntentAction.WEB_SEARCH, parsed?.action)
            assertEquals("Cricbuzz", parsed?.query)
        }
    }

    @Test fun openBrowserDoesNotBecomeMapsSearch() {
        assertEquals(AndroidIntentAction.OPEN_BROWSER, AndroidIntentParser.parse("Open browser")?.action)
        assertEquals(AndroidIntentAction.OPEN_BROWSER, AndroidIntentParser.parse("open browser and do a search")?.action)
    }

    @Test fun youtubeOpeningAndSearchAreSeparateActions() {
        assertEquals(AndroidIntentAction.OPEN_YOUTUBE, AndroidIntentParser.parse("open YouTube app")?.action)
        val search = AndroidIntentParser.parse("open YouTube app and search for Tamil songs")
        assertEquals(AndroidIntentAction.YOUTUBE_SEARCH, search?.action)
        assertEquals("Tamil songs", search?.query)
    }

    @Test fun mapsIsSelectedOnlyForExplicitMapsIntent() {
        assertEquals(AndroidIntentAction.MAPS_SEARCH, AndroidIntentParser.parse("search maps for petrol stations")?.action)
        assertEquals(AndroidIntentAction.WEB_SEARCH, AndroidIntentParser.parse("search for Cricbuzz")?.action)
    }

    @Test fun contactSearchAndCallingAreDistinct() {
        val search = AndroidIntentParser.parse("open contacts and search for wife")
        assertEquals(AndroidIntentAction.CONTACT_SEARCH, search?.action)
        assertEquals("wife", search?.query)
        val call = AndroidIntentParser.parse("call my wife")
        assertEquals(AndroidIntentAction.CALL_CONTACT, call?.action)
        assertEquals("wife", call?.query)
        val naturalCall = AndroidIntentParser.parse("Please make a call to my wife")
        assertEquals(AndroidIntentAction.CALL_CONTACT, naturalCall?.action)
        assertEquals("wife", naturalCall?.query)
        assertEquals(AndroidIntentAction.WEB_SEARCH, AndroidIntentParser.parse("Could you search for Cricbuzz")?.action)
    }

    @Test fun timerAndAlarmAcceptNaturalArticles() {
        assertEquals(AndroidIntentAction.SET_TIMER, AndroidIntentParser.parse("set a timer for 5 minutes")?.action)
        assertEquals(AndroidIntentAction.SET_TIMER, AndroidIntentParser.parse("start timer for 10 seconds")?.action)
        assertEquals(AndroidIntentAction.SET_ALARM, AndroidIntentParser.parse("set an alarm for 6:30 AM")?.action)
        assertEquals(AndroidIntentAction.SET_ALARM, AndroidIntentParser.parse("wake me at 7 AM")?.action)
    }
}
