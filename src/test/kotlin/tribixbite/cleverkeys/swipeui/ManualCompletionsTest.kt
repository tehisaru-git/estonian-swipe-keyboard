package tribixbite.cleverkeys.swipeui

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import android.widget.EditText
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import tribixbite.cleverkeys.estonianui.KeyboardLanguage

@RunWith(RobolectricTestRunner::class)
class ManualCompletionsTest {
    private val et = listOf("tere", "terve", "tervitus", "tore").mapIndexed { i, w -> w to i }
    private val en = listOf("there", "the", "then").mapIndexed { i, w -> w to i }
    private fun info(type: Int = InputType.TYPE_CLASS_TEXT) = EditorInfo().apply { inputType = type }

    @Test fun manualPrefixUsesSelectedLanguageAndNeverCorrectsAutomatically() {
        val completions = ManualCompletions.fromEntries(et, en)
        val field = Field("say ter")
        assertThat(completions.update(field.connection, info(), KeyboardLanguage.ESTONIAN)).containsExactly("tere", "terve", "tervitus").inOrder()
        assertThat(field.text).isEqualTo("say ter")
        assertThat(completions.update(field.connection, info(), KeyboardLanguage.ENGLISH)).isEmpty()
        field.text = "say th"
        field.caret = field.text.length
        field.selectionStart = field.caret
        assertThat(completions.update(field.connection, info(), KeyboardLanguage.ENGLISH)).containsExactly("there", "the", "then").inOrder()
    }

    @Test fun collapsedSelectionMayReturnNullInOrdinaryEditors() {
        val completions = ManualCompletions.fromEntries(et, en)
        val field = Field("ter")
        every { field.connection.getSelectedText(0) } answers {
            if (field.selectionStart == field.caret) null
            else field.text.substring(field.selectionStart, field.caret)
        }
        assertThat(completions.update(field.connection, info(), KeyboardLanguage.ESTONIAN))
            .containsExactly("tere", "terve", "tervitus").inOrder()
        assertThat(completions.choose(field.connection, 0)).isTrue()
        assertThat(field.text).isEqualTo("tere")
    }

    @Test fun realEditTextConnectionCompletesOnlyTheWordAtTheCaret() {
        val field = EditText(RuntimeEnvironment.getApplication())
        field.setText("tere ter rest")
        field.setSelection(8)
        val connection = field.onCreateInputConnection(info())
        val completions = ManualCompletions.fromEntries(et, en)
        assertThat(completions.update(connection, info(), KeyboardLanguage.ESTONIAN))
            .contains("terve")
        assertThat(completions.choose(connection, 1)).isTrue()
        assertThat(field.text.toString()).isEqualTo("tere terve rest")
    }

    @Test fun selectingSuggestionKeepsTypedInitialCase() {
        val completions = ManualCompletions.fromEntries(et, en)
        val field = Field("Ter")
        assertThat(completions.update(field.connection, info(), KeyboardLanguage.ESTONIAN))
            .containsExactly("Tere", "Terve", "Tervitus").inOrder()
        assertThat(completions.choose(field.connection, 0)).isTrue()
        assertThat(field.text).isEqualTo("Tere")
        field.text = "TER"
        field.caret = field.text.length
        field.selectionStart = field.caret
        assertThat(completions.update(field.connection, info(), KeyboardLanguage.ESTONIAN))
            .containsExactly("TERE", "TERVE", "TERVITUS").inOrder()
        assertThat(completions.choose(field.connection, 1)).isTrue()
        assertThat(field.text).isEqualTo("TERVE")
    }

    @Test fun packagedDictionariesOfferRealEtAndEnPrefixes() {
        val completions = ManualCompletions.fromBundledAssets(RuntimeEnvironment.getApplication())
        val field = Field("ter")
        val etWords = completions.update(field.connection, info(), KeyboardLanguage.ESTONIAN)
        assertThat(etWords).isNotEmpty()
        assertThat(etWords.all { it.startsWith("ter", ignoreCase = true) }).isTrue()
        field.text = "th"
        field.caret = 2
        field.selectionStart = 2
        val enWords = completions.update(field.connection, info(), KeyboardLanguage.ENGLISH)
        assertThat(enWords).isNotEmpty()
        assertThat(enWords.all { it.startsWith("th", ignoreCase = true) }).isTrue()
    }

    @Test fun userAddedMhmIsLiveOnlyForItsChosenLanguageAndCanBeDeleted() {
        val ownEt = mutableListOf("mhm")
        val completions = ManualCompletions.fromEntries(et, en) { language ->
            if (language == KeyboardLanguage.ESTONIAN) ownEt else emptyList()
        }
        val field = Field("mh")
        assertThat(completions.update(field.connection, info(), KeyboardLanguage.ESTONIAN))
            .containsExactly("mhm")
        assertThat(completions.update(field.connection, info(), KeyboardLanguage.ENGLISH)).isEmpty()
        assertThat(completions.update(field.connection, info(), KeyboardLanguage.ESTONIAN)).contains("mhm")
        assertThat(completions.choose(field.connection, 0)).isTrue()
        assertThat(field.text).isEqualTo("mhm")
        ownEt.clear()
        field.text = "mh"; field.caret = 2; field.selectionStart = 2
        assertThat(completions.update(field.connection, info(), KeyboardLanguage.ESTONIAN)).isEmpty()
    }

    @Test fun replacementSelectsOnlyVerifiedWordAndLeavesOtherWordsIntact() {
        val completions = ManualCompletions.fromEntries(et, en)
        val field = Field("tere ter rest", 8)
        assertThat(completions.update(field.connection, info(), KeyboardLanguage.ESTONIAN)).contains("terve")
        assertThat(completions.choose(field.connection, 1)).isTrue()
        assertThat(field.text).isEqualTo("tere terve rest")
    }

    @Test fun staleCaretOrChangedContextNeverReplacesMatchingSuffix() {
        val completions = ManualCompletions.fromEntries(et, en)
        val field = Field("first ter")
        completions.update(field.connection, info(), KeyboardLanguage.ESTONIAN)
        field.text = "other ter"
        field.caret = field.text.length
        assertThat(completions.choose(field.connection, 0)).isFalse()
        assertThat(field.text).isEqualTo("other ter")
        completions.update(field.connection, info(), KeyboardLanguage.ESTONIAN)
        field.caret = 3
        assertThat(completions.choose(field.connection, 0)).isFalse()
        assertThat(field.text).isEqualTo("other ter")
    }

    @Test fun secretNumericNoSuggestionsAndSelectedTextAreGated() {
        val completions = ManualCompletions.fromEntries(et, en)
        val field = Field("ter")
        val types = listOf(InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        for (type in types) assertThat(completions.update(field.connection, info(type), KeyboardLanguage.ESTONIAN)).isEmpty()
        assertThat(completions.update(field.connection, info().apply {
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }, KeyboardLanguage.ESTONIAN)).isEmpty()
        field.selectionStart = 0
        assertThat(completions.update(field.connection, info(), KeyboardLanguage.ESTONIAN)).isEmpty()
    }

    private class Field(var text: String, var caret: Int = text.length) {
        var selectionStart = caret
        val connection: InputConnection = mockk {
            every { getExtractedText(any(), any()) } answers {
                ExtractedText().apply { startOffset = 0; selectionStart = this@Field.selectionStart; selectionEnd = caret }
            }
            every { getTextBeforeCursor(any(), any()) } answers {
                text.substring(0, caret).takeLast(firstArg())
            }
            every { getTextAfterCursor(any(), any()) } answers {
                text.substring(caret).take(firstArg())
            }
            every { getSelectedText(any()) } answers {
                if (selectionStart == caret) "" else text.substring(selectionStart, caret)
            }
            every { setSelection(any(), any()) } answers {
                selectionStart = firstArg(); caret = secondArg(); true
            }
            every { commitText(any(), any()) } answers {
                val value = firstArg<CharSequence>().toString()
                text = text.substring(0, selectionStart) + value + text.substring(caret)
                caret = selectionStart + value.length
                selectionStart = caret
                true
            }
        }
    }
}
