/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import objc.*
import kotlinx.cinterop.*

fun main(args: Array<String>) {
    memScoped {
        val argc = args.size + 1
        val argv = (arrayOf("konan") + args).toCStringArray(memScope)

        autoreleasepool {
            UIApplicationMain(argc, argv, null, NSStringFromClass(AppDelegate))
        }
    }
}

class AppDelegate : UIResponder(), UIApplicationDelegateProtocol {
    companion object : UIResponderMeta(), UIApplicationDelegateProtocolMeta {}

    override fun init() = initBy(AppDelegate())

    private var _window: UIWindow? = null
    override fun window() = _window
    override fun setWindow(window: UIWindow?) { _window = window }
}

@ExportObjCClass
class ViewController : GLKViewController {

    constructor(aDecoder: NSCoder) : super(aDecoder)
    override fun initWithCoder(aDecoder: NSCoder) = initBy(ViewController(aDecoder))

    private lateinit var context: EAGLContext

    private val statsFetcher = StatsFetcherImpl().also {
        it.asyncFetch()
    }

    private val gameState = GameState(SceneState(), statsFetcher)
    private val touchControl = TouchControl(gameState)

    private lateinit var gameRenderer: GameRenderer

    override fun viewDidLoad() {
        this.context = EAGLContext(kEAGLRenderingAPIOpenGLES3)

        val view = this.view.reinterpret<GLKView>()
        view.context = this.context
        view.drawableDepthFormat = GLKViewDrawableDepthFormat24
        view.drawableMultisample = GLKViewDrawableMultisample4X

        EAGLContext.setCurrentContext(this.context)

        gameRenderer = GameRenderer()
    }

    private var panGestureBeganDate: NSDate? = null

    @ObjCAction
    fun handlePanGesture(sender: UIPanGestureRecognizer) {
        val screen = this.view.bounds.useContents { Vector2(size.width.toFloat(), size.height.toFloat()) }
        val total = sender.translationInView(this.view).useContents {
            Vector2(
                    x.toFloat() / screen.length,
                    -y.toFloat() / screen.length // TouchControl uses the opposite `y` axis direction.
            )
        }

        when (sender.state) {
            UIGestureRecognizerStateBegan -> {
                panGestureBeganDate = NSDate.date()
                touchControl.down()
            }

            UIGestureRecognizerStateChanged -> touchControl.move(total)

            UIGestureRecognizerStateEnded -> touchControl.up(
                    total,
                    -panGestureBeganDate!!.timeIntervalSinceNow().toFloat()
            )
        }
    }

    @ObjCAction
    fun update() {
        gameState.update(this.timeSinceLastUpdate.toFloat())
    }

    override fun glkView(view: GLKView, drawInRect: CValue<CGRect>) {
        val (screenWidth, screenHeight) = this.view.bounds.useContents {
            size.width to size.height
        }

        gameRenderer.render(gameState.sceneState, screenWidth.toFloat(), screenHeight.toFloat())
    }

}