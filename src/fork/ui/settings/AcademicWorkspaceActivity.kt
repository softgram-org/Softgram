package desu.inugram.ui.settings

import android.view.View
import desu.inugram.helpers.InuUtils
import desu.inugram.helpers.academics.AcademicStore
import desu.inugram.ui.showInputDialog
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.TextCheckCell
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter

// Academic Workspace: turn it on, then create academic folders and fill them with groups and channels
class AcademicWorkspaceActivity : SettingsPageActivity() {
    override fun getTitle(): CharSequence = LocaleController.getString(R.string.InuAcademicWorkspace)

    override fun onResume() {
        super.onResume()
        listView?.adapter?.update(true)
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(
            UItem.asRippleCheck(TOGGLE_ENABLED, LocaleController.getString(R.string.InuAcademicWorkspace))
                .setChecked(AcademicStore.enabled)
        )
        items.add(UItem.asShadow(LocaleController.getString(R.string.InuAcademicWorkspaceInfo)))
        if (!AcademicStore.enabled) return

        val folders = AcademicStore.folders(currentAccount)
        if (folders.isNotEmpty()) {
            items.add(UItem.asHeader(LocaleController.getString(R.string.InuAcademicFolders)))
            for (folder in folders) {
                items.add(
                    mkSubPageButton(
                        FOLDER_BASE + folder.id,
                        folder.name,
                        LocaleController.formatString(R.string.InuAcademicFolderSummary, folder.chats.size),
                    )
                )
            }
            items.add(UItem.asShadow(null))
        }
        items.add(SoftIcons.button(BUTTON_NEW, R.drawable.msg_add, LocaleController.getString(R.string.InuAcademicNewFolder)))
        items.add(UItem.asShadow(null))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        when {
            item.id == TOGGLE_ENABLED -> {
                val new = !AcademicStore.enabled
                AcademicStore.enabled = new
                (view as? TextCheckCell)?.apply {
                    setChecked(new)
                    setBackgroundColorAnimated(
                        new,
                        Theme.getColor(if (new) Theme.key_windowBackgroundChecked else Theme.key_windowBackgroundUnchecked),
                    )
                }
                listView?.adapter?.update(true)
            }

            item.id == BUTTON_NEW -> {
                showInputDialog(
                    this,
                    title = LocaleController.getString(R.string.InuAcademicNewFolder),
                    hint = LocaleController.getString(R.string.InuAcademicFolderName),
                ) { raw ->
                    val name = raw.trim()
                    if (name.isEmpty()) {
                        false
                    } else {
                        val folder = AcademicStore.create(currentAccount, name)
                        listView?.adapter?.update(true)
                        AndroidUtilities.runOnUIThread({ presentFragment(AcademicFolderActivity(folder.id)) }, 250)
                        true
                    }
                }
            }

            item.id >= FOLDER_BASE -> presentFragment(AcademicFolderActivity(item.id - FOLDER_BASE))
        }
    }

    companion object {
        private const val FOLDER_BASE = 300_000
        private val TOGGLE_ENABLED = InuUtils.generateId()
        private val BUTTON_NEW = InuUtils.generateId()
    }
}
