package com.araujo.karaoke_ia.online

import org.junit.Assert.*
import org.junit.Test

class OnlineSearchTest {
    @Test fun elevenCharacterNamesAreSearchTermsRatherThanVideoIds() {
        assertFalse(OnlineRepository.isVideoLink("Desconhecer"))
        assertFalse(OnlineRepository.isVideoLink("abcdefghijk"))
        assertFalse(OnlineRepository.isVideoLink("Evidências"))
    }
    @Test fun recognizesOnlyActualYoutubeVideoLinks() {
        assertTrue(OnlineRepository.isVideoLink("https://www.youtube.com/watch?v=abcdefghijk"))
        assertTrue(OnlineRepository.isVideoLink("youtu.be/abcdefghijk"))
        assertFalse(OnlineRepository.isVideoLink("https://youtube.com.evil.invalid/watch?v=abcdefghijk"))
        assertFalse(OnlineRepository.isVideoLink("https://youtube.com/results?search_query=karaoke"))
    }
}
