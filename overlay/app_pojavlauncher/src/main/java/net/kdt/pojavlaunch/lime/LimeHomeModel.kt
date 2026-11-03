package net.kdt.pojavlaunch.lime

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import net.kdt.pojavlaunch.PojavApplication
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.authenticator.accounts.Account
import net.kdt.pojavlaunch.authenticator.accounts.Accounts
import net.kdt.pojavlaunch.extra.ExtraConstants
import net.kdt.pojavlaunch.extra.ExtraCore
import net.kdt.pojavlaunch.instances.DisplayInstance
import net.kdt.pojavlaunch.instances.InstanceIconProvider
import net.kdt.pojavlaunch.instances.Instances

class AccountItem(val account: Account, val face: ImageBitmap?) {
    val name: String get() = account.username ?: "Player"
    val typeLabel: String
        get() = when (account.authType?.name) {
            "LOCAL" -> "Офлайн"
            "MICROSOFT" -> "Microsoft"
            "ELY_BY", "ELYBY" -> "Ely.by"
            else -> account.authType?.name ?: ""
        }
}

class InstanceItem(val instance: DisplayInstance, val icon: ImageBitmap?) {
    val name: String get() = instance.name ?: "Без названия"
    val version: String get() = instance.versionId ?: ""
}

/**
 * Данные домашнего экрана. Читает аккаунты и версии существующими классами лаунчера,
 * поэтому вся логика входа, выбора и запуска остаётся прежней.
 */
class LimeHomeModel(private val resources: Resources) {
    var accounts by mutableStateOf<List<AccountItem>>(emptyList())
        private set
    var accountIndex by mutableIntStateOf(-1)
        private set
    var instances by mutableStateOf<List<InstanceItem>>(emptyList())
        private set
    var instanceIndex by mutableIntStateOf(-1)
        private set
    var loaded by mutableStateOf(false)
        private set

    private val faceCache = HashMap<String, ImageBitmap?>()

    val currentAccount: AccountItem? get() = accounts.getOrNull(accountIndex)
    val currentInstance: InstanceItem? get() = instances.getOrNull(instanceIndex)

    private fun faceOf(account: Account): ImageBitmap? {
        val key = "${account.username}|${account.profileId}|${account.authType?.name}"
        synchronized(faceCache) {
            if (faceCache.containsKey(key)) return faceCache[key]
        }
        val face = try {
            account.skinFace?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
        synchronized(faceCache) { faceCache[key] = face }
        return face
    }

    private fun iconOf(instance: DisplayInstance): ImageBitmap? = try {
        drawableToBitmap(InstanceIconProvider.fetchIcon(resources, instance), 96).asImageBitmap()
    } catch (e: Exception) {
        null
    }

    private fun drawableToBitmap(d: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(canvas)
        return bmp
    }

    /** Полная перезагрузка аккаунтов и версий (в фоне). */
    fun reload() {
        PojavApplication.sExecutorService.execute {
            try {
                val acc = Accounts.load()
                val accItems = acc.accounts.map { AccountItem(it, faceOf(it)) }
                val inst = Instances.loadDisplay()
                val instItems = inst.list.map { InstanceItem(it, iconOf(it)) }
                val accSel = if (accItems.isEmpty()) -1 else acc.selectionIndex
                val instSel = inst.selectedIndex
                Tools.runOnUiThread {
                    accounts = accItems
                    accountIndex = accSel
                    instances = instItems
                    instanceIndex = instSel
                    loaded = true
                }
            } catch (e: Exception) {
                Log.e("LimeHome", "reload failed", e)
                Tools.runOnUiThread { loaded = true }
            }
        }
    }

    /** Лёгкое обновление только аккаунтов: вход мог завершиться в фоне. */
    fun refreshAccounts() {
        PojavApplication.sExecutorService.execute {
            try {
                val acc = Accounts.load()
                val names = acc.accounts.map { it.username }
                val oldNames = accounts.map { it.name }
                if (names == oldNames && acc.selectionIndex == accountIndex) return@execute
                val items = acc.accounts.map { AccountItem(it, faceOf(it)) }
                val sel = if (items.isEmpty()) -1 else acc.selectionIndex
                Tools.runOnUiThread {
                    accounts = items
                    accountIndex = sel
                }
            } catch (e: Exception) {
                Log.e("LimeHome", "refreshAccounts failed", e)
            }
        }
    }

    fun selectAccount(index: Int) {
        val item = accounts.getOrNull(index) ?: return
        accountIndex = index
        PojavApplication.sExecutorService.execute {
            Accounts.setCurrent(item.account)
            Tools.runOnUiThread { ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true) }
        }
    }

    fun deleteAccount(item: AccountItem) {
        PojavApplication.sExecutorService.execute {
            Accounts.delete(item.account)
            Tools.runOnUiThread { ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true) }
            reload()
        }
    }

    fun addAccount() {
        ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true)
    }

    fun selectInstance(index: Int) {
        val item = instances.getOrNull(index) ?: return
        instanceIndex = index
        PojavApplication.sExecutorService.execute {
            Instances.setSelectedInstance(item.instance)
        }
    }
}
