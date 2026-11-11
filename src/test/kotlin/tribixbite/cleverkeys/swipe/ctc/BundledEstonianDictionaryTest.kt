package tribixbite.cleverkeys.swipe.ctc

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import tribixbite.cleverkeys.swipe.geometric.CkdtDictionaryReader
import java.io.File
import java.security.MessageDigest

/** The APK's offline et source is the released CKDT, not a second generated vocabulary. */
class BundledEstonianDictionaryTest {
    @After fun resetResolver() = CtcImportedPackSupport.installResolver(null)

    @Test fun bundledBytesMatchPublishedPackAndContainEstonianForms() {
        val asset = File("src/main/assets/dictionaries/et_enhanced.bin")
        val bytes = asset.readBytes()
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        assertThat(sha256)
            .isEqualTo("9b4e79ad7ebefb59d7a23b3d719fcc62d8d8b33afa5d2638461e8a7fdfe9abe2")
        val entries = CkdtDictionaryReader.readEntries(bytes)
        assertThat(entries.size).isEqualTo(95_000)
        assertThat(entries.map { it.word }).contains("tere")
    }

    @Test fun bundledAttributionAndManifestRetainPublishedMetadata() {
        val manifest = File("src/main/assets/estonian/manifest.json").readText()
        val notice = File("src/main/assets/estonian/NOTICE.txt").readText()
        assertThat(manifest).contains("\"code\": \"et\"")
        assertThat(manifest).contains("\"wordCount\": 95000")
        assertThat(manifest).contains("CC-BY-4.0")
        assertThat(notice).contains("Leipzig Corpora Collection")
        assertThat(notice).contains("CC BY 4.0")
    }

    @Test fun freshInstallRoutesEstonianToBundledCkdt() {
        assertThat(CtcLanguageSupport.sourceFor("et-EE"))
            .isEqualTo(CtcLanguageSupport.LexiconSource.CKDT_BIN)
        assertThat(CtcLanguageSupport.assetFor("et"))
            .isEqualTo("dictionaries/et_enhanced.bin")
        assertThat(CtcLanguageSupport.langpackRelativePath("et")).isNull()
        assertThat(CtcScoringParams.presetFor("et").lambda).isEqualTo(2.0)
    }

    @Test fun eligibleImportedEstonianPackTakesPrecedenceWithoutChangingOtherBundledLanguages() {
        CtcImportedPackSupport.installResolver { it == "et" }
        assertThat(CtcLanguageSupport.sourceFor("et"))
            .isEqualTo(CtcLanguageSupport.LexiconSource.CKDT_LANGPACK)
        assertThat(CtcLanguageSupport.assetFor("et")).isNull()
        assertThat(CtcLanguageSupport.langpackRelativePath("et"))
            .isEqualTo("langpacks/et/dictionary.bin")
        assertThat(CtcLanguageSupport.sourceFor("fr"))
            .isEqualTo(CtcLanguageSupport.LexiconSource.CKDT_BIN)
    }
}
