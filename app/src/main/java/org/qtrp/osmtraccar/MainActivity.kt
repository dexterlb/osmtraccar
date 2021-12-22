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
    }

    fun initOsmAndApi(view: View) {
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
        var osmAndHelper = this.mOsmAndHelper;
        if (aidlHelper == null || osmAndHelper == null) {
            Toast.makeText(this, "foo", Toast.LENGTH_SHORT).show()
            return;
        }
        osmAndHelper.showLocation(42.6749, 23.3302)
    }

    override fun osmandMissing() {
        Toast.makeText(this, "oh no, osmand is missing", Toast.LENGTH_SHORT).show()
    }
}