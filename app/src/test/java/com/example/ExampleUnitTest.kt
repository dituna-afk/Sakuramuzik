package com.example

import com.example.data.BebopAnimationIntensity
import com.example.data.BebopAnimationSpeed
import com.example.data.BebopPresetProfile
import com.example.data.CowboyBebopThemeConfig
import com.example.downloader.MediaTrackInfo
import com.example.downloader.RemotePlaylistSummary
import com.example.downloader.YouTubeStreamResolver
import com.example.model.LrcParser
import com.example.ui.components.GriffithThemeQuotes
import com.example.ui.theme.AppTheme
import com.example.ui.theme.buildCowboyBebopPalette
import com.example.ui.theme.toPalette
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {

    @Test
    fun lrcParser_parsesTimestampsAndBilingualTranslations() {
        val sampleLrc = """
            [00:12.50]First line of song • [TR: Şarkının ilk satırı]
            [01:05.00]Second line of song
        """.trimIndent()

        val parsed = LrcParser.parse(sampleLrc)
        assertEquals(2, parsed.size)
        assertEquals(12_500L, parsed[0].timeMs)
        assertEquals("First line of song", parsed[0].text)
        assertEquals("Şarkının ilk satırı", parsed[0].translatedText)
        assertEquals(65_000L, parsed[1].timeMs)
        assertEquals("Second line of song", parsed[1].text)
    }

    @Test
    fun lrcParser_formatsTimestampCorrectly() {
        val formatted = LrcParser.formatTimestamp(75_340L)
        assertTrue(formatted.startsWith("01:15"))
    }

    @Test
    fun playlistIsolation_excludesRecommendedCarouselsAutomixAndSectionContinuations() {
        val rootJson = JSONObject(
            """
            {
              "contents": {
                "twoColumnBrowseResultsRenderer": {
                  "secondaryContents": {
                    "sectionListRenderer": {
                      "contents": [
                        {
                          "musicPlaylistShelfRenderer": {
                            "playlistId": "PL_REAL_123",
                            "contents": [
                              {
                                "musicResponsiveListItemRenderer": {
                                  "playlistItemData": { "videoId": "realSong001" },
                                  "flexColumns": [
                                    { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Real Track 1" }] } } },
                                    { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Artist A" }] } } }
                                  ]
                                }
                              },
                              {
                                "musicResponsiveListItemRenderer": {
                                  "playlistItemData": { "videoId": "realSong002" },
                                  "flexColumns": [
                                    { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Real Track 2" }] } } },
                                    { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Artist B" }] } } }
                                  ]
                                }
                              }
                            ],
                            "continuations": [
                              { "nextContinuationData": { "continuation": "TOKEN_REAL_PLAYLIST_PAGE_2" } }
                            ]
                          }
                        },
                        {
                          "musicCarouselShelfRenderer": {
                            "contents": [
                              {
                                "musicResponsiveListItemRenderer": {
                                  "playlistItemData": { "videoId": "recommended999" },
                                  "flexColumns": [
                                    { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Recommended Intruder Song" }] } } }
                                  ]
                                }
                              }
                            ],
                            "continuations": [
                              { "nextContinuationData": { "continuation": "TOKEN_RECOMMENDED_INTRUDER" } }
                            ]
                          }
                        },
                        {
                          "automixPreviewVideoRenderer": {
                            "content": {
                              "automixPlaylistVideoRenderer": {
                                "navigationEndpoint": { "watchPlaylistEndpoint": { "playlistId": "RDAMPL_INTRUDER" } }
                              }
                            }
                          }
                        }
                      ],
                      "continuations": [
                        { "nextContinuationData": { "continuation": "TOKEN_SECTION_LIST_AUTOPLAY_INTRUDER" } }
                      ]
                    }
                  }
                }
              }
            }
            """.trimIndent()
        )

        val resolver = YouTubeStreamResolver()
        val parsedTracks = mutableListOf<MediaTrackInfo>()
        val seenIds = mutableSetOf<String>()

        resolver.parseWebRemixTracks(
            node = rootJson,
            playlistId = "PL_REAL_123",
            playlistTitle = "My Custom Playlist",
            playlistCoverUrl = null,
            outList = parsedTracks,
            seenIds = seenIds,
            insideValidPlaylistContainer = false
        )

        assertEquals("Only the 2 real playlist tracks must be parsed", 2, parsedTracks.size)
        assertEquals(listOf("realSong001", "realSong002"), parsedTracks.map { it.id })
        assertFalse("Recommended carousel track must NOT be included", parsedTracks.any { it.id == "recommended999" })

        val nextToken = resolver.findContinuationToken(rootJson, insideValidPlaylistContainer = false)
        assertEquals("TOKEN_REAL_PLAYLIST_PAGE_2", nextToken)

        // When musicPlaylistShelfRenderer has no continuation, sectionListRenderer's continuation must NOT be used
        val rootWithoutShelfContinuation = JSONObject(
            """
            {
              "sectionListRenderer": {
                "contents": [
                  {
                    "musicPlaylistShelfRenderer": {
                      "contents": []
                    }
                  }
                ],
                "continuations": [
                  { "nextContinuationData": { "continuation": "TOKEN_SECTION_LIST_AUTOPLAY_INTRUDER" } }
                ]
              }
            }
            """.trimIndent()
        )
        val intruderToken = resolver.findContinuationToken(rootWithoutShelfContinuation, insideValidPlaylistContainer = false)
        assertNull("sectionListRenderer continuation must be ignored to prevent recommended tracks", intruderToken)
    }

    @Test
    fun libraryPlaylistsParser_extractsPlaylistTitleAndExactTrackCount_ignoringAlbumsAndSingleTracks() {
        val libraryBrowseJson = JSONObject(
            """
            {
              "contents": [
                {
                  "musicTwoRowItemRenderer": {
                    "title": { "runs": [{ "text": "Gece Sürüşü 2026" }] },
                    "subtitle": { "runs": [{ "text": "Oynatma listesi • 48 şarkı" }] },
                    "navigationEndpoint": { "browseEndpoint": { "browseId": "VLPL_NIGHT_2026" } }
                  }
                },
                {
                  "musicTwoRowItemRenderer": {
                    "title": { "runs": [{ "text": "Workout Synthwave" }] },
                    "subtitle": { "runs": [{ "text": "Playlist • 125 tracks" }] },
                    "navigationEndpoint": { "browseEndpoint": { "browseId": "VLPL_SYNTH_125" } }
                  }
                },
                {
                  "musicTwoRowItemRenderer": {
                    "title": { "runs": [{ "text": "Some Artist Channel" }] },
                    "subtitle": { "runs": [{ "text": "Artist • 12K subscribers" }] },
                    "navigationEndpoint": { "browseEndpoint": { "browseId": "UC_CHANNEL_ID" } }
                  }
                },
                {
                  "musicTwoRowItemRenderer": {
                    "title": { "runs": [{ "text": "Some Album" }] },
                    "subtitle": { "runs": [{ "text": "Album • 2025" }] },
                    "navigationEndpoint": { "browseEndpoint": { "browseId": "MPREb_ALBUM_ID" } }
                  }
                }
              ]
            }
            """.trimIndent()
        )

        val resolver = YouTubeStreamResolver()
        val summaries = mutableListOf<RemotePlaylistSummary>()
        val seenIds = mutableSetOf<String>()

        resolver.parseLibraryPlaylistsFromNode(libraryBrowseJson, summaries, seenIds)

        assertEquals(2, summaries.size)
        assertEquals("PL_NIGHT_2026", summaries[0].playlistId)
        assertEquals("Gece Sürüşü 2026", summaries[0].title)
        assertEquals(48, summaries[0].trackCount)

        assertEquals("PL_SYNTH_125", summaries[1].playlistId)
        assertEquals("Workout Synthwave", summaries[1].title)
        assertEquals(125, summaries[1].trackCount)
    }

    @Test
    fun griffithQuotesAndRandomLines_returnValidNonBlankStrings() {
        val quote1 = GriffithThemeQuotes.quoteForKey("track_01")
        val quote2 = GriffithThemeQuotes.quoteForKey(null)
        val line1 = GriffithThemeQuotes.randomLineForContext("track_01")
        val line2 = GriffithThemeQuotes.randomLineForContext("empty_home")

        assertTrue(quote1.isNotBlank())
        assertTrue(quote2.isNotBlank())
        assertTrue(line1.isNotBlank())
        assertTrue(line2.isNotBlank())
    }

    @Test
    fun cowboyBebopThemeConfig_validatesAndAppliesPresetsAccurately() {
        val defaults = CowboyBebopThemeConfig()
        assertTrue(defaults.cbFayePurpleYellowPaletteEnabled)
        assertTrue(defaults.cbNightFriendlyLowBrightnessEnabled)
        assertEquals(BebopAnimationIntensity.LOW, defaults.cbAnimationIntensity)
        assertTrue(defaults.cbPauseEffectsInBackgroundEnabled)

        val nightPreset = CowboyBebopThemeConfig.fromPreset(BebopPresetProfile.NIGHT)
        assertTrue(nightPreset.cbLowEffectModeEnabled)
        assertFalse(nightPreset.cbMasterAnimationsEnabled)
        assertEquals(BebopAnimationIntensity.OFF, nightPreset.cbAnimationIntensity)

        val balancedPreset = CowboyBebopThemeConfig.fromPreset(BebopPresetProfile.BALANCED)
        assertTrue(balancedPreset.cbFayePurpleYellowPaletteEnabled)
        assertEquals(BebopAnimationIntensity.LOW, balancedPreset.cbAnimationIntensity)

        val cinematicPreset = CowboyBebopThemeConfig.fromPreset(BebopPresetProfile.CINEMATIC)
        assertTrue(cinematicPreset.cbMasterAnimationsEnabled)
        assertEquals(BebopAnimationIntensity.MEDIUM, cinematicPreset.cbAnimationIntensity)
        assertEquals(BebopAnimationSpeed.NORMAL, cinematicPreset.cbAnimationSpeed)
        assertTrue(cinematicPreset.cbSubtleCardGlowEnabled)

        // Out-of-bounds values must be clamped by validated()
        val clamped = defaults.copy(
            cbPurpleToneIntensity = 5f,
            cbAmberYellowAccentIntensity = -2f,
            cbCardSurfaceOpacity = 0.05f
        ).validated()
        assertEquals(1.0f, clamped.cbPurpleToneIntensity, 0.001f)
        assertEquals(0.0f, clamped.cbAmberYellowAccentIntensity, 0.001f)
        assertEquals(0.30f, clamped.cbCardSurfaceOpacity, 0.001f)
    }

    @Test
    fun cowboyBebopPalette_usesNightFriendlyFayePurpleAmberAndDoesNotAffectOtherThemes() {
        val defaultBebopPalette = buildCowboyBebopPalette(CowboyBebopThemeConfig())
        val sakuraPalette = AppTheme.SAKURA_DARK.toPalette()
        val griffithPalette = AppTheme.BERSERK_GRIFFITH.toPalette()

        assertEquals(AppTheme.COWBOY_BEBOP_SPIKE, defaultBebopPalette.theme)
        assertNotEquals(defaultBebopPalette.primaryAccent, sakuraPalette.primaryAccent)
        assertNotEquals(defaultBebopPalette.background, griffithPalette.background)

        // Darker background level produces darker surface/background
        val brightBebop = buildCowboyBebopPalette(
            CowboyBebopThemeConfig(
                cbNightFriendlyLowBrightnessEnabled = false,
                cbBackgroundDarknessLevel = 0.0f,
                cbTextContrastBoostEnabled = true
            )
        )
        assertNotNull(brightBebop)
        assertNotEquals(defaultBebopPalette.background, brightBebop.background)
    }
}
