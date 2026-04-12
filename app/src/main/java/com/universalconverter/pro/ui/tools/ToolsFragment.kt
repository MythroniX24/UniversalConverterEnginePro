package com.universalconverter.pro.ui.tools

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import com.universalconverter.pro.databinding.FragmentToolsBinding
import com.universalconverter.pro.engine.NativeEngine

class ToolsFragment : Fragment() {
    private var _b: FragmentToolsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentToolsBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Engine info
        b.tvEngineVersion.text = "NDK Engine ${NativeEngine.getVersion()}"
        b.tvThreadCount.text   = "${NativeEngine.getThreadCount()} threads available"
        b.tvNativeLoaded.text  = if(NativeEngine.isLoaded) "✅ Native engine loaded" else "⚠️ Using Kotlin fallback"

        // Navigate to sub-tools
        b.cardDuplicateFinder.setOnClickListener {
            com.google.android.material.snackbar.Snackbar.make(b.root, "Pick images to scan for duplicates", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
        }
        b.cardSecureDelete.setOnClickListener {
            com.google.android.material.snackbar.Snackbar.make(b.root, "Select files to securely delete", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
        }
        b.cardCloudConnect.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), com.universalconverter.pro.ui.cloud.CloudActivity::class.java))
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
