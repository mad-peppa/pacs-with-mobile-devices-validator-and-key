package by.snegoviki2.key

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class NfcService : HostApduService() {
    private val settingsManager = SettingsManager(this)
    private val keyStoreManager = KeyStoreManager()

    override fun onDeactivated(reason: Int) {}

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        if (isGetDataCommand(commandApdu)) {
            val employeeId = getEmployeeId()
            val timestamp = getTimestamp()
            val signature = getSignature(employeeId, timestamp)

            val json = JSONObject().apply {
                put("employee_id", employeeId)
                put("timestamp", timestamp)
                put("signature", signature)
            }.toString()

            return (json.toByteArray() + byteArrayOf(0x90.toByte(), 0x00))
        }

        return byteArrayOf(0x6F.toByte(), 0x00)
    }

    private fun isGetDataCommand(apdu: ByteArray): Boolean {
        return apdu.size >= 4 &&
                apdu[0] == 0x00.toByte() &&  // CLA
                apdu[1] == 0xCA.toByte() &&  // INS = GET DATA
                apdu[2] == 0x00.toByte() &&  // P1
                apdu[3] == 0x00.toByte()     // P2
    }

    private fun getEmployeeId(): String {
        var employeeId = ""
        runBlocking {
            employeeId = settingsManager.getEmployeeId()
        }
        return employeeId
    }

    private fun getTimestamp(): Long {
        return System.currentTimeMillis() / 1000
    }

    private fun getSignature(employeeId: String, timestamp: Long): String? {
        return keyStoreManager.signData("$employeeId|$timestamp")
    }
}