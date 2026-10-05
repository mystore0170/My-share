package com.example.data.utils

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import com.example.data.model.ContactItem
import java.io.File
import java.io.FileOutputStream

object ContactHelper {

    fun fetchContacts(context: Context): List<ContactItem> {
        val contactsList = mutableListOf<ContactItem>()
        val contentResolver = context.contentResolver
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            val seenNumbers = HashSet<String>()

            while (it.moveToNext()) {
                val id = if (idIndex >= 0) it.getString(idIndex) ?: "" else ""
                val name = if (nameIndex >= 0) it.getString(nameIndex) ?: "Unknown" else "Unknown"
                val rawNumber = if (numberIndex >= 0) it.getString(numberIndex) ?: "" else ""
                val cleanNumber = rawNumber.replace(" ", "").replace("-", "")

                if (cleanNumber.isNotEmpty() && !seenNumbers.contains(cleanNumber)) {
                    seenNumbers.add(cleanNumber)
                    contactsList.add(
                        ContactItem(
                            id = id,
                            name = name,
                            phoneNumber = rawNumber
                        )
                    )
                }
            }
        }

        return contactsList
    }

    fun generateVCard(name: String, phoneNumber: String, email: String? = null): String {
        val builder = StringBuilder()
        builder.append("BEGIN:VCARD\r\n")
        builder.append("VERSION:3.0\r\n")
        builder.append("FN:").append(name).append("\r\n")
        builder.append("N:").append(name).append(";;;;\r\n")
        builder.append("TEL;TYPE=CELL:").append(phoneNumber).append("\r\n")
        if (!email.isNullOrEmpty()) {
            builder.append("EMAIL;TYPE=INTERNET:").append(email).append("\r\n")
        }
        builder.append("END:VCARD\r\n")
        return builder.toString()
    }

    fun saveContactsToVcfFile(context: Context, contacts: List<ContactItem>): File {
        val cacheDir = File(context.cacheDir, "contacts_vcf")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val vcfFile = File(cacheDir, "contacts_${System.currentTimeMillis()}.vcf")
        FileOutputStream(vcfFile).use { fos ->
            for (contact in contacts) {
                val vcard = generateVCard(contact.name, contact.phoneNumber, contact.email)
                fos.write(vcard.toByteArray(Charsets.UTF_8))
            }
        }
        return vcfFile
    }
}
