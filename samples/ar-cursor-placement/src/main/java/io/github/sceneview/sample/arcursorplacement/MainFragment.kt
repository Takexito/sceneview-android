package io.github.sceneview.sample.arcursorplacement

import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.ar.core.Anchor
import com.google.ar.sceneform.math.Vector3
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.CursorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Transform
import io.github.sceneview.math.quaternion
import io.github.sceneview.model.GLBLoader
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.utils.doOnApplyWindowInsets
import kotlinx.coroutines.delay


class MainFragment : Fragment(R.layout.fragment_main) {

    lateinit var sceneView: ArSceneView
    lateinit var loadingView: View
    lateinit var anchorButton: ExtendedFloatingActionButton
    lateinit var recordButton: ExtendedFloatingActionButton

    lateinit var cursorNode: CursorNode
    var modelNode: ArModelNode? = null

    var modelInstance: ModelInstance? = null

    var isLoading = false
        set(value) {
            field = value
            loadingView.isGone = !value
            anchorButton.isGone = value
            recordButton.isGone = value
        }

    val fileName by lazy { "${requireContext().externalCacheDir?.absolutePath}/screen_record.mp4" }

    lateinit var recorder: MediaRecorder

    var isRecording = false
        set(value) {
            field = value
            recordButton.setText(if (value) R.string.stop else R.string.record)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadingView = view.findViewById(R.id.loadingView)
        anchorButton = view.findViewById<ExtendedFloatingActionButton>(R.id.anchorButton).apply {
            val bottomMargin = (layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
            doOnApplyWindowInsets { systemBarsInsets ->
                (layoutParams as ViewGroup.MarginLayoutParams).bottomMargin =
                    systemBarsInsets.bottom + bottomMargin
            }
            setOnClickListener { cursorNode.createAnchor()?.let { anchorOrMove(it) } }
        }
        recordButton = view.findViewById<ExtendedFloatingActionButton>(R.id.recordButton).apply {
            setOnClickListener {
                isRecording = if (isRecording) {
                    stopRecording()
                    false
                } else {
                    startRecording()
                    true
                }
            }
        }

        sceneView = view.findViewById<ArSceneView?>(R.id.sceneView).apply {
            planeRenderer.isVisible = false
            depthEnabled = true
            instantPlacementEnabled = true
            // Handle a fallback in case of non AR usage. The exception contains the failure reason
            // e.g. SecurityException in case of camera permission denied
            onArSessionFailed = { e: Exception ->
                // If AR is not available or the camara permission has been denied, we add the model
                // directly to the scene for a fallback 3D only usage
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            }
            onTapAr = { hitResult, _ ->
                anchorOrMove(hitResult.createAnchor())
            }
        }



        cursorNode = CursorNode(sceneView.engine).apply {
            onHitResult = { node, _ ->
                if (!isLoading) {
                    anchorButton.isGone = !node.isTracking
                }
            }
        }

//        val parentNode = CursorNode(sceneView.engine).apply {
//            position = Position(x = 0f, z = -2f, y = 0f)
//            followHitPosition = false
//            anchor()
//            rotation = Rotation(30f)
//        }

        val basePosition = Position(x = 0f, y = -1f, z = -2f)
        val stepX = 0.5f
        val stepZ = -0.5f
        val readyPositions = List(3) { row ->
            List(3) { column ->
                basePosition + Position(x = row * stepX, z = column * stepZ)
            }
        }

        val cards = List(3) { row ->
            List(3) { column ->
                CursorNode(sceneView.engine).apply {
                    onTap = { motionEvent, renderable ->
                        val cameraTransform = sceneView.cameraNode.transform
                        val cameraPosition = cameraTransform.position
                        val cameraQuaternion = cameraTransform.quaternion
                        val forwardVector = cameraQuaternion * Float3(0f, 0f, 1f)
                        val newPosition = cameraPosition + forwardVector * -0.5f

                        val newQuaternion = Quaternion.fromAxisAngle(forwardVector, 0f)
                        val newTransform = Transform(newPosition, cameraQuaternion + newQuaternion, scale)
//                        this.smooth(readyPositions[row][column] + Position(y = 0.5f))
                        this.smooth(newTransform)
                    }
                    onHitResult = { node, _ ->
                        if (!isLoading) {
                            anchorButton.isGone = !node.isTracking
                        }
                    }
                    followHitPosition = false
                    position = basePosition
                    anchor()
                }
            }
        }

        cards.forEach { rows ->
            rows.forEach {
                sceneView.addChild(it)
            }
        }

        lifecycleScope.launchWhenResumed {
            delay(3000)
            cards.forEachIndexed { row, rows ->
                rows.forEachIndexed { column, node ->
                    node.smooth(readyPositions[row][column])
                }
            }
        }

        sceneView.addChild(cursorNode)


//        lifecycleScope.launchWhenCreated {
//
//            val material = withContext(Dispatchers.Main) {
//                MaterialFactory.makeOpaqueWithColor(
//                    requireContext(),
//                    Color(android.graphics.Color.RED)
//                ).thenAccept {
//                    val sphereBuilder = Sphere.Builder()
//
//                    val entity = GeometryNode(
//                        sceneView.engine,
//                        sphereBuilder.build(),
//                        materials = listOf(it.getFilamentMaterialInstance())
//                    )
//
//                    sceneView.addChild(entity)
//                }
//            }
//
//        }

        isLoading = true
        lifecycleScope.launchWhenCreated {
            modelInstance = GLBLoader.loadModelInstance(
                context = requireContext(),
                glbFileLocation = "models/spiderbot.glb"
            )
            modelNode?.modelInstance = modelInstance
            anchorButton.text = getString(R.string.move_object)
            anchorButton.setIconResource(R.drawable.ic_target)
            isLoading = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
    }

    fun anchorOrMove(anchor: Anchor) {
        if (modelNode == null) {
            modelNode = ArModelNode(
                sceneView.engine,
                followHitPosition = false
            ).apply {
                modelInstance = modelInstance
                parent = sceneView
            }
        }
        modelNode!!.anchor = anchor
    }

    fun startRecording() {
        recorder = MediaRecorder().apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(fileName)
            setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT)
            setVideoSize(sceneView.width, sceneView.height)
            prepare()
        }
        recorder.start()
        sceneView.startMirroring(recorder.surface)
    }

    private fun stopRecording() {
        sceneView.stopMirroring(recorder.surface)
        recorder.stop()
        recorder.release()
    }
}