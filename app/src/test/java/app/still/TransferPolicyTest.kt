package app.still

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class TransferPolicyTest {
    private val allDomains = setOf(
        "root", "file", "database", "sharedpref", "external",
        "device_root", "device_file", "device_database", "device_sharedpref",
    )
    private val transferFiles = setOf("database" to "still.db")

    @Test
    fun manifestParticipatesInTransferAndReferencesBothRuleFormats() {
        val application = parse("AndroidManifest.xml").getElementsByTagName("application").item(0) as Element
        assertEquals("true", application.getAttribute("android:allowBackup"))
        assertEquals("@xml/backup_rules", application.getAttribute("android:fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", application.getAttribute("android:dataExtractionRules"))
    }

    @Test
    fun modernCloudBackupExcludesEveryStorageDomain() {
        val root = parse("res/xml/data_extraction_rules.xml")
        val cloud = root.getElementsByTagName("cloud-backup").item(0) as Element
        assertEquals(0, cloud.getElementsByTagName("include").length)
        assertAllDomainsExcluded(cloud)
    }

    @Test
    fun modernTransferAllowsOnlyJournal() {
        val root = parse("res/xml/data_extraction_rules.xml")
        val transfer = root.getElementsByTagName("device-transfer").item(0) as Element
        assertEquals(transferFiles, includes(transfer))
        assertEquals(1, transfer.getElementsByTagName("include").length)
        assertEquals(0, transfer.getElementsByTagName("exclude").length)
    }

    @Test
    fun androidNineToElevenRequireDeviceTransferForEveryIncludedFile() {
        val legacy = parse("res/xml-v28/backup_rules.xml")
        assertEquals("full-backup-content", legacy.tagName)
        assertEquals(transferFiles, includes(legacy))
        val nodes = legacy.getElementsByTagName("include")
        for (index in 0 until nodes.length) {
            assertEquals("deviceToDeviceTransfer", (nodes.item(index) as Element).getAttribute("requireFlags"))
        }
    }

    @Test
    fun androidEightCannotAccidentallyFallBackToCloudBackup() {
        val legacy = parse("res/xml/backup_rules.xml")
        assertEquals("full-backup-content", legacy.tagName)
        assertEquals(0, legacy.getElementsByTagName("include").length)
        assertAllDomainsExcluded(legacy)
    }

    private fun assertAllDomainsExcluded(element: Element) {
        val nodes = element.getElementsByTagName("exclude")
        val domains = (0 until nodes.length).map { index ->
            val exclude = nodes.item(index) as Element
            assertEquals(".", exclude.getAttribute("path"))
            exclude.getAttribute("domain")
        }.toSet()
        assertEquals(allDomains, domains)
    }

    @Test
    fun manifestHasNoReminderPermissionsOrReceivers() {
        val root = parse("AndroidManifest.xml")
        assertEquals(0, root.getElementsByTagName("uses-permission").length)
        assertEquals(0, root.getElementsByTagName("receiver").length)
    }

    private fun includes(element: Element): Set<Pair<String, String>> {
        val nodes = element.getElementsByTagName("include")
        return (0 until nodes.length).map {
            val include = nodes.item(it) as Element
            include.getAttribute("domain") to include.getAttribute("path")
        }.toSet()
    }

    private fun parse(path: String): Element {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        return factory.newDocumentBuilder().parse(File("src/main/$path")).documentElement
    }
}
