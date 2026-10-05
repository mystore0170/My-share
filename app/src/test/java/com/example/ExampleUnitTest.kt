package com.example

import com.example.data.utils.QrCodeDecoder
import com.example.data.utils.QrCodeGenerator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {
    @Test
    fun qrCodePayload_formatAndParse() {
        val deviceName = "Pixel 8 Pro"
        val token = "998877"
        val payload = QrCodeGenerator.createPairingPayload(deviceName, token)

        assertEquals("NEARSHARE:v1:DEVICE:Pixel 8 Pro:TOKEN:998877", payload)

        val parsed = QrCodeGenerator.parsePairingPayload(payload)
        assertNotNull(parsed)
        assertEquals("Pixel 8 Pro", parsed?.deviceName)
        assertEquals("998877", parsed?.token)
    }

    @Test
    fun qrCodePayload_legacyDeviceNameSupport() {
        val legacyPayload = "Galaxy S24"
        val parsed = QrCodeGenerator.parsePairingPayload(legacyPayload)
        assertNotNull(parsed)
        assertEquals("Galaxy S24", parsed?.deviceName)
        assertNull(parsed?.token)
    }

    @Test
    fun qrCodeGenerationAndDecoding_roundTrip() {
        val testPayload = "NEARSHARE:v1:DEVICE:TestDevice:TOKEN:123456"
        val bitmap = QrCodeGenerator.generateAndroidBitmap(testPayload, 400)
        assertNotNull(bitmap)

        val decoded = bitmap?.let { QrCodeDecoder.decodeBitmap(it) }
        assertEquals(testPayload, decoded)
    }

    @Test
    fun mimeType_categoryResolution() {
        assertEquals(com.example.data.model.TransferCategory.PHOTOS, com.example.data.utils.FileUtils.getCategoryFromMimeType("image/jpeg"))
        assertEquals(com.example.data.model.TransferCategory.PHOTOS, com.example.data.utils.FileUtils.getCategoryFromMimeType("image/png"))
        assertEquals(com.example.data.model.TransferCategory.VIDEOS, com.example.data.utils.FileUtils.getCategoryFromMimeType("video/mp4"))
        assertEquals(com.example.data.model.TransferCategory.AUDIO, com.example.data.utils.FileUtils.getCategoryFromMimeType("audio/mpeg"))
        assertEquals(com.example.data.model.TransferCategory.FILES, com.example.data.utils.FileUtils.getCategoryFromMimeType("application/pdf"))
        assertEquals(com.example.data.model.TransferCategory.FILES, com.example.data.utils.FileUtils.getCategoryFromMimeType("text/plain"))
        assertEquals(com.example.data.model.TransferCategory.FILES, com.example.data.utils.FileUtils.getCategoryFromMimeType(null))
    }

    @Test
    fun permissionHelper_android13NearbyWifiDevicesIncluded() {
        val wifiPerms = com.example.data.utils.PermissionHelper.getWifiPermissions()
        assertTrue(wifiPerms.contains(android.Manifest.permission.NEARBY_WIFI_DEVICES))

        val allNearby = com.example.data.utils.PermissionHelper.getNearbyConnectionsPermissions()
        assertTrue(allNearby.contains(android.Manifest.permission.NEARBY_WIFI_DEVICES))
        assertTrue(allNearby.contains(android.Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(allNearby.contains(android.Manifest.permission.BLUETOOTH_ADVERTISE))
        assertTrue(allNearby.contains(android.Manifest.permission.BLUETOOTH_CONNECT))
    }
}
