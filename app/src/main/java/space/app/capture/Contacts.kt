package space.app.capture

import android.content.ContentValues
import android.content.Intent
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import space.app.understand.Understood

/** Android's new-contact form, filled in from a business card. You check it and save; Space never saves contacts itself. */
fun addContactIntent(card: Understood): Intent {
    val rows = ArrayList<ContentValues>()
    card.phones.drop(1).forEach { rows += ContentValues().apply { put(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE); put(CommonDataKinds.Phone.NUMBER, it) } }
    card.emails.drop(1).forEach { rows += ContentValues().apply { put(ContactsContract.Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE); put(CommonDataKinds.Email.ADDRESS, it) } }
    card.links.forEach { rows += ContentValues().apply { put(ContactsContract.Data.MIMETYPE, CommonDataKinds.Website.CONTENT_ITEM_TYPE); put(CommonDataKinds.Website.URL, it) } }
    return Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE).apply {
        card.name?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
        card.company?.let { putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
        card.job?.let { putExtra(ContactsContract.Intents.Insert.JOB_TITLE, it) }
        card.phones.firstOrNull()?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
        card.emails.firstOrNull()?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
        if (rows.isNotEmpty()) putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, rows)
    }
}
