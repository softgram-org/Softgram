package desu.inugram.helpers.academics

import desu.inugram.helpers.dialogs.LocalFoldersHelper
import org.telegram.messenger.MessagesController
import org.telegram.messenger.support.LongSparseIntArray
import org.telegram.ui.ActionBar.BaseFragment

// An academic folder is mirrored as a local folder (kept on this device only), which gives it a tab on the Chats screen.
object AcademicFilters {
    private const val STUDY_ICON = "\uD83C\uDF93" // the study icon of folder tabs

    private fun filterOf(fragment: BaseFragment, folder: AcademicStore.Folder): MessagesController.DialogFilter? =
        if (folder.filterId == 0) null else fragment.messagesController.dialogFiltersById.get(folder.filterId)

    private fun inSync(filter: MessagesController.DialogFilter, folder: AcademicStore.Folder): Boolean =
        filter.name == folder.name && filter.alwaysShow.toHashSet() == folder.chats.toHashSet()

    /** creates the local folder when it is missing, otherwise makes its name and chats match the academic folder */
    fun sync(fragment: BaseFragment, account: Int, folder: AcademicStore.Folder) {
        val always = ArrayList<Long>(folder.chats)
        val existing = filterOf(fragment, folder)
        if (existing == null) {
            val filter = MessagesController.DialogFilter()
            filter.id = LocalFoldersHelper.nextLocalFilterId()
            filter.inu_emoticon = STUDY_ICON
            LocalFoldersHelper.processAddLocalFilter(
                filter, 0, folder.name, ArrayList(), false, (Math.random() * 8).toInt(),
                always, ArrayList(), LongSparseIntArray(),
                true, false, true, true, false, fragment, null,
            )
            AcademicStore.setFilterId(account, folder.id, filter.id)
        } else {
            LocalFoldersHelper.processAddLocalFilter(
                existing, existing.flags, folder.name, existing.entities, existing.title_noanimate, existing.color,
                always, existing.neverShow, existing.pinnedDialogs,
                false, false, true, true, false, fragment, null,
            )
        }
    }

    fun remove(fragment: BaseFragment, folder: AcademicStore.Folder) {
        val filter = filterOf(fragment, folder) ?: return
        fragment.messagesController.removeFilter(filter)
        fragment.messagesStorage.deleteDialogFilter(filter)
        folder.filterId = 0
    }

    /** makes sure every academic folder has its tab; only touches folders that changed or lost their tab */
    fun ensureAll(fragment: BaseFragment, account: Int) {
        // until Telegram has loaded its folders a missing tab may only be a late one; creating it now would duplicate it
        if (!fragment.messagesController.dialogFiltersLoaded) return
        for (folder in AcademicStore.folders(account).toList()) {
            val filter = filterOf(fragment, folder)
            if (filter == null || !inSync(filter, folder)) sync(fragment, account, folder)
        }
    }

    /** turning the workspace off removes the tabs; the academic folders themselves are kept */
    fun removeAll(fragment: BaseFragment, account: Int) {
        for (folder in AcademicStore.folders(account).toList()) {
            remove(fragment, folder)
            AcademicStore.setFilterId(account, folder.id, 0)
        }
    }
}
