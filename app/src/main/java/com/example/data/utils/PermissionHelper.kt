package com.example.data.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class NearbyPermissionType {
    BLUETOOTH,
    WIFI,
    LOCATION
}

data class PermissionDetail(
    val permission: String,
    val type: NearbyPermissionType,
    val name: String,
    val purpose: String
)

object PermissionHelper {

    fun getBluetoothPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            emptyList()
        }
    }

    fun getWifiPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            emptyList()
        }
    }

    fun getLocationPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            emptyList()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    fun getNearbyConnectionsPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        permissions.addAll(getBluetoothPermissions())
        permissions.addAll(getWifiPermissions())
        permissions.addAll(getLocationPermissions())
        return permissions.distinct()
    }

    fun getNearbyPermissions(): List<String> = getNearbyConnectionsPermissions()

    fun getPermissionDetails(): List<PermissionDetail> {
        val details = mutableListOf<PermissionDetail>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            details.add(
                PermissionDetail(
                    permission = Manifest.permission.BLUETOOTH_SCAN,
                    type = NearbyPermissionType.BLUETOOTH,
                    name = "Bluetooth Scan",
                    purpose = "Discovers nearby peer devices without internet connection"
                )
            )
            details.add(
                PermissionDetail(
                    permission = Manifest.permission.BLUETOOTH_ADVERTISE,
                    type = NearbyPermissionType.BLUETOOTH,
                    name = "Bluetooth Advertise",
                    purpose = "Broadcasts your device beacon so senders can locate you"
                )
            )
            details.add(
                PermissionDetail(
                    permission = Manifest.permission.BLUETOOTH_CONNECT,
                    type = NearbyPermissionType.BLUETOOTH,
                    name = "Bluetooth Connect",
                    purpose = "Pairs and manages local communication handshakes"
                )
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            details.add(
                PermissionDetail(
                    permission = Manifest.permission.NEARBY_WIFI_DEVICES,
                    type = NearbyPermissionType.WIFI,
                    name = "Nearby Wi-Fi Devices",
                    purpose = "Establishes ultra-fast local Wi-Fi Direct peer-to-peer data channels"
                )
            )
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            details.add(
                PermissionDetail(
                    permission = Manifest.permission.ACCESS_FINE_LOCATION,
                    type = NearbyPermissionType.LOCATION,
                    name = "Precise Location",
                    purpose = "Required by Android OS to scan for local Bluetooth and Wi-Fi Direct radios"
                )
            )
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                details.add(
                    PermissionDetail(
                        permission = Manifest.permission.ACCESS_COARSE_LOCATION,
                        type = NearbyPermissionType.LOCATION,
                        name = "Approximate Location",
                        purpose = "Allows discovering nearby hardware endpoints"
                    )
                )
            }
        }
        return details
    }

    fun getMediaPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun getContactsPermissions(): List<String> {
        return listOf(Manifest.permission.READ_CONTACTS)
    }

    fun getCameraPermission(): String {
        return Manifest.permission.CAMERA
    }

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    fun hasPermissions(context: Context, permissions: List<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasBluetoothPermissions(context: Context): Boolean {
        return hasPermissions(context, getBluetoothPermissions())
    }

    fun hasWifiPermissions(context: Context): Boolean {
        return hasPermissions(context, getWifiPermissions())
    }

    fun hasLocationPermissions(context: Context): Boolean {
        return hasPermissions(context, getLocationPermissions())
    }

    fun hasAllNearbyPermissions(context: Context): Boolean {
        return hasPermissions(context, getNearbyConnectionsPermissions())
    }
}
