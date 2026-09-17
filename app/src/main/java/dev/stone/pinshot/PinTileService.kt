package dev.stone.pinshot

import android.content.Intent
import android.service.quicksettings.TileService

class PinTileService : TileService() {
    override fun onClick() {
        super.onClick()
        startActivityAndCollapse(Intent(this, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
