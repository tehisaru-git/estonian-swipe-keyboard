package tribixbite.cleverkeys

import android.util.Log
import android.util.Xml
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.kxml2.io.KXmlParser
import tribixbite.cleverkeys.prefs.LayoutsPreference
import tribixbite.cleverkeys.theme.KeyboardLooks

/** Pins the visible Estonian layout while the independent IME surface is still pending. */
class EstonianKeyboardLayoutTest {
    @Before fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        mockkStatic(Xml::class)
        every { Xml.newPullParser() } answers { KXmlParser() }
    }

    @After fun teardown() = unmockkAll()

    private fun layout(id: String): KeyboardData = KeyboardData.load_string_exn(
        File("src/main/layouts/$id.xml").readText()
    )

    private fun centres(row: KeyboardData.Row): String = row.keys.mapNotNull {
        it.getKeyValue(0)?.getString()?.takeIf { value -> value.length == 1 && value[0].isLetter() }
    }.joinToString("")

    @Test fun freshInstallOffersEstonianAndEnglishAsSeparateLayouts() {
        val defaults = LayoutsPreference.DEFAULT
        assertThat(defaults).containsExactly(
            LayoutsPreference.NamedLayout("latn_qwerty_et"),
            LayoutsPreference.NamedLayout("latn_qwerty_us_gboard_style")
        ).inOrder()
    }

    @Test fun estonianIsBoundToItsOwnLanguageAndFollowsLightDarkLook() {
        val et = layout("latn_qwerty_et")
        assertThat(et.declared_language).isEqualTo("et")
        assertThat(et.declared_theme).isEqualTo(KeyboardLooks.GBOARD)
        assertThat(PredefinedLayoutThemes.declaredThemeOf("latn_qwerty_et")).isEqualTo(KeyboardLooks.GBOARD)
        assertThat(KeyboardLooks.gboardLook.palette(true)).isNotEqualTo(KeyboardLooks.gboardLook.palette(false))
        assertThat(layout("latn_qwerty_us").declared_language).isNull()
    }

    @Test fun estonianHasUnclutteredLettersAndWorkingKeyboardControls() {
        val et = layout("latn_qwerty_et")
        assertThat(et.bottom_row).isFalse()
        assertThat(et.embedded_number_row).isTrue()
        assertThat(et.rows).hasSize(5)
        assertThat(et.rows[0].keys.map { it.getKeyValue(0)?.getString() })
            .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").inOrder()
        assertThat(centres(et.rows[1])).isEqualTo("qwertyuiopü")
        assertThat(centres(et.rows[2])).isEqualTo("asdfghjklöä")
        assertThat(centres(et.rows[3])).isEqualTo("zxcvbnmõ")
        assertThat(et.rows[3].keys.first().getKeyValue(0)).isEqualTo(KeyValue.getKeyByName("shift"))
        assertThat(et.rows[3].keys.last().getKeyValue(0)).isEqualTo(KeyValue.getKeyByName("backspace"))
        val bottom = et.rows[4].keys
        assertThat(bottom).hasSize(6)
        for ((index, name) in listOf("switch_numeric", ",", "switch_emoji", "space", ".", "enter").withIndex()) {
            val actual = bottom[index].getKeyValue(0)!!
            val expected = KeyValue.getKeyByName(name)
            // The visible ?123 alias changes the legend, not its switch action.
            if (index == 0) assertThat(actual.getEvent()).isEqualTo(expected.getEvent())
            else assertThat(actual).isEqualTo(expected)
        }
        // The language picker stays reachable even when a single layout is enabled.
        assertThat(bottom[2].getKeyValue(7)).isEqualTo(KeyValue.getKeyByName("change_method"))
        // Nothing changes the stock English pane or its independent language choice.
        val en = layout("latn_qwerty_us_gboard_style")
        assertThat(en.rows).hasSize(4)
        assertThat(en.declared_language).isEqualTo("en")
    }
}
