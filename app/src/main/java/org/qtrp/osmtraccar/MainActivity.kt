package org.qtrp.osmtraccar

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.PersistableBundle
import android.view.View
import android.widget.Toast

class MainActivity : AppCompatActivity(), OsmAndHelper.OnOsmandMissingListener {
    private var mAidlHelper: OsmAndAidlHelper? = null
    private var mOsmAndHelper: OsmAndHelper? = null

    companion object {
        const val REQUEST_OSMAND_API = 1001
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        this.mOsmAndHelper = OsmAndHelper(this, REQUEST_OSMAND_API, this)
        if (this.mOsmAndHelper == null) {
            Toast.makeText(this, "oh no, cannot create osmand helper", Toast.LENGTH_SHORT).show()
            return;
        }
        this.mAidlHelper = OsmAndAidlHelper(this.application, this)
        if (this.mAidlHelper == null) {
            Toast.makeText(this, "oh no, cannot create aidl helper", Toast.LENGTH_SHORT).show()
            return;
        }
    }

    fun doStuff(view: View) {
        var aidlHelper = this.mAidlHelper;
        if (aidlHelper == null) {
            Toast.makeText(this, "foo", Toast.LENGTH_SHORT).show()
            return;
        }
        aidlHelper.addMapMarker(42.6743, 23.3277, "fmi")
    }

    override fun osmandMissing() {
        Toast.makeText(this, "oh no, osmand is missing", Toast.LENGTH_SHORT).show()
    }
}