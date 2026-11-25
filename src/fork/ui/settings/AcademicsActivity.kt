package desu.inugram.ui.settings

import android.view.View
import desu.inugram.helpers.InuUtils
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter

// Softgram Academics: Academic Workspace, Calendar & Schedule and Study Tools (the pages come step by step)
class AcademicsActivity : SettingsPageActivity() {
    private var header: AcademicsHeaderView? = null

    override fun getTitle(): CharSequence = LocaleController.getString(R.string.InuAcademics)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        context?.let { items.add(UItem.asCustom(header ?: AcademicsHeaderView(it).also { v -> header = v })) }
        items.add(mkSubPageButton(ACADEMIC_WORKSPACE, R.drawable.softgram_academic_workspace, LocaleController.getString(R.string.InuAcademicWorkspace)))
        items.add(mkSubPageButton(ACADEMIC_CALENDAR, R.drawable.softgram_academic_calendar, LocaleController.getString(R.string.InuAcademicCalendar)))
        items.add(mkSubPageButton(ACADEMIC_STUDY, R.drawable.softgram_academic_study, LocaleController.getString(R.string.InuAcademicStudy)))
        items.add(UItem.asShadow(null))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        val ctx = context ?: return
        when (item.id) {
            ACADEMIC_WORKSPACE -> presentFragment(AcademicWorkspaceActivity())
            ACADEMIC_CALENDAR, ACADEMIC_STUDY ->
                android.widget.Toast.makeText(ctx, LocaleController.getString(R.string.InuAcademicsSoon), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private val ACADEMIC_WORKSPACE = InuUtils.generateId()
        private val ACADEMIC_CALENDAR = InuUtils.generateId()
        private val ACADEMIC_STUDY = InuUtils.generateId()
    }
}
